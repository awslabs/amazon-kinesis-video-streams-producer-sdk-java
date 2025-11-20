package com.amazonaws.kinesisvideo.common;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.amazonaws.kinesisvideo.internal.producer.jni.NativeKinesisVideoProducerJni.PRODUCER_NATIVE_LIBRARY_NAME;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.kinesisvideo.auth.DefaultAuthCallbacks;
import com.amazonaws.kinesisvideo.client.KinesisVideoClientConfiguration;
import com.amazonaws.kinesisvideo.internal.producer.ServiceCallbacks;
import com.amazonaws.kinesisvideo.internal.producer.client.KinesisVideoServiceClient;
import com.amazonaws.kinesisvideo.internal.producer.jni.NativeKinesisVideoProducerJni;
import com.amazonaws.kinesisvideo.java.auth.JavaCredentialsFactory;
import com.amazonaws.kinesisvideo.producer.Tag;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.amazonaws.kinesisvideo.internal.client.NativeKinesisVideoClient;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducer;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.internal.service.DefaultServiceCallbacksImpl;
import com.amazonaws.kinesisvideo.java.service.CachedInfoMultiAuthServiceCallbacksImpl;
import com.amazonaws.kinesisvideo.java.service.JavaKinesisVideoServiceClient;
import com.amazonaws.kinesisvideo.producer.*;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClientBuilder;
import com.amazonaws.services.kinesisvideo.model.*;
import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.AWSKMSClientBuilder;
import com.amazonaws.services.kms.model.*;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import static com.amazonaws.kinesisvideo.producer.StreamInfo.NalAdaptationFlags.NAL_ADAPTATION_FLAG_NONE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.*;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.RECALCULATE_METRICS;

public class ProducerTestBase {
    protected static final long TEST_BUFFER_DURATION = 12000L * Time.HUNDREDS_OF_NANOS_IN_A_SECOND; // 120 seconds
    protected static final long TEST_LATENCY = 6000L * Time.HUNDREDS_OF_NANOS_IN_A_SECOND; // 60 seconds
    protected static final int FRAME_FLAG_KEY_FRAME = 1;
    protected static final int FRAME_FLAG_NONE = 0;
    protected static final long STATUS_KMS_KEY_INVALID_STATE = 0x5200006AL;
    protected static final long RESULT_KMS_KEY_INVALID_STATE = 4505;
    protected static final int TEST_FRAME_SIZE_BYTES = 1000;
    protected static final int TEST_FPS = 20;
    protected static final int TEST_KEY_FRAME_INTERVAL = 20;
    protected static final int TEST_MEDIA_DURATION_SECONDS = 60;
    protected static final int WAIT_5_SECONDS_FOR_ACKS = 5000;
    protected static final int TEST_TOTAL_FRAME_COUNT = TEST_FPS * TEST_MEDIA_DURATION_SECONDS;
    protected static final long TEST_FRAME_DURATION = 1000 * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND / TEST_FPS;

    protected static final int DEVICE_VERSION = 0;
    protected static final String DEVICE_NAME = "java-test-application";

    protected static final int STORAGE_SIZE_MEGS = 64 * 1024 * 1024;
    protected static final int SPILL_RATIO_PERCENT = 90;
    protected static final String STORAGE_PATH = "/tmp";

    protected static final int NUMBER_OF_THREADS_IN_POOL = 2;
    protected static final int NUMBER_OF_STREAMS = 500;

    protected int fps_ = TEST_FPS;
    protected int keyFrameInterval_ = TEST_KEY_FRAME_INTERVAL;
    protected long frameDuration_ = TEST_FRAME_DURATION;
    protected StorageInfo storageInfo_ = new StorageInfo(0,
            StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM, STORAGE_SIZE_MEGS,
            SPILL_RATIO_PERCENT, STORAGE_PATH);
    protected DeviceInfo deviceInfo_;
    private final Logger log = LogManager.getLogger(ProducerTestBase.class);

    // flags that are updated in case of various events like overflow, error, pressure, etc.
    protected boolean stopCalled_;
    protected boolean frameDropped_;
    protected boolean bufferDurationPressure_;
    protected boolean storageOverflow_;
    protected boolean bufferingAckInSequence_;
    protected boolean allowStreamCreation;

    protected long errorStatus_;
    protected int latencyPressureCount_;
    protected HashMap<Long, Long> previousBufferingAckTimestamp_ = new HashMap<>();
    protected List<KinesisVideoFragmentAck> receivedFragmentAcks_ = new ArrayList<>();

    // set by the createProducer method to be used throughout
    private StreamCallbacks streamCallbacks;
    private KinesisVideoClientConfiguration configuration;
    private AWSCredentialsProvider awsCredentialsProvider;
    private JavaKinesisVideoServiceClient serviceClient;
    private ScheduledExecutorService executor;
    private NativeKinesisVideoClient kinesisVideoClient;
    private AuthCallbacks authCallbacks;
    private StorageCallbacks storageCallbacks;
    private KinesisVideoProducer kinesisVideoProducer;

    protected void reset() {
        stopCalled_ = false;
        frameDropped_ = false;
        bufferDurationPressure_ = false;
        storageOverflow_ = false;
        bufferingAckInSequence_ = true;
        errorStatus_ = 0x00000000;
        latencyPressureCount_ = 0;
        previousBufferingAckTimestamp_.clear();
        receivedFragmentAcks_.clear();

        fps_ = 20;
        keyFrameInterval_ = 20;
        frameDuration_ = 1000 * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND / fps_;
    }

    protected static boolean isJNILoaded() {
        try {
            System.loadLibrary(PRODUCER_NATIVE_LIBRARY_NAME);
            return true;
        } catch (final UnsatisfiedLinkError e) {
            return false;
        }
    }

    protected long getFragmentDurationMs() {
        return keyFrameInterval_ * frameDuration_ / Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
    }

    @FunctionalInterface
    protected interface ServiceCallbacksConstructor {
        DefaultServiceCallbacksImpl apply(
                Logger log,
                ScheduledExecutorService executor,
                KinesisVideoClientConfiguration configuration,
                KinesisVideoServiceClient kinesisVideoServiceClient
        );
    }

    /**
     * This method is used to create a KinesisVideoProducer which is used by the later methods
     */
    protected void createProducer() {
        deviceInfo_ = new DeviceInfo(DEVICE_VERSION,
                DEVICE_NAME, storageInfo_, NUMBER_OF_STREAMS, null,
                "JNI " + NativeKinesisVideoProducerJni.EXPECTED_LIBRARY_VERSION,
                new ClientInfo());

        try {
            createProducer(deviceInfo_);
        } catch (Exception e) {
            log.error("Unable to create Kinesis Video Producer.", e);
            fail("Unable to create Kinesis Video Producer.");
        }
    }

    protected void createProducer(DeviceInfo deviceInfo) {
        try {
            createProducer(deviceInfo, DefaultServiceCallbacksImpl::new);
        } catch (Exception e) {
            log.error("Unable to create Kinesis Video Producer.", e);
            fail("Unable to create Kinesis Video Producer.");
        }
    }

    /**
     * This method is used to create a KinesisVideoProducer which is used by the later methods
     */
    protected void createProducer(DeviceInfo deviceInfo, ServiceCallbacksConstructor serviceCallbacksConstructor) throws Exception {

        reset(); // reset all flags to initial values so that they can be modified by the stream and storage callbacks

        executor = Executors.newScheduledThreadPool(NUMBER_OF_THREADS_IN_POOL,
                new ThreadFactoryBuilder().setNameFormat("KVS-JavaClientExecutor-%d").build());

        awsCredentialsProvider = DefaultAWSCredentialsProviderChain.getInstance();
        configuration = KinesisVideoClientConfiguration.builder()
                .withRegion(Regions.US_WEST_2.getName())
                .withCredentialsProvider(JavaCredentialsFactory.createKinesisVideoCredentialsProvider(awsCredentialsProvider))
                .build();

        serviceClient = new JavaKinesisVideoServiceClient(log);
        authCallbacks = new DefaultAuthCallbacks(configuration.getCredentialsProvider(),
                executor,
                log);
        // use TestStorageCallbacks and TestStreamCallbacks to override the callbacks to update the flags in case of
        // overflow, errors and other events. The current ProducerTestBase object is passed to their constructors so
        // that they can access the flags to be updated
        storageCallbacks = new TestStorageCallbacks(this);
        streamCallbacks = new TestStreamCallBacks(this);

        // Use the custom service callbacks (used to inject return values for API calls)
        ServiceCallbacks defaultServiceCallbacks = serviceCallbacksConstructor.apply(log, executor,
                configuration, serviceClient);
        kinesisVideoClient = new NativeKinesisVideoClient(log,
                authCallbacks,
                storageCallbacks,
                defaultServiceCallbacks,
                streamCallbacks);
        try {
            kinesisVideoProducer = kinesisVideoClient.initializeNewKinesisVideoProducer(deviceInfo);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    protected void free() {
        try {
            this.kinesisVideoProducer.free();
        } catch (ProducerException e) {
            log.error("Failed to free the producer", e);
            fail(e.getMessage());
        }

        this.executor.shutdownNow();
        try {
            assertTrue("Didn't shutdown the executor service in time!",
                    this.executor.awaitTermination(5, TimeUnit.SECONDS));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for executor to terminate", e);
        }
    }

    /**
     * This method is used to create a stream with the specified information using the producer created as a part of
     * the createProducer method
     *
     * @param streamName     the name of the stream to be created
     * @param streamingType  the type of the stream - realtime, offline
     * @param maxLatency     the maxLatency for the streamInfo
     * @param bufferDuration the bufferDuration for the streamInfo
     * @return KinesisVideoProducerStream the created stream
     */
    protected KinesisVideoProducerStream createTestStream(String streamName, StreamInfo.StreamingType streamingType,
                                                          long maxLatency, long bufferDuration) throws ProducerException {
        return createTestStream(streamName, streamingType, maxLatency, bufferDuration, NAL_ADAPTATION_FLAG_NONE, false);
    }

    protected KinesisVideoProducerStream createTestStream(String streamName, StreamInfo.StreamingType streamingType,
                                                          long maxLatency, long bufferDuration, StreamInfo.NalAdaptationFlags nalAdaptationFlags, boolean skipPreparation) throws ProducerException {
        KinesisVideoProducerStream kinesisVideoProducerStream = null;
        
        final byte[] codecPrivateData = ProducerTestCPDs.getTestCPD(nalAdaptationFlags);

        final String prefix = Optional.ofNullable(System.getenv("TEST_STREAMS_PREFIX")).orElse("");
        final String finalStreamName = prefix + streamName;

        if (!skipPreparation) {
            prepareStream(finalStreamName);
        }

        final StreamInfo streamInfo = new StreamInfo(
                StreamInfo.STREAM_INFO_CURRENT_VERSION,
                finalStreamName,
                streamingType,
                "video/h264",
                NO_KMS_KEY_ID,
                RETENTION_ONE_HOUR,
                NOT_ADAPTIVE,
                maxLatency,
                DEFAULT_GOP_DURATION,
                KEYFRAME_FRAGMENTATION,
                USE_FRAME_TIMECODES,
                RELATIVE_TIMECODES,
                REQUEST_FRAGMENT_ACKS,
                RECOVER_ON_FAILURE,
                "V_MPEG4/ISO/AVC",
                "test-track",
                DEFAULT_BITRATE,
                fps_,
                bufferDuration,
                DEFAULT_REPLAY_DURATION,
                DEFAULT_STALENESS_DURATION,
                DEFAULT_TIMESCALE,
                RECALCULATE_METRICS,
                codecPrivateData,
                new Tag[]{
                        new Tag("device", "Test Device"),
                        new Tag("stream", "Test Stream")},
                nalAdaptationFlags,
                allowStreamCreation
        );

        return kinesisVideoProducer.createStreamSync(streamInfo, streamCallbacks);
    }

    /**
     * Create the stream if it doesn't exist. Calls describe to check if it exists first.
     * Also verifies the retention period and updates it if it's 0.
     *
     * @param streamName the stream to create
     */
    protected void prepareStream(final String streamName) {
        final AmazonKinesisVideo kvs = AmazonKinesisVideoClientBuilder.standard()
                .withRegion(configuration.getRegion())
                .withCredentials(awsCredentialsProvider)
                .build();

        boolean created = false;
        try {
            final DescribeStreamRequest describeStreamRequest = new DescribeStreamRequest();
            describeStreamRequest.setStreamName(streamName);

            final DescribeStreamResult describeStreamResult = kvs.describeStream(describeStreamRequest);
            log.debug("Stream exists! {}", describeStreamResult.getStreamInfo().getStreamARN());


            if (describeStreamResult.getStreamInfo().getDataRetentionInHours() == 0) {
                log.info("Stream {} does not have any retention. Updating...", streamName);

                final UpdateDataRetentionRequest updateDataRetentionRequest = new UpdateDataRetentionRequest();
                updateDataRetentionRequest.setStreamName(streamName);
                updateDataRetentionRequest.setCurrentVersion(describeStreamResult.getStreamInfo().getVersion());
                updateDataRetentionRequest.setOperation(UpdateDataRetentionOperation.INCREASE_DATA_RETENTION.toString());
                updateDataRetentionRequest.setDataRetentionChangeInHours(2);
                kvs.updateDataRetention(updateDataRetentionRequest);
            }

        } catch (final Exception e) {
            final CreateStreamRequest createStreamRequest = new CreateStreamRequest();
            createStreamRequest.setStreamName(streamName);
            createStreamRequest.setDataRetentionInHours(2);
            final CreateStreamResult createStreamResult = kvs.createStream(createStreamRequest);
            log.debug("Stream created! {}", createStreamResult.getStreamARN());
            created = true;
        }

        // In case the stream hasn't finished being created yet
        if (created) {
            for (int i = 0; i < 5; i++) {
                try {
                    final DescribeStreamRequest describeStreamRequest = new DescribeStreamRequest();
                    describeStreamRequest.setStreamName(streamName);

                    final DescribeStreamResult describeStreamResult = kvs.describeStream(describeStreamRequest);
                    log.debug("Stream exists now. ARN: {}", describeStreamResult.getStreamInfo().getStreamARN());
                    break;
                } catch (final Exception e) {
                    log.info("Stream is still creating... {}/{}", i, 3, e);
                    try {
                        Thread.sleep(1000L * (1 << i));
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }

        kvs.shutdown();
    }

    /**
     * This method is used to free the specified kinesisVideoProducerStream from the producer
     *
     * @param kinesisVideoProducerStream the stream to be freed
     */
    protected void freeTestStream(KinesisVideoProducerStream kinesisVideoProducerStream) {
        try {
            kinesisVideoProducer.freeStream(kinesisVideoProducerStream);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    /**
     * This method is used to free all the streams associated with the producer
     */
    protected void freeStreams() {
        try {
            kinesisVideoProducer.freeStreams();
        } catch (ProducerException e) {
            e.printStackTrace();
            fail();
        }
    }

    protected void freeStream(final KinesisVideoProducerStream stream) {
        try {
            kinesisVideoProducer.freeStream(stream);
        } catch (ProducerException e) {
            e.printStackTrace();
            fail();
        }
    }

    protected void deleteStream(final String streamName) {
        final AmazonKinesisVideo awsSdkKinesisVideoClient = AmazonKinesisVideoClient.builder().build();
        final String prefix = Optional.ofNullable(System.getenv("TEST_STREAMS_PREFIX")).orElse("");
        final String finalStreamName = prefix + streamName;
        try {
            final DescribeStreamRequest describeStreamRequest = new DescribeStreamRequest().withStreamName(finalStreamName);
            final DescribeStreamResult describeStreamResult = awsSdkKinesisVideoClient.describeStream(describeStreamRequest);

            final DeleteStreamRequest deleteStreamRequest = new DeleteStreamRequest()
                    .withStreamARN(describeStreamResult.getStreamInfo().getStreamARN())
                    .withCurrentVersion(describeStreamResult.getStreamInfo().getVersion());
            awsSdkKinesisVideoClient.deleteStream(deleteStreamRequest);
        } catch (final Exception e) {
            log.error("Failed to delete the stream: {}", finalStreamName, e);
            fail(e.getMessage());
        }
    }

    /**
     * This method is used to cache stream-info, stream-endpoint and credentials-provider for a stream. It can be called
     * for an existing stream only. It cannot be used to create a stream
     *
     * @param cacheAll       boolean set to true if all -
     *                       credential-provider, stream-info and stream-endpoint need to be cached
     *                       set to false if only stream-endpoint needs to be cached
     * @param testStreamName String name of the stream for which the caching has to take place
     */
    protected void cacheStreamingInfo(boolean cacheAll, String testStreamName) {

        CachedInfoMultiAuthServiceCallbacksImpl cacheServiceCallbacks = new CachedInfoMultiAuthServiceCallbacksImpl(log,
                executor, configuration, serviceClient);
        kinesisVideoClient = new NativeKinesisVideoClient(log,
                authCallbacks,
                storageCallbacks,
                cacheServiceCallbacks,
                streamCallbacks);
        String region = configuration.getRegion();
        AmazonKinesisVideo kvsClient = AmazonKinesisVideoClientBuilder.standard()
                .withRegion(region)
                .withCredentials(awsCredentialsProvider)
                .build();

        if (cacheAll) {
            cacheServiceCallbacks.addCredentialsProviderToCache(testStreamName, awsCredentialsProvider);
            DescribeStreamResult streamInfo = kvsClient.describeStream(new DescribeStreamRequest()
                    .withStreamName(testStreamName));
            cacheServiceCallbacks.addStreamInfoToCache(testStreamName, streamInfo);
        }

        GetDataEndpointResult dataEndpoint =
                kvsClient.getDataEndpoint(new GetDataEndpointRequest().withAPIName(APIName.PUT_MEDIA)
                        .withStreamName(testStreamName));
        cacheServiceCallbacks.addStreamingEndpointToCache(testStreamName, dataEndpoint.getDataEndpoint());
    }

    /**
     * Creates a symmetric KMS key that can be used for encryption/decryption
     * operations with Kinesis Video Streams. The key is created in the same region as
     * configured for the test client.
     *
     * @param keyDescription A human-readable description for the KMS key
     * @return The KMS key ID that can be used for stream encryption
     */
    protected String createKmsKey(final String keyDescription) {
        final AWSKMS kmsClient = AWSKMSClientBuilder.standard()
                .withRegion(configuration.getRegion())
                .withCredentials(awsCredentialsProvider)
                .build();

        final CreateKeyRequest createKeyRequest = new CreateKeyRequest()
                .withDescription(keyDescription)
                .withKeyUsage(KeyUsageType.ENCRYPT_DECRYPT);

        final CreateKeyResult createKeyResult = kmsClient.createKey(createKeyRequest);
        final String keyId = createKeyResult.getKeyMetadata().getKeyId();

        log.info("Created KMS key with ID: {}", keyId);

        // Wait for the key to be fully activated to prevent KMSInvalidStateException
        // when using the key immediately after creation
        try {
            log.debug("Waiting 3 seconds for KMS key {} to be fully activated...", keyId);
            Thread.sleep(3000);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for KMS key activation: {}", keyId);
        }

        return keyId;
    }

    /**
     * Schedules a KMS key for deletion with retry logic.
     *
     * <p>This method schedules the specified KMS key for deletion with the minimum
     * waiting period of 7 days as required by AWS KMS. The key becomes immediately
     * inaccessible for cryptographic operations but is not permanently deleted until
     * the waiting period expires.</p>
     *
     * <p><strong>Retry Logic:</strong></p>
     * <ul>
     *   <li>Retries up to 5 times with exponential backoff for KMSInvalidStateException</li>
     *   <li>Handles cases where key is not ready for deletion (still activating or in use)</li>
     *   <li>Initial delay: 2 seconds, doubles each retry (2s, 4s, 8s, 16s, 32s)</li>
     *   <li>Total maximum wait time: ~126 seconds across all retries</li>
     * </ul>
     *
     * <p><strong>Important Notes:</strong></p>
     * <ul>
     *   <li>Keys cannot be immediately deleted - minimum 7-day waiting period applies</li>
     *   <li>Once scheduled, the key becomes inaccessible for encryption/decryption</li>
     *   <li>The deletion can be canceled during the waiting period if needed</li>
     *   <li>This method logs warnings but does not throw exceptions on failure</li>
     * </ul>
     *
     * <p><strong>Required Permissions:</strong></p>
     * <ul>
     *   <li>kms:ScheduleKeyDeletion</li>
     * </ul>
     *
     * @param keyId The KMS key ID to schedule for deletion
     */
    protected void deleteKmsKey(final String keyId) {
        final AWSKMS kmsClient = AWSKMSClientBuilder.standard()
                .withRegion(configuration.getRegion())
                .withCredentials(awsCredentialsProvider)
                .build();

        final int maxRetries = 5;
        int retryCount = 0;
        long delayMs = 2000; // Start with 2 seconds

        while (retryCount < maxRetries) {
            try {
                final ScheduleKeyDeletionRequest deleteRequest = new ScheduleKeyDeletionRequest()
                        .withKeyId(keyId)
                        .withPendingWindowInDays(7); // Minimum allowed value

                kmsClient.scheduleKeyDeletion(deleteRequest);
                log.info("Successfully scheduled KMS key {} for deletion", keyId);
                return; // Success, exit the retry loop

            } catch (com.amazonaws.services.kms.model.KMSInvalidStateException e) {
                retryCount++;
                if (retryCount >= maxRetries) {
                    log.warn("Failed to schedule KMS key {} for deletion after {} attempts. " +
                            "Key may still be in use or not fully activated. Error: {}",
                            keyId, maxRetries, e.getMessage());
                    return;
                }

                log.info("KMS key {} is not ready for deletion (attempt {}/{}). " +
                        "Waiting {}ms before retry. Error: {}",
                        keyId, retryCount, maxRetries, delayMs, e.getErrorMessage());

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while waiting to retry KMS key deletion for {}", keyId);
                    return;
                }

                delayMs *= 2; // Exponential backoff

            } catch (Exception e) {
                log.warn("Failed to schedule KMS key {} for deletion: {}", keyId, e.getMessage());
                return; // For other exceptions, don't retry
            }
        }
    }

    /**
     * Creates a Kinesis Video Stream with KMS encryption.
     *
     * <p>This method creates a new Kinesis Video Stream configured to use the specified
     * KMS key for server-side encryption. The stream is created with a 2-hour data
     * retention period and waits for the stream to become active before returning.</p>
     *
     * @param streamName The name of the stream to create
     * @param kmsKeyId The KMS key ID to use for encryption
     * @throws RuntimeException if stream creation fails or stream doesn't become active within timeout
     */
    protected void createKmsEncryptedStream(final String streamName, final String kmsKeyId) {
        final AmazonKinesisVideo kvs = AmazonKinesisVideoClientBuilder.standard()
                .withRegion(configuration.getRegion())
                .withCredentials(awsCredentialsProvider)
                .build();

        try {
            final CreateStreamRequest createStreamRequest = new CreateStreamRequest()
                    .withStreamName(streamName)
                    .withDataRetentionInHours(2)
                    .withKmsKeyId(kmsKeyId);

            final CreateStreamResult createStreamResult = kvs.createStream(createStreamRequest);
            log.info("Created KMS-encrypted stream: {} with ARN: {}", streamName, createStreamResult.getStreamARN());

            // Wait for stream to be active
            waitForStreamToBeActive(streamName, kvs);
        } catch (Exception e) {
            log.error("Failed to create KMS-encrypted stream: {}", streamName, e);
            throw new RuntimeException("Failed to create KMS-encrypted stream", e);
        }
    }

    /**
     * Waits for a Kinesis Video Stream to become active.
     *
     * <p>This method polls the stream status up to 10 times with exponential backoff
     * until the stream reaches the ACTIVE state. This is necessary because stream
     * creation is asynchronous and the stream must be active before it can accept
     * video data.</p>
     *
     * <p><strong>Polling Strategy:</strong></p>
     * <ul>
     *   <li>Maximum attempts: 10</li>
     *   <li>Backoff: 2 seconds * (attempt + 1)</li>
     *   <li>Total maximum wait time: ~110 seconds</li>
     * </ul>
     *
     * @param streamName The stream name to wait for
     * @param kvs The Kinesis Video client to use for status checks
     * @throws RuntimeException if the stream doesn't become active within the timeout period
     * @throws RuntimeException if interrupted while waiting
     */
    private void waitForStreamToBeActive(String streamName, AmazonKinesisVideo kvs) {
        for (int i = 0; i < 10; i++) {
            try {
                final DescribeStreamRequest describeStreamRequest = new DescribeStreamRequest()
                        .withStreamName(streamName);
                final DescribeStreamResult describeStreamResult = kvs.describeStream(describeStreamRequest);

                if ("ACTIVE".equals(describeStreamResult.getStreamInfo().getStatus())) {
                    log.info("Stream {} is now active", streamName);
                    return;
                }

                log.info("Stream {} status: {}, waiting...", streamName,
                        describeStreamResult.getStreamInfo().getStatus());
                Thread.sleep(2000L * (i + 1));
            } catch (Exception e) {
                log.info("Waiting for stream {} to be active... attempt {}/10", streamName, i + 1);
                try {
                    Thread.sleep(2000L * (i + 1));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for stream", ex);
                }
            }
        }
        throw new RuntimeException("Stream " + streamName + " did not become active within timeout");
    }

    /**
     * Creates a test stream with KMS encryption using the specified KMS key.
     *
     * <p>This method creates both the AWS Kinesis Video Stream (via AWS API) and the
     * corresponding producer stream object for sending data. The stream is configured
     * with the specified KMS key for server-side encryption.</p>
     *
     * <p><strong>Stream Configuration:</strong></p>
     * <ul>
     *   <li>Content Type: video/h264</li>
     *   <li>Codec: V_MPEG4/ISO/AVC</li>
     *   <li>Encryption: Server-side with specified KMS key</li>
     *   <li>Fragmentation: Key frame based</li>
     *   <li>Timecodes: Frame-based, relative</li>
     *   <li>ACKs: Fragment acknowledgments enabled</li>
     * </ul>
     *
     * <p><strong>Usage Pattern:</strong></p>
     * <ol>
     *   <li>Creates the AWS stream with KMS encryption</li>
     *   <li>Waits for stream to become active</li>
     *   <li>Creates the producer stream object</li>
     *   <li>Returns the producer stream for data ingestion</li>
     * </ol>
     *
     * @param streamName The name of the stream to be created
     * @param streamingType The type of the stream (STREAMING_TYPE_REALTIME or STREAMING_TYPE_OFFLINE)
     * @param maxLatency The maximum latency for the stream in 100ns units
     * @param bufferDuration The buffer duration for the stream in 100ns units
     * @param kmsKeyId The KMS key ID to use for encryption
     * @return KinesisVideoProducerStream the created producer stream object
     * @throws RuntimeException if stream creation fails
     */
    protected KinesisVideoProducerStream createTestStreamWithKms(String streamName,
                                                                 StreamInfo.StreamingType streamingType,
                                                                 long maxLatency,
                                                                 long bufferDuration,
                                                                 String kmsKeyId) {
        KinesisVideoProducerStream kinesisVideoProducerStream = null;
        final byte[] AVCC_EXTRA_DATA = {
                (byte) 0x01, (byte) 0x42, (byte) 0x00, (byte) 0x1E, (byte) 0xFF, (byte) 0xE1, (byte) 0x00, (byte) 0x22,
                (byte) 0x27, (byte) 0x42, (byte) 0x00, (byte) 0x1E, (byte) 0x89, (byte) 0x8B, (byte) 0x60, (byte) 0x50,
                (byte) 0x1E, (byte) 0xD8, (byte) 0x08, (byte) 0x80, (byte) 0x00, (byte) 0x13, (byte) 0x88,
                (byte) 0x00, (byte) 0x03, (byte) 0xD0, (byte) 0x90, (byte) 0x70, (byte) 0x30, (byte) 0x00, (byte) 0x5D,
                (byte) 0xC0, (byte) 0x00, (byte) 0x17, (byte) 0x70, (byte) 0x5E, (byte) 0xF7, (byte) 0xC1, (byte) 0xF0,
                (byte) 0x88, (byte) 0x46, (byte) 0xE0, (byte) 0x01, (byte) 0x00, (byte) 0x04, (byte) 0x28, (byte) 0xCE,
                (byte) 0x1F, (byte) 0x20};

        final String prefix = Optional.ofNullable(System.getenv("TEST_STREAMS_PREFIX")).orElse("");
        final String finalStreamName = prefix + streamName;

        // Create the KMS-encrypted stream first
        createKmsEncryptedStream(finalStreamName, kmsKeyId);

        StreamInfo streamInfo = new StreamInfo(
                StreamInfo.STREAM_INFO_CURRENT_VERSION,
                finalStreamName,
                streamingType,
                "video/h264",
                kmsKeyId, // Use the provided KMS key ID instead of NO_KMS_KEY_ID
                RETENTION_ONE_HOUR,
                NOT_ADAPTIVE,
                maxLatency,
                DEFAULT_GOP_DURATION,
                KEYFRAME_FRAGMENTATION,
                USE_FRAME_TIMECODES,
                RELATIVE_TIMECODES,
                REQUEST_FRAGMENT_ACKS,
                RECOVER_ON_FAILURE,
                "V_MPEG4/ISO/AVC",
                "test-track",
                DEFAULT_BITRATE,
                fps_,
                bufferDuration,
                DEFAULT_REPLAY_DURATION,
                DEFAULT_STALENESS_DURATION,
                DEFAULT_TIMESCALE,
                RECALCULATE_METRICS,
                AVCC_EXTRA_DATA,
                new com.amazonaws.kinesisvideo.producer.Tag[]{
                        new com.amazonaws.kinesisvideo.producer.Tag("device", "Test Device"),
                        new com.amazonaws.kinesisvideo.producer.Tag("stream", "Test Stream")},
                NAL_ADAPTATION_FLAG_NONE,
                allowStreamCreation
        );

        try {
            kinesisVideoProducerStream = kinesisVideoProducer.createStreamSync(streamInfo, streamCallbacks);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
        return kinesisVideoProducerStream;
    }

    /**
     * Creates a test stream with KMS encryption using the specified KMS key and custom stream callbacks.
     *
     * <p>This method is similar to {@link #createTestStreamWithKms} but allows specifying custom
     * stream callbacks for advanced testing scenarios, such as per-stream error tracking.</p>
     *
     * @param streamName The name of the stream to be created
     * @param streamingType The type of the stream (STREAMING_TYPE_REALTIME or STREAMING_TYPE_OFFLINE)
     * @param maxLatency The maximum latency for the stream in 100ns units
     * @param bufferDuration The buffer duration for the stream in 100ns units
     * @param kmsKeyId The KMS key ID to use for encryption
     * @param customStreamCallbacks Custom stream callbacks for this specific stream
     * @return KinesisVideoProducerStream the created producer stream object
     * @throws RuntimeException if stream creation fails
     */
    protected KinesisVideoProducerStream createTestStreamWithKmsAndCallbacks(String streamName,
                                                                             StreamInfo.StreamingType streamingType,
                                                                             long maxLatency,
                                                                             long bufferDuration,
                                                                             String kmsKeyId,
                                                                             StreamCallbacks customStreamCallbacks) {
        KinesisVideoProducerStream kinesisVideoProducerStream = null;
        final byte[] AVCC_EXTRA_DATA = {
                (byte) 0x01, (byte) 0x42, (byte) 0x00, (byte) 0x1E, (byte) 0xFF, (byte) 0xE1, (byte) 0x00, (byte) 0x22,
                (byte) 0x27, (byte) 0x42, (byte) 0x00, (byte) 0x1E, (byte) 0x89, (byte) 0x8B, (byte) 0x60, (byte) 0x50,
                (byte) 0x1E, (byte) 0xD8, (byte) 0x08, (byte) 0x80, (byte) 0x00, (byte) 0x13, (byte) 0x88,
                (byte) 0x00, (byte) 0x03, (byte) 0xD0, (byte) 0x90, (byte) 0x70, (byte) 0x30, (byte) 0x00, (byte) 0x5D,
                (byte) 0xC0, (byte) 0x00, (byte) 0x17, (byte) 0x70, (byte) 0x5E, (byte) 0xF7, (byte) 0xC1, (byte) 0xF0,
                (byte) 0x88, (byte) 0x46, (byte) 0xE0, (byte) 0x01, (byte) 0x00, (byte) 0x04, (byte) 0x28, (byte) 0xCE,
                (byte) 0x1F, (byte) 0x20};

        final String prefix = Optional.ofNullable(System.getenv("TEST_STREAMS_PREFIX")).orElse("");
        final String finalStreamName = prefix + streamName;

        // Create the KMS-encrypted stream first
        createKmsEncryptedStream(finalStreamName, kmsKeyId);

        StreamInfo streamInfo = new StreamInfo(
                StreamInfo.STREAM_INFO_CURRENT_VERSION,
                finalStreamName,
                streamingType,
                "video/h264",
                kmsKeyId, // Use the provided KMS key ID instead of NO_KMS_KEY_ID
                RETENTION_ONE_HOUR,
                NOT_ADAPTIVE,
                maxLatency,
                DEFAULT_GOP_DURATION,
                KEYFRAME_FRAGMENTATION,
                USE_FRAME_TIMECODES,
                RELATIVE_TIMECODES,
                REQUEST_FRAGMENT_ACKS,
                RECOVER_ON_FAILURE,
                "V_MPEG4/ISO/AVC",
                "test-track",
                DEFAULT_BITRATE,
                fps_,
                bufferDuration,
                DEFAULT_REPLAY_DURATION,
                DEFAULT_STALENESS_DURATION,
                DEFAULT_TIMESCALE,
                RECALCULATE_METRICS,
                AVCC_EXTRA_DATA,
                new com.amazonaws.kinesisvideo.producer.Tag[]{
                        new com.amazonaws.kinesisvideo.producer.Tag("device", "Test Device"),
                        new com.amazonaws.kinesisvideo.producer.Tag("stream", "Test Stream")},
                NAL_ADAPTATION_FLAG_NONE,
                allowStreamCreation
        );

        try {
            kinesisVideoProducerStream = kinesisVideoProducer.createStreamSync(streamInfo, customStreamCallbacks);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
        return kinesisVideoProducerStream;
    }
}
