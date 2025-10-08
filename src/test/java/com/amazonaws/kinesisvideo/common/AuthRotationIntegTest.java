package com.amazonaws.kinesisvideo.common;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.kinesisvideo.auth.DefaultAuthCallbacks;
import com.amazonaws.kinesisvideo.client.KinesisVideoClientConfiguration;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.internal.client.NativeKinesisVideoClient;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSource;
import com.amazonaws.kinesisvideo.internal.service.DefaultServiceCallbacksImpl;
import com.amazonaws.kinesisvideo.java.auth.JavaCredentialsFactory;
import com.amazonaws.kinesisvideo.java.mediasource.file.ImageFileMediaSource;
import com.amazonaws.kinesisvideo.java.mediasource.file.ImageFileMediaSourceConfiguration;
import com.amazonaws.kinesisvideo.java.service.JavaKinesisVideoServiceClient;
import com.amazonaws.kinesisvideo.producer.ClientInfo;
import com.amazonaws.kinesisvideo.producer.DeviceInfo;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFragmentAck;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.StorageInfo;
import com.amazonaws.kinesisvideo.producer.StreamCallbacks;
import com.amazonaws.kinesisvideo.producer.Time;
import com.amazonaws.kinesisvideo.storage.DefaultStorageCallbacks;
import com.amazonaws.kinesisvideo.streaming.DefaultStreamCallbacks;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClientBuilder;
import com.amazonaws.services.kinesisvideo.model.CreateStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DeleteStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamResult;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.GetSessionTokenRequest;
import com.amazonaws.services.securitytoken.model.GetSessionTokenResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static com.amazonaws.kinesisvideo.common.ProducerTestBase.NUMBER_OF_STREAMS;
import static com.amazonaws.kinesisvideo.common.ProducerTestBase.SPILL_RATIO_PERCENT;
import static com.amazonaws.kinesisvideo.common.ProducerTestBase.STORAGE_PATH;
import static com.amazonaws.kinesisvideo.common.ProducerTestBase.STORAGE_SIZE_MEGS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/**
 * Integration test for auth refresh timeout scenarios using MediaSource interface.
 * Verifies that when the client auth rotation occurs, PutMedia is rotated seamlessly.
 */
public class AuthRotationIntegTest {
    private static final Logger log = LogManager.getLogger(AuthRotationIntegTest.class);

    private static final String IMAGE_DIR = "src/main/resources/data/h264/";
    private static final String IMAGE_FILENAME_FORMAT = "frame-%03d.h264";
    private static final int START_FILE_INDEX = 1;
    private static final int END_FILE_INDEX = 375;

    private final List<String> createdStreams = new ArrayList<>();
    private NativeKinesisVideoClient kinesisVideoClient;
    private ScheduledExecutorService executor;

    private final long timeoutMillis = 2000;
    private final long timeoutNanos = this.timeoutMillis * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
    private final Duration credentialRefreshInterval = Duration.ofSeconds(45);
    private final Duration gracePeriod = Duration.ofSeconds(8);
    private final AtomicInteger refreshCalled = new AtomicInteger(0);

    @Before
    public void setUp() throws KinesisVideoException {
        this.executor = Executors.newScheduledThreadPool(2);

        final KinesisVideoClientConfiguration config = KinesisVideoClientConfiguration.builder()
                .withCredentialsProvider(JavaCredentialsFactory.createKinesisVideoCredentialsProvider(new TemporaryCredentialsProvider(refreshCalled), credentialRefreshInterval))
                .build();

        final DefaultAuthCallbacks authCallbacks = new DefaultAuthCallbacks(
                config.getCredentialsProvider(), this.executor, log);

        authCallbacks.setCredentialsUpdateTimeoutMillis(this.timeoutMillis);

        final DefaultStorageCallbacks storageCallbacks = new DefaultStorageCallbacks();
        final DefaultServiceCallbacksImpl serviceCallbacks = new DefaultServiceCallbacksImpl(
                log, this.executor, config, new JavaKinesisVideoServiceClient());
        final StreamCallbacks streamCallbacks = new DefaultStreamCallbacks();

        this.kinesisVideoClient = new NativeKinesisVideoClient(log, authCallbacks, storageCallbacks,
                serviceCallbacks, streamCallbacks);

        this.kinesisVideoClient.initialize(new DeviceInfo(1,
                "java-test-application", new StorageInfo(0,
                StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM, STORAGE_SIZE_MEGS,
                SPILL_RATIO_PERCENT, STORAGE_PATH), NUMBER_OF_STREAMS, null, "java-client", new ClientInfo(
                this.timeoutNanos, this.timeoutNanos, this.timeoutNanos, this.timeoutNanos, 0, false, this.timeoutNanos, this.timeoutNanos
        )));
    }

    @After
    public void tearDown() {
        if (this.kinesisVideoClient != null) {
            try {
                this.kinesisVideoClient.stopAllMediaSources();
                this.kinesisVideoClient.free();
            } catch (final Exception e) {
                log.warn("Error freeing client", e);
            }
        }

        if (this.executor != null) {
            this.executor.shutdownNow();
        }

        final AmazonKinesisVideo awsSdkClient = AmazonKinesisVideoClientBuilder.standard().build();

        for (final String streamName : this.createdStreams) {
            try {
                final DescribeStreamRequest describeRequest = new DescribeStreamRequest().withStreamName(streamName);
                final DescribeStreamResult describeResult = awsSdkClient.describeStream(describeRequest);

                final DeleteStreamRequest deleteRequest = new DeleteStreamRequest()
                        .withStreamARN(describeResult.getStreamInfo().getStreamARN())
                        .withCurrentVersion(describeResult.getStreamInfo().getVersion());
                awsSdkClient.deleteStream(deleteRequest);
            } catch (final Exception e) {
                log.warn("Failed to delete stream: {}", streamName, e);
            }
        }
    }

    /**
     * <ol>
     *     <li>Start 5 streams</li>
     *     <li>When the credentials are about to expire, register 15 more streams spaced out</li>
     *     <li>All the streams should continue normally</li>
     * </ol>
     */
    @Test
    public void testWhenClientAuthRotates_thenStreamContinuesNormally() throws InterruptedException{
        final String testName = "AuthRotationIntegTest-workingFine";
        final List<StreamContext> streamContexts = new ArrayList<>();

        // Should go through fine
        for (int i = 0; i < 5; i++) {
            final StreamContext streamContext = initializeMediaSourceAndContext(testName, i);
            final MediaSource mediaSource = streamContext.mediaSource;
            try {
                this.kinesisVideoClient.registerMediaSource(mediaSource);
                log.info("Registered mediasource: {}", i);
                mediaSource.start();
            } catch (final KinesisVideoException e) {
                fail("Unexpected exception: " + e);
            }
            streamContexts.add(streamContext);
        }

        Thread.sleep(credentialRefreshInterval.toMillis() - 5000);

        // Should be working fine
        for (int i = 5; i < 20; i++) {
            final StreamContext streamContext = initializeMediaSourceAndContext(testName, i);
            final MediaSource mediaSource = streamContext.mediaSource;
            try {
                log.info("Registering mediasource: {}", i);
                this.kinesisVideoClient.registerMediaSource(mediaSource);
                mediaSource.start();
            } catch (final KinesisVideoException e) {
                fail("Unexpected exception: " + e);
            }

            streamContexts.add(streamContext);

            // Space them out
            try {
                Thread.sleep(500);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Validate results
        for (int i = 0; i < 5; i++) {
            System.out.println(i + " ----------------");
            final StreamContext streamContext = streamContexts.get(i);

            assertTrue("Stream " + i + " did not receive any acks!", !streamContext.acksReceived.isEmpty());

            // Search for the refresh -- the ack timestamp should have been around 45000 then down to around 0
            // Note since acks are asynchronous, it might be out of order, so we use a threshold instead

            boolean rotated = false;
            KinesisVideoFragmentAck prev = null;
            for (final KinesisVideoFragmentAck ack : streamContext.acksReceived) {
                if (prev != null) {
                    long timestampGap = prev.getTimestamp() - ack.getTimestamp();

                    if (timestampGap >= credentialRefreshInterval.toMillis() - gracePeriod.toMillis()) {
                        log.debug("Detected the refresh after {}ms", timestampGap);
                        rotated = true;
                        break;
                    }
                }
                prev = ack;
            }

            assertTrue("Stream " + i + " was not rotated!", rotated);
        }

        System.out.println("Refresh was called: " + refreshCalled.get() + " times!");
    }


    @SuppressWarnings("ConstantConditions")
    private StreamContext initializeMediaSourceAndContext(@Nonnull final String testName, final int index) {
        assumeTrue(testName != null);
        assumeTrue(index >= 0);
        final String streamName = testName + "_" + index + "_" + System.currentTimeMillis();

        // Create stream first
        final AmazonKinesisVideo awsSdkClient = AmazonKinesisVideoClientBuilder.standard().build();
        final String prefix = Optional.ofNullable(System.getenv("TEST_STREAMS_PREFIX")).orElse("");
        final String finalStreamName = prefix + streamName;

        awsSdkClient.createStream(new CreateStreamRequest()
                .withStreamName(finalStreamName)
                .withDataRetentionInHours(2));
        this.createdStreams.add(finalStreamName);

        final StreamContext streamContext = new StreamContext();

        // Create minimal media source
        final MediaSource mediaSource = new ImageFileMediaSource(finalStreamName);
        final ImageFileMediaSourceConfiguration config = new ImageFileMediaSourceConfiguration.Builder()
                .fps(25)
                .dir(IMAGE_DIR)
                .filenameFormat(IMAGE_FILENAME_FORMAT)
                .startFileIndex(START_FILE_INDEX)
                .endFileIndex(END_FILE_INDEX)
                .allowStreamCreation(false)
                .frameGeneratorThreadName(streamName + "-frame-generator")
                .streamCallbacks(new DefaultStreamCallbacks() {
                    @Override
                    public void streamErrorReport(long uploadHandle, long frameTimecode, long statusCode) {
                        streamContext.errorsReceived.add(uploadHandle);
                    }

                    @Override
                    public void fragmentAckReceived(long uploadHandle, @Nonnull KinesisVideoFragmentAck fragmentAck) throws ProducerException {
                        streamContext.acksReceived.add(fragmentAck);
                    }
                })
                .build();
        mediaSource.configure(config);

        streamContext.mediaSource = mediaSource;

        return streamContext;
    }

    private class StreamContext {
        public MediaSource mediaSource;
        public final List<KinesisVideoFragmentAck> acksReceived = new ArrayList<>();
        public final List<Long> errorsReceived = new ArrayList<>();
    }

    /**
     * Credentials provider that always returns temporary credentials
     */
    private static class TemporaryCredentialsProvider implements AWSCredentialsProvider {

        private final AWSCredentials credentials;
        private final AtomicInteger refreshCount;

        public TemporaryCredentialsProvider(AtomicInteger refreshCount) {
            AWSCredentials creds = DefaultAWSCredentialsProviderChain.getInstance().getCredentials();
            assumeTrue(creds != null);

            if (!(creds instanceof AWSSessionCredentials)) {
                final AWSSecurityTokenService sts = AWSSecurityTokenServiceClientBuilder.standard().build();
                final GetSessionTokenResult result = sts.getSessionToken(new GetSessionTokenRequest().withDurationSeconds(900)); // minimum: 15 mins
                creds = new BasicSessionCredentials(result.getCredentials().getAccessKeyId(), result.getCredentials().getSecretAccessKey(), result.getCredentials().getSessionToken());
            }

            this.credentials = creds;
            this.refreshCount = refreshCount;
        }

        @Override
        public AWSCredentials getCredentials() {
            return this.credentials;
        }

        @Override
        public void refresh() {
            log.info("Refresh called");
            refreshCount.getAndIncrement();
        }
    }
}
