package com.amazonaws.kinesisvideo.common;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.kinesisvideo.client.KinesisVideoClient;
import com.amazonaws.kinesisvideo.client.KinesisVideoClientConfiguration;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSource;
import com.amazonaws.kinesisvideo.java.auth.JavaCredentialsFactory;
import com.amazonaws.kinesisvideo.java.client.KinesisVideoJavaClientFactory;
import com.amazonaws.kinesisvideo.java.mediasource.file.ImageFileMediaSource;
import com.amazonaws.kinesisvideo.java.mediasource.file.ImageFileMediaSourceConfiguration;
import com.amazonaws.kinesisvideo.producer.DeviceInfo;
import com.amazonaws.kinesisvideo.producer.FragmentAckType;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFragmentAck;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.StorageInfo;
import com.amazonaws.kinesisvideo.producer.Tag;
import com.amazonaws.kinesisvideo.streaming.DefaultStreamCallbacks;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClientBuilder;
import com.amazonaws.services.kinesisvideo.model.CreateStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DeleteStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamResult;
import com.amazonaws.services.kinesisvideo.model.ListTagsForStreamRequest;
import com.amazonaws.services.kinesisvideo.model.ListTagsForStreamResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNotNull;

@RunWith(Parameterized.class)
public class TagStreamIntegTest {

    private static final Logger log = LogManager.getLogger(TagStreamIntegTest.class);

    @Parameterized.Parameters(name = "tags={0}")
    public static Collection<Object[]> params() {
        return Arrays.asList(new Object[][]{
                {null},
                {Arrays.asList(new Tag("foo", "bar"))},
                {Arrays.asList(new Tag("tagname1", "tagvalue1"), new Tag("tagname2", "tagvalue2"))},
                {Arrays.asList(null, null)},
        });
    }

    private static final int FPS_25 = 25;
    private static final String H264_FILES_DIR = "src/main/resources/data/h264/";
    private static final String IMAGE_FILENAME_FORMAT = "frame-%03d.h264";
    private static final int START_FILE_INDEX = 1;
    private static final int END_FILE_INDEX = 375;

    private static final int STREAMING_DURATION_MS = 2000;
    private static final int SETUP_TEARDOWN_PADDING_MS = 10000;

    @Rule
    public Timeout globalTimeout = Timeout.millis(STREAMING_DURATION_MS + SETUP_TEARDOWN_PADDING_MS);

    @Nullable
    private final List<Tag> tags;

    private String streamName;

    public TagStreamIntegTest(@Nullable final List<Tag> tags) {
        this.tags = tags;
    }

    @Before
    public void setUp() {
        final boolean jniLoaded = ProducerTestBase.isJNILoaded();
        if (!jniLoaded) {
            fail("JNI library not found.");
        }

        assumeNotNull("Unable to find credentials!", DefaultAWSCredentialsProviderChain.getInstance().getCredentials());
        final String prefix = Optional.ofNullable(System.getenv("TEST_STREAMS_PREFIX")).orElse("");
        this.streamName = String.join("-", prefix, "TagStreamIntegTest", Long.toString(System.currentTimeMillis()), UUID.randomUUID().toString());

        createStream(this.streamName);
    }

    @After
    public void tearDown() {
        if (this.streamName != null) {
            final AmazonKinesisVideo awsSdkKinesisVideoClient = AmazonKinesisVideoClientBuilder.standard().build();
            try {
                final DescribeStreamRequest describeStreamRequest = new DescribeStreamRequest().withStreamName(this.streamName);
                final DescribeStreamResult describeStreamResult = awsSdkKinesisVideoClient.describeStream(describeStreamRequest);

                final DeleteStreamRequest deleteStreamRequest = new DeleteStreamRequest()
                        .withStreamARN(describeStreamResult.getStreamInfo().getStreamARN())
                        .withCurrentVersion(describeStreamResult.getStreamInfo().getVersion());
                awsSdkKinesisVideoClient.deleteStream(deleteStreamRequest);
            } catch (final Exception e) {
                log.error("Failed to delete the stream: {}", this.streamName, e);
                fail("Failed to delete the stream: " + this.streamName);
            }
        }
    }

    @Test
    public void when_tagStream_thenProducerContinuesNormally()
            throws KinesisVideoException, InterruptedException {
        final String testName = new Object() {
        }.getClass().getEnclosingMethod().getName();

        final KinesisVideoClientConfiguration configuration = KinesisVideoClientConfiguration.builder()
                .withCredentialsProvider(JavaCredentialsFactory
                        .createKinesisVideoCredentialsProvider(DefaultAWSCredentialsProviderChain
                                .getInstance()
                        )
                )
                .build();
        final DeviceInfo deviceInfo = createTestDeviceInfo(testName + "-device");
        final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(2);
        final KinesisVideoClient client = KinesisVideoJavaClientFactory.createKinesisVideoClient(configuration,
                deviceInfo, executorService);

        assertTrue("Client should be initialized!", client.isInitialized());

        final List<KinesisVideoFragmentAck> acksReceived = new ArrayList<>();
        final MediaSource mediaSource = new ImageFileMediaSource(this.streamName);
        mediaSource.configure(new ImageFileMediaSourceConfiguration.Builder()
                .fps(FPS_25)
                .dir(H264_FILES_DIR)
                .filenameFormat(IMAGE_FILENAME_FORMAT)
                .startFileIndex(START_FILE_INDEX)
                .endFileIndex(END_FILE_INDEX)
                .streamCallbacks(new DefaultStreamCallbacks() {
                    @Override
                    public void fragmentAckReceived(final long uploadHandle, @Nonnull final KinesisVideoFragmentAck fragmentAck) throws ProducerException, ProducerException {
                        super.fragmentAckReceived(uploadHandle, fragmentAck);
                        acksReceived.add(fragmentAck);

                        assertNotEquals("Received an unexpected ERROR ack: " + fragmentAck,
                                FragmentAckType.FRAGMENT_ACK_TYPE_ERROR, fragmentAck.getAckType().getIntType());
                    }

                    @Override
                    public void streamErrorReport(final long uploadHandle, final long frameTimecode, final long statusCode) throws ProducerException {
                        super.streamErrorReport(uploadHandle, frameTimecode, statusCode);

                        fail("Received an unexpected ERROR for the stream: 0x" + Long.toHexString(statusCode));
                    }
                })
                .tags(this.tags != null ? this.tags.toArray(new Tag[0]) : null)
                .build());

        client.registerMediaSource(mediaSource);

        mediaSource.start();

        log.info("Started media source");
        Thread.sleep(STREAMING_DURATION_MS);

        log.info("Stopping media source");
        mediaSource.stop();
        client.unregisterMediaSource(mediaSource);
        mediaSource.free();

        final long persistedAcksCount = acksReceived.stream()
                .filter(ack -> ack.getAckType().getIntType() == FragmentAckType.FRAGMENT_ACK_TYPE_PERSISTED)
                .count();

        assertTrue("Didn't receive any PERSISTED ACKs. Received: " + acksReceived, persistedAcksCount > 0);

        executorService.shutdownNow();

        verifyTags(this.streamName, this.tags);

        client.free();
    }

    private void createStream(@Nonnull final String streamName) {
        assumeNotNull(streamName, "StreamName cannot be null");

        final AmazonKinesisVideo awsSdkKinesisVideoClient = AmazonKinesisVideoClientBuilder.standard().build();
        try {
            final CreateStreamRequest createStreamRequest = new CreateStreamRequest()
                    .withStreamName(streamName)
                    .withDataRetentionInHours(2);
            awsSdkKinesisVideoClient.createStream(createStreamRequest);
        } catch (final Exception e) {
            log.error("Failed to create the stream: {}", streamName, e);
            fail("Failed to create the stream: " + streamName);
        }
    }

    private void verifyTags(@Nonnull final String streamName, @Nullable final List<Tag> tags) {
        assumeNotNull(streamName, "StreamName cannot be null");

        final List<Tag> expectedTags;
        if (tags != null) {
            expectedTags = tags.stream().filter(Objects::nonNull).collect(Collectors.toList());
        } else {
            expectedTags = new ArrayList<>();
        }

        final AmazonKinesisVideo awsSdkKinesisVideoClient = AmazonKinesisVideoClientBuilder.standard().build();
        String nextToken;
        final List<Tag> actualTags = new ArrayList<>();
        try {
            do {
                final ListTagsForStreamRequest listTagsForStreamRequest = new ListTagsForStreamRequest()
                        .withStreamName(streamName);
                final ListTagsForStreamResult listTagsForStreamResult =
                        awsSdkKinesisVideoClient.listTagsForStream(listTagsForStreamRequest);

                final Map<String, String> streamTagsFromApiCall = listTagsForStreamResult.getTags();
                for (final Map.Entry<String, String> entry : streamTagsFromApiCall.entrySet()) {
                    actualTags.add(new Tag(entry.getKey(), entry.getValue()));
                }
                nextToken = listTagsForStreamResult.getNextToken();
            } while (nextToken != null);
        } catch (final Exception e) {
            log.error("Failed to create the stream: {}", streamName, e);
            fail("Failed to create the stream: " + streamName);
            return;
        }

        final Comparator<Tag> tagComparator = (o1, o2) -> {
            if (o1 == null) {
                return (o2 == null) ? 0 : -1;
            } else if (o2 == null) {
                return +1;
            } else {
                return o1.getName().compareTo(o2.getName());
            }
        };

        expectedTags.sort(tagComparator);
        actualTags.sort(tagComparator);

        log.info("Expected tags: {}", expectedTags);
        log.info("Actual tags: {}", actualTags);
        assertEquals("The tags fetched from the service don't match the specified tags!", expectedTags.toString(), actualTags.toString());
    }

    @Nonnull
    @SuppressWarnings("ConstantConditions")
    private DeviceInfo createTestDeviceInfo(@Nonnull final String deviceName) {
        assumeNotNull("Device name cannot be null", deviceName);

        final int storageInfoVersion = 0;
        final StorageInfo.DeviceStorageType storageType = StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM;
        final long storageSizeBytes = 1024 * 1024 * 10; // 10 MB
        final int spillRatio = 90;
        final String rootDirectory = "/tmp";
        final StorageInfo storageInfo = new StorageInfo(storageInfoVersion,
                storageType,
                storageSizeBytes,
                spillRatio,
                rootDirectory);

        final int deviceInfoVersion = 0;
        final int numStreams = 1;
        final Tag[] tags = null;
        return new DeviceInfo(deviceInfoVersion,
                deviceName,
                storageInfo,
                numStreams,
                tags);
    }
}
