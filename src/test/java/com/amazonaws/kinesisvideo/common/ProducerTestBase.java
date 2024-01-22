package com.amazonaws.kinesisvideo.common;

import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import static org.junit.Assert.fail;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.kinesisvideo.auth.DefaultAuthCallbacks;
import com.amazonaws.kinesisvideo.client.KinesisVideoClientConfiguration;
import com.amazonaws.kinesisvideo.internal.producer.jni.NativeKinesisVideoProducerJni;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.amazonaws.kinesisvideo.demoapp.auth.AuthHelper;
import com.amazonaws.kinesisvideo.internal.client.NativeKinesisVideoClient;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducer;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.internal.service.DefaultServiceCallbacksImpl;
import com.amazonaws.kinesisvideo.java.auth.JavaCredentialsProviderImpl;
import com.amazonaws.kinesisvideo.java.service.CachedInfoMultiAuthServiceCallbacksImpl;
import com.amazonaws.kinesisvideo.java.service.JavaKinesisVideoServiceClient;
import com.amazonaws.kinesisvideo.producer.*;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClientBuilder;
import com.amazonaws.services.kinesisvideo.model.*;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import static com.amazonaws.kinesisvideo.producer.StreamInfo.NalAdaptationFlags.NAL_ADAPTATION_FLAG_NONE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.*;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.RECALCULATE_METRICS;

public class ProducerTestBase {
    protected static final long TEST_BUFFER_DURATION = 12000L * Time.HUNDREDS_OF_NANOS_IN_A_SECOND; // 120 seconds
    protected static final long TEST_LATENCY = 6000L * Time.HUNDREDS_OF_NANOS_IN_A_SECOND; // 60 seconds
    protected static final int FRAME_FLAG_KEY_FRAME = 1;
    protected static final int FRAME_FLAG_NONE = 0;
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
    protected static final int NUMBER_OF_STREAMS = 10;

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
    protected long errorStatus_;
    protected int latencyPressureCount_;
    protected HashMap<Long, Long> previousBufferingAckTimestamp_ = new HashMap<>();

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

        fps_ = 20;
        keyFrameInterval_ = 20;
        frameDuration_ = 1000 * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND / fps_;
    }

    protected long getFragmentDurationMs() {
        return keyFrameInterval_ * frameDuration_ / Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
    }

    /**
     * This method is used to create a KinesisVideoProducer which is used by the later methods
     */
    protected void createProducer() {
        deviceInfo_ = new DeviceInfo(DEVICE_VERSION,
                DEVICE_NAME, storageInfo_, NUMBER_OF_STREAMS, null,
                "JNI " + NativeKinesisVideoProducerJni.EXPECTED_LIBRARY_VERSION,
                new ClientInfo());
        createProducer(deviceInfo_);
    }

    /**
     * This method is used to create a KinesisVideoProducer which is used by the later methods
     */
    protected void createProducer(DeviceInfo deviceInfo) {

        reset(); // reset all flags to initial values so that they can be modified by the stream and storage callbacks

        executor = Executors.newScheduledThreadPool(NUMBER_OF_THREADS_IN_POOL,
                new ThreadFactoryBuilder().setNameFormat("KVS-JavaClientExecutor-%d").build());

        awsCredentialsProvider = AuthHelper.getSystemPropertiesCredentialsProvider();
        configuration = KinesisVideoClientConfiguration.builder()
                .withRegion(Regions.US_WEST_2.getName())
                .withCredentialsProvider(new JavaCredentialsProviderImpl(awsCredentialsProvider))
                .build();

        serviceClient = new JavaKinesisVideoServiceClient();
        authCallbacks = new DefaultAuthCallbacks(configuration.getCredentialsProvider(),
                executor);
        // use TestStorageCallbacks and TestStreamCallbacks to override the callbacks to update the flags in case of
        // overflow, errors and other events. The current ProducerTestBase object is passed to their constructors so
        // that they can access the flags to be updated
        storageCallbacks = new TestStorageCallbacks(this);
        streamCallbacks = new TestStreamCallBacks(this);

        DefaultServiceCallbacksImpl defaultServiceCallbacks = new DefaultServiceCallbacksImpl(log, executor,
                configuration, serviceClient);
        kinesisVideoClient = new NativeKinesisVideoClient(log,
                    authCallbacks,
                    storageCallbacks,
                    defaultServiceCallbacks,
                    streamCallbacks);
        try {
            kinesisVideoProducer = kinesisVideoClient.initializeNewKinesisVideoProducer(deviceInfo);
        } catch(Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    /**
     * This method is used to create a stream with the specified information using the producer created as a part of
     * the createProducer method
     * @param streamName the name of the stream to be created
     * @param streamingType the type of the stream - realtime, offline
     * @param maxLatency the maxLatency for the streamInfo
     * @param bufferDuration the bufferDuration for the streamInfo
     * @return KinesisVideoProducerStream the created stream
     */
    protected KinesisVideoProducerStream createTestStream(String streamName, StreamInfo.StreamingType streamingType,
                                                       long maxLatency, long bufferDuration) {
        KinesisVideoProducerStream kinesisVideoProducerStream = null;
        final byte[] AVCC_EXTRA_DATA = {
                (byte) 0x01, (byte) 0x42, (byte) 0x00, (byte) 0x1E, (byte) 0xFF, (byte) 0xE1, (byte) 0x00, (byte) 0x22,
                (byte) 0x27, (byte) 0x42, (byte) 0x00, (byte) 0x1E, (byte) 0x89, (byte) 0x8B, (byte) 0x60, (byte) 0x50,
                (byte) 0x1E, (byte) 0xD8, (byte) 0x08, (byte) 0x80, (byte) 0x00, (byte) 0x13, (byte) 0x88,
                (byte) 0x00, (byte) 0x03, (byte) 0xD0, (byte) 0x90, (byte) 0x70, (byte) 0x30, (byte) 0x00, (byte) 0x5D,
                (byte) 0xC0, (byte) 0x00, (byte) 0x17, (byte) 0x70, (byte) 0x5E, (byte) 0xF7, (byte) 0xC1, (byte) 0xF0,
                (byte) 0x88, (byte) 0x46, (byte) 0xE0, (byte) 0x01, (byte) 0x00, (byte) 0x04, (byte) 0x28, (byte) 0xCE,
                (byte) 0x1F, (byte) 0x20};

        StreamInfo streamInfo = new StreamInfo(VERSION_TWO,
                streamName,
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
                AVCC_EXTRA_DATA,
                new Tag[] {
                        new Tag("device", "Test Device"),
                        new Tag("stream", "Test Stream") },
                NAL_ADAPTATION_FLAG_NONE);

        try{
            kinesisVideoProducerStream =  kinesisVideoProducer.createStreamSync(streamInfo, streamCallbacks);

        } catch(Exception e) {
            e.printStackTrace();
            fail();
        }
        return kinesisVideoProducerStream;
    }

    /**
     * This method is used to free the specified kinesisVideoProducerStream from the producer
     * @param kinesisVideoProducerStream the stream to be freed
     */
    protected void freeTestStream(KinesisVideoProducerStream kinesisVideoProducerStream) {
        try {
            kinesisVideoProducer.freeStream(kinesisVideoProducerStream);
        } catch(Exception e) {
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
        } catch(ProducerException e) {
            e.printStackTrace();
            fail();
        }
    }

    /**
     * This method is used to cache stream-info, stream-endpoint and credentials-provider for a stream. It can be called
     * for an existing stream only. It cannot be used to create a stream
     * @param cacheAll boolean set to true if all -
     *                 credential-provider, stream-info and stream-endpoint need to be cached
     *                 set to false if only stream-endpoint needs to be cached
     * @param testStreamName String name of the stream for which the caching has to take place
     *
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

        if(cacheAll) {
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
}
