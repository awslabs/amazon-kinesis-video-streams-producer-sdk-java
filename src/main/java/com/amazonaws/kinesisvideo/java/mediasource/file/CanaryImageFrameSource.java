package com.amazonaws.kinesisvideo.java.mediasource.file;

import com.amazonaws.AbortedException;
import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.demoapp.auth.AuthHelper;
import com.amazonaws.kinesisvideo.internal.mediasource.OnStreamDataAvailable;

import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsync;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClientBuilder;
import com.amazonaws.services.cloudwatch.model.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.logging.log4j.Logger;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.amazonaws.kinesisvideo.producer.FrameFlags.FRAME_FLAG_KEY_FRAME;
import static com.amazonaws.kinesisvideo.producer.FrameFlags.FRAME_FLAG_NONE;
import static com.amazonaws.kinesisvideo.producer.Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;

/**
 * Frame source backed by local image files.
 */
@NotThreadSafe
public class CanaryImageFrameSource {
    public static final int METADATA_INTERVAL = 8;
    private static final long FRAME_DURATION_20_MS = 20L;
    private final ExecutorService executor = Executors.newFixedThreadPool(1);
    private final int fps;
    private final CanaryImageFileMediaSourceConfiguration configuration;

    private final int totalFiles;
    private OnStreamDataAvailable mkvDataAvailableCallback;
    private boolean isRunning = false;
    private int frameCounter;
    private final Log log = LogFactory.getLog(CanaryImageFrameSource.class);
    private final String metadataName = "ImageLoop";
    private int metadataCount = 0;
    private final Dimension dimensionPerStream;
    List<MetricDatum> datumList;

    public CanaryImageFrameSource(final CanaryImageFileMediaSourceConfiguration configuration) {
        this.configuration = configuration;
        this.totalFiles = getTotalFiles(configuration.getStartFileIndex(), configuration.getEndFileIndex());
        this.fps = configuration.getFps();
        dimensionPerStream = new Dimension()
                .withName("ProducerSDKCanaryStreamName")
                .withValue("Test123");
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

    private void startFrameGenerator() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    generateFrameAndNotifyListener();
                } catch (final KinesisVideoException e) {
                    log.error("Failed to keep generating frames with Exception", e);
                }
            }
        });
    }

    private void sendMetrics(List<MetricDatum> datumList) {
        PutMetricDataRequest request = new PutMetricDataRequest()
                .withNamespace("KVSJavaCW")
                .withMetricData(datumList);
        System.out.println("###############################");
        configuration.cloudWatchClient.putMetricDataAsync(request, new AsyncHandler<PutMetricDataRequest, PutMetricDataResult>() {
            @Override
            public void onSuccess(PutMetricDataRequest request, PutMetricDataResult result) {
                System.out.println("PUBLISHED");
                log.trace("Published metric: " + request);
            }

            @Override
            public void onError(Exception exception) {
                log.error("Could not publish metric: " + request, exception);
            }
        });
    }

    private void generateFrameAndNotifyListener() throws KinesisVideoException {

        long startTimeLatency = Duration.between(configuration.startTimestamp, Instant.now()).toMillis();
        datumList = new ArrayList<>();
        MetricDatum datum = new MetricDatum()
                .withMetricName("StartTimeLatency")
                .withUnit(StandardUnit.Milliseconds)
                .withValue((double) startTimeLatency)
                .withDimensions(dimensionPerStream);
        datumList.add(datum);
        sendMetrics(datumList);

        System.out.println("@@@@@@@@@@@@@@@@@@@@@" + startTimeLatency);

        long endTime = System.currentTimeMillis() + configuration.streamingDurationInSeconds * 1000;

        while (isRunning && System.currentTimeMillis() < endTime) {
            if (mkvDataAvailableCallback != null) {
                mkvDataAvailableCallback.onFrameDataAvailable(createKinesisVideoFrameFromImage(frameCounter));
                if (isMetadataReady()) {
                    mkvDataAvailableCallback.onFragmentMetadataAvailable(metadataName + metadataCount,
                            Integer.toString(metadataCount++), false);
                }
            }

            frameCounter++;
            try {
                Thread.sleep(Duration.ofSeconds(1L).toMillis() / fps);
            } catch (final InterruptedException e) {
                log.error("Frame interval wait interrupted by Exception ", e);
            }
        }
        stop();
    }

    private boolean isMetadataReady() {
        return frameCounter % METADATA_INTERVAL == 0;
    }

    private KinesisVideoFrame createKinesisVideoFrameFromImage(final long index) {
        final String filename = String.format(
                configuration.getFilenameFormat(),
                index % totalFiles + configuration.getStartFileIndex());
        final Path path = Paths.get(configuration.getDir() + filename);
        final long currentTimeMs = System.currentTimeMillis();

        final int flags = isKeyFrame() ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE;

        try {
            final byte[] data = Files.readAllBytes(path);
            return new KinesisVideoFrame(
                    frameCounter,
                    flags,
                    currentTimeMs * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                    currentTimeMs * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                    FRAME_DURATION_20_MS * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                    ByteBuffer.wrap(data));
        } catch (final IOException e) {
            log.error("Read file failed with Exception ", e);
        }

        return null;
    }

    private boolean isKeyFrame() {
        return frameCounter % configuration.getFps() == 0;
    }


    private void stopFrameGenerator() {
        executor.shutdown();
    }
}
