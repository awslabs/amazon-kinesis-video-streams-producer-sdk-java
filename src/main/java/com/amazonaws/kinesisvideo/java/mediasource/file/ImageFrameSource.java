package com.amazonaws.kinesisvideo.java.mediasource.file;

import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.internal.mediasource.OnStreamDataAvailable;

import com.amazonaws.kinesisvideo.java.inferencemodel.InferenceModel;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.bytedeco.javacv.FFmpegFrameGrabber;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import java.util.HashMap;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.amazonaws.kinesisvideo.producer.FrameFlags.FRAME_FLAG_KEY_FRAME;
import static com.amazonaws.kinesisvideo.producer.FrameFlags.FRAME_FLAG_NONE;
import static com.amazonaws.kinesisvideo.producer.Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;

/**
 * Frame source backed by local image files.
 */
@NotThreadSafe
public class ImageFrameSource {
    public static final int METADATA_INTERVAL = 8;
    private static final long FRAME_DURATION_20_MS = 20L;
    private final ExecutorService executor = Executors.newFixedThreadPool(1);
    private final int fps;
    private final ImageFileMediaSourceConfiguration configuration;

    private final int totalFiles;
    private OnStreamDataAvailable mkvDataAvailableCallback;
    private boolean isRunning = false;
    private int frameCounter;
    private final Log log = LogFactory.getLog(ImageFrameSource.class);
    private final String metadataName = "ImageLoop";
    private int metadataCount = 0;
    private String tableName = "MetadataPoC";
    private final InferenceModel inferenceModel;

    public ImageFrameSource(final ImageFileMediaSourceConfiguration configuration) {
        this.configuration = configuration;
        this.totalFiles = getTotalFiles(configuration.getStartFileIndex(), configuration.getEndFileIndex());
        this.fps = configuration.getFps();
        this.inferenceModel = new InferenceModel();
    }

    private int getTotalFiles(final int startIndex, final int endIndex) {
        Preconditions.checkState(endIndex >= startIndex);
        return endIndex - startIndex + 1;
    }

    public void start() {
        if (isRunning) {
            throw new IllegalStateException("Frame source is already running");
        }

        isRunning = true;
        startFrameGenerator();
    }

    public void stop() {
        isRunning = false;
        stopFrameGenerator();
    }

    public void onStreamDataAvailable(final OnStreamDataAvailable onMkvDataAvailable) {
        this.mkvDataAvailableCallback = onMkvDataAvailable;
    }

    public void putItemInTable(String streamName, final long producerTs, String jsonMetadata) {
        Region region = Region.US_WEST_2;

        DynamoDbClient ddb = DynamoDbClient.builder()
                .region(region)
                .credentialsProvider(()
                        -> AwsBasicCredentials.create("", ""))
                .build();

        HashMap<String, AttributeValue> itemValues = new HashMap<>();
        itemValues.put("StreamName", AttributeValue.builder().s(streamName).build());
        itemValues.put("StartTimeStamp", AttributeValue.builder().s(String.valueOf(producerTs)).build());
        itemValues.put("ProducerTs", AttributeValue.builder().s(String.valueOf(producerTs)).build());
        itemValues.put("TagsMetadata", AttributeValue.builder().s(jsonMetadata).build());

        PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(itemValues)
                .build();

        try {
            PutItemResponse response = ddb.putItem(request);
            System.out.println(tableName + " was successfully updated. The request id is " + response.responseMetadata().requestId());

        } catch (ResourceNotFoundException e) {
            System.err.format("Error: The Amazon DynamoDB table \"%s\" can't be found.\n", tableName);
            System.err.println("Be sure that it exists and that you've typed its name correctly!");
            System.exit(1);
        } catch (DynamoDbException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    private void startFrameGenerator() {
        executor.execute(() -> {
            try {
                generateFrameAndNotifyListener();
            } catch (final KinesisVideoException e) {
                log.error("Failed to keep generating frames with Exception", e);
            } catch (FFmpegFrameGrabber.Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void generateFrameAndNotifyListener() throws KinesisVideoException, FFmpegFrameGrabber.Exception {
        while (isRunning) {
            if (mkvDataAvailableCallback != null) {
                final long currentTimeMs = System.currentTimeMillis() * HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
                if (isKeyFrame()) {
                    HashMap<String, Double> labels = inferenceModel.performObjectDetection(getFileName(frameCounter));
                    if (labels.size() > 0) {
                        JSONObject json = new JSONObject(labels);
                        System.out.println("Detected objects are: " + String.valueOf(json));
                        putItemInTable("my-new-stream", currentTimeMs, String.valueOf(json));
                        for (String label : labels.keySet()) {
                            mkvDataAvailableCallback.onFragmentMetadataAvailable(label,
                                    String.valueOf(labels.get(label)), true); // putKinesisVideoFragmentMetadata
                        }
                    }
                }
                mkvDataAvailableCallback.onFrameDataAvailable(createKinesisVideoFrameFromImage(frameCounter, currentTimeMs)); // putFrame
            }

            frameCounter++;
            try {
                Thread.sleep(Duration.ofSeconds(1L).toMillis() / fps);
            } catch (final InterruptedException e) {
                log.error("Frame interval wait interrupted by Exception ", e);
            }
        }
    }

    private boolean isMetadataReady() {
        return frameCounter % METADATA_INTERVAL == 0;
    }

    private String getFileName(final long index) {
        return configuration.getDir() + String.format(
                configuration.getFilenameFormat(),
                index % totalFiles + configuration.getStartFileIndex());
    }
    private ByteBuffer getFrameData(final long index) {
        byte [] data;
        final Path path = Paths.get(getFileName(index));
        try {
            data = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return ByteBuffer.wrap(data);
    }

    private KinesisVideoFrame createKinesisVideoFrameFromImage(final long index, final long currentTimeMs) {

        final int flags = isKeyFrame() ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE;
        return new KinesisVideoFrame(
                frameCounter,
                flags,
                currentTimeMs,
                currentTimeMs,
                FRAME_DURATION_20_MS * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                getFrameData(index));
    }

    private boolean isKeyFrame() {
        return frameCounter % configuration.getFps() == 0;
    }


    private void stopFrameGenerator() {
        executor.shutdown();
    }
}
