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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.amazonaws.kinesisvideo.common.ProducerTestBase.NUMBER_OF_STREAMS;
import static com.amazonaws.kinesisvideo.common.ProducerTestBase.SPILL_RATIO_PERCENT;
import static com.amazonaws.kinesisvideo.common.ProducerTestBase.STORAGE_PATH;
import static com.amazonaws.kinesisvideo.common.ProducerTestBase.STORAGE_SIZE_MEGS;
import static com.amazonaws.kinesisvideo.producer.ProducerException.STATUS_AUTH_CALL_FAILED;
import static com.amazonaws.kinesisvideo.producer.ProducerException.STATUS_INVALID_STREAM_STATE;
import static com.amazonaws.kinesisvideo.producer.ProducerException.STATUS_OPERATION_TIMED_OUT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/**
 * Integration test for auth refresh timeout scenarios using MediaSource interface.
 * Tests behavior when credential refresh is slow and causes registerMediaSource timeouts.
 * The SDK should emit the errors to the application.
 */
public class AuthRefreshTimeoutIntegTest {
    private static final Logger log = LogManager.getLogger(AuthRefreshTimeoutIntegTest.class);

    private static final String IMAGE_DIR = "src/main/resources/data/h264/";
    private static final String IMAGE_FILENAME_FORMAT = "frame-%03d.h264";
    private static final int START_FILE_INDEX = 1;
    private static final int END_FILE_INDEX = 375;

    private final List<String> createdStreams = new ArrayList<>();
    private NativeKinesisVideoClient kinesisVideoClient;
    private ScheduledExecutorService executor;
    private SlowCredentialsProvider slowCredentialsProvider;

    private final long timeoutMillis = 2000;
    private final long timeoutNanos = this.timeoutMillis * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
    private final Duration credentialRefreshInterval = Duration.ofSeconds(45);

    @Before
    public void setUp() throws KinesisVideoException {
        this.executor = Executors.newScheduledThreadPool(2);

        // Create slow credentials provider
        this.slowCredentialsProvider = new SlowCredentialsProvider(100 * this.timeoutMillis);

        final KinesisVideoClientConfiguration config = KinesisVideoClientConfiguration.builder()
                .withCredentialsProvider(JavaCredentialsFactory.createKinesisVideoCredentialsProvider(this.slowCredentialsProvider, this.credentialRefreshInterval))
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
        this.slowCredentialsProvider.unthrottle();

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

    @Test
    public void test_When_AuthRefreshIsSlow_Then_RegisterMediaSourceTimesOut() {
        final String testName = "AuthRefreshTimeoutIntegTest";

        this.slowCredentialsProvider.throttle();

        for (int i = 0; i < 5; i++) {
            final MediaSource mediaSource = createMediaSource(testName, i);
            try {
                this.kinesisVideoClient.registerMediaSource(mediaSource);
                fail("Expected timeout but registerMediaSource succeeded");
            } catch (final KinesisVideoException e) {
                assertTrue("Expected to receive ProducerException but received: " + e, e instanceof ProducerException);
                assertEquals("Expected timed out", STATUS_OPERATION_TIMED_OUT, ((ProducerException) e).getStatusCode());
            }
        }
    }

    @Test
    public void testWhenGetTokenIsSlow_Then_RegisterMediaSourceTimesOut() {
        final String testName = "AuthRefreshTimeoutIntegTest-registerMediaSourceTimesOut";

        this.slowCredentialsProvider.throttle();

        for (int i = 0; i < 10; i++) {
            final MediaSource mediaSource = createMediaSource(testName, i);
            try {
                this.kinesisVideoClient.registerMediaSource(mediaSource);
                fail("Expected timeout but registerMediaSource succeeded");
            } catch (final KinesisVideoException e) {
                assertTrue("Expected to receive ProducerException but received: " + e, e instanceof ProducerException);
                assertEquals("Expected timed out", STATUS_OPERATION_TIMED_OUT, ((ProducerException) e).getStatusCode());
            }
        }
    }


    @Test
    public void testWhenClientAuthRefreshIsSlow_Then_EverythingTimesOut() throws InterruptedException{
        final String testName = "AuthRefreshTimeoutIntegTest-everythingTimesOut";

        // Should go through fine
        for (int i = 0; i < 5; i++) {
            final MediaSource mediaSource = createMediaSource(testName, i);
            try {
                this.kinesisVideoClient.registerMediaSource(mediaSource);
                log.info("Registered mediasource: {}", i);
                mediaSource.start();
            } catch (final KinesisVideoException e) {
                fail("Unexpected exception: " + e);
            }
        }

        this.slowCredentialsProvider.throttle();

        Thread.sleep(credentialRefreshInterval.toMillis() - 5000);

        // The first ones should time out since the retries haven't been exhausted yet
        // The next few should fail with the auth failed after the retries are exhausted
        // and the rest should fail with the invalid state since the client is no longer
        // ready (since it's currently in the auth state)
        final Set<Integer> expectedErrorCodes = new HashSet<>();
        expectedErrorCodes.add(STATUS_OPERATION_TIMED_OUT);
        expectedErrorCodes.add(STATUS_AUTH_CALL_FAILED);
        expectedErrorCodes.add(STATUS_INVALID_STREAM_STATE);

        // Should all error out
        for (int i = 5; i < 15; i++) {
            final MediaSource mediaSource = createMediaSource(testName, i);
            try {
                log.info("Registering mediasource: {}", i);
                this.kinesisVideoClient.registerMediaSource(mediaSource);
                fail("Expected timeout but registerMediaSource succeeded");
            } catch (final KinesisVideoException e) {
                assertTrue("Expected to receive ProducerException but received: " + e, e instanceof ProducerException);

                final int statusCode = ((ProducerException) e).getStatusCode();
                assertTrue("Stream " + i + " - expected one of the error codes: " + expectedErrorCodes + ", but received: " + statusCode,
                        expectedErrorCodes.contains(statusCode));
            }

            // Space them out
            try {
                Thread.sleep(500);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }


    @SuppressWarnings("ConstantConditions")
    private MediaSource createMediaSource(@Nonnull final String testName, final int index) {
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
                .build();
        mediaSource.configure(config);

        return mediaSource;
    }

    /**
     * Credentials provider that simulates slow refresh by adding delay
     */
    private static class SlowCredentialsProvider implements AWSCredentialsProvider {
        private final long delayMs;
        private final AWSCredentials credentials;

        private boolean nextCallSlow;

        public SlowCredentialsProvider(final long delayMs) {
            assumeTrue("delayMs must be positive!", delayMs > 0);
            this.delayMs = delayMs;
            this.nextCallSlow = false;

            AWSCredentials creds = DefaultAWSCredentialsProviderChain.getInstance().getCredentials();
            if (!(creds instanceof AWSSessionCredentials)) {
                final AWSSecurityTokenService sts = AWSSecurityTokenServiceClientBuilder.standard().build();
                final GetSessionTokenResult result = sts.getSessionToken(new GetSessionTokenRequest().withDurationSeconds(900)); // minimum: 15 mins
                creds = new BasicSessionCredentials(result.getCredentials().getAccessKeyId(), result.getCredentials().getSecretAccessKey(), result.getCredentials().getSessionToken());
            }

            this.credentials = creds;
        }

        public void throttle() {
            this.nextCallSlow = true;
        }

        public void unthrottle() {
            this.nextCallSlow = false;
        }

        @Override
        public AWSCredentials getCredentials() {
            return this.credentials;
        }

        @Override
        public void refresh() {
            if (!this.nextCallSlow) {
                log.info("Fast refresh with no delay");
                return;
            }

            try {
                log.info("Simulating slow credential refresh with {}ms delay", this.delayMs);
                Thread.sleep(this.delayMs);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
