package com.amazonaws.kinesisvideo.common;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.kinesisvideo.auth.DefaultAuthCallbacks;
import com.amazonaws.kinesisvideo.client.KinesisVideoClientConfiguration;
import com.amazonaws.kinesisvideo.common.logging.Log;
import com.amazonaws.kinesisvideo.common.logging.LogLevel;
import com.amazonaws.kinesisvideo.demoapp.auth.AuthHelper;
import com.amazonaws.kinesisvideo.internal.client.NativeKinesisVideoClient;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducer;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.internal.service.DefaultServiceCallbacksImpl;
import com.amazonaws.kinesisvideo.java.auth.JavaCredentialsProviderImpl;
import com.amazonaws.kinesisvideo.java.logging.SysOutLogChannel;
import com.amazonaws.kinesisvideo.java.service.CachedInfoMultiAuthServiceCallbacksImpl;
import com.amazonaws.kinesisvideo.java.service.JavaKinesisVideoServiceClient;
import com.amazonaws.kinesisvideo.producer.*;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.kinesisvideo.storage.DefaultStorageCallbacks;
import com.amazonaws.kinesisvideo.streaming.DefaultStreamCallbacks;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClientBuilder;
import com.amazonaws.services.kinesisvideo.model.*;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.amazonaws.kinesisvideo.producer.StreamInfo.NalAdaptationFlags.NAL_ADAPTATION_FLAG_NONE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.*;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.RECALCULATE_METRICS;
import static org.junit.Assert.fail;

public class ProducerTestBase {
    public static final long TEST_BUFFER_DURATION = 12000L * Time.HUNDREDS_OF_NANOS_IN_A_SECOND; // 120 seconds
    public static final long TEST_LATENCY = 6000L * Time.HUNDREDS_OF_NANOS_IN_A_SECOND; // 60 seconds
    public static final int FRAME_FLAG_KEY_FRAME = 1;
    public static final int FRAME_FLAG_NONE = 0;
    public static final int TEST_FRAME_SIZE_BYTES_1000 = 1000;
    public static final int TEST_FPS = 20;
    public static final int TEST_KEY_FRAME_INTERVAL = 20;
    public static final int TEST_MEDIA_DURATION_SECONDS = 60;
    public static final int TEST_TOTAL_FRAME_COUNT = TEST_FPS * TEST_MEDIA_DURATION_SECONDS;
    public static final long TEST_FRAME_DURATION = 1000 * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND / TEST_FPS;

    private static final int NUMBER_OF_THREADS_IN_POOL = 2;
    private static final int NUMBER_OF_STREAMS = 10;

    private static final int DEVICE_VERSION = 0;
    private static final String DEVICE_NAME = "java-test-application";

    private static final int STORAGE_SIZE_MEGS = 64 * 1024 * 1024;
    private static final int SPILL_RATIO_90_PERCENT = 90;
    private static final String STORAGE_PATH = "/tmp";

    private StorageInfo storageInfo = new StorageInfo(0,
            StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM, STORAGE_SIZE_MEGS,
            SPILL_RATIO_90_PERCENT, STORAGE_PATH);

    private DeviceInfo deviceInfo = new DeviceInfo(DEVICE_VERSION,
            DEVICE_NAME, getStorageInfo(), NUMBER_OF_STREAMS, null);

    private StreamCallbacks streamCallbacks;
    private KinesisVideoClientConfiguration configuration;
    private AWSCredentialsProvider awsCredentialsProvider;
    private JavaKinesisVideoServiceClient serviceClient;
    private ScheduledExecutorService executor;
    private Log log;
    private NativeKinesisVideoClient kinesisVideoClient;
    private AuthCallbacks authCallbacks;
    private StorageCallbacks storageCallbacks;
    private KinesisVideoProducer kinesisVideoProducer;

    protected boolean stopCalled;
    protected boolean frameDropped;
    protected boolean bufferDurationPressure;
    protected boolean storageOverflow;
    protected boolean bufferingAckInSequence;
    protected int errorStatus;
    protected int latencyPressureCount;


    public StorageInfo getStorageInfo() {
        return storageInfo;
    }

    public DeviceInfo getDeviceInfo() {
        return deviceInfo;
    }

    public void setDeviceInfo(int version, @Nullable final String name, @Nonnull final StorageInfo storageInfo,
                                      int streamCount, @Nullable final Tag[] tags) {
        deviceInfo = new DeviceInfo(version, name, storageInfo, streamCount, tags);
    }

    public void setStorageInfo(int version, StorageInfo.DeviceStorageType deviceStorageType, long storageSize, int spillRatio,
                                       @Nonnull String rootDirectory) {
        storageInfo = new StorageInfo(version, deviceStorageType, storageSize, spillRatio, rootDirectory);
    }

    public void createProducer() {

        executor = Executors.newScheduledThreadPool(NUMBER_OF_THREADS_IN_POOL,
                new ThreadFactoryBuilder().setNameFormat("KVS-JavaClientExecutor-%d").build());

        awsCredentialsProvider = AuthHelper.getSystemPropertiesCredentialsProvider();
        configuration = KinesisVideoClientConfiguration.builder()
                .withRegion(Regions.US_WEST_2.getName())
                .withCredentialsProvider(new JavaCredentialsProviderImpl(awsCredentialsProvider))
                .withLogChannel(new SysOutLogChannel())
                .withStorageCallbacks(new DefaultStorageCallbacks())
                .build();

        log = new Log(configuration.getLogChannel(), LogLevel.VERBOSE, "KinesisVideoProducerApiTest");

        serviceClient = new JavaKinesisVideoServiceClient(log);
        authCallbacks = new DefaultAuthCallbacks(configuration.getCredentialsProvider(),
                executor,
                log);
        storageCallbacks = configuration.getStorageCallbacks();
        streamCallbacks = new DefaultStreamCallbacks();

        DefaultServiceCallbacksImpl defaultServiceCallbacks = new DefaultServiceCallbacksImpl(log, executor, configuration, serviceClient);
        kinesisVideoClient = new NativeKinesisVideoClient(log,
                    authCallbacks,
                    storageCallbacks,
                defaultServiceCallbacks,
                    streamCallbacks);

        try {
            kinesisVideoProducer = kinesisVideoClient.initializeNewKinesisVideoProducer(getDeviceInfo());
        } catch(Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    public KinesisVideoProducerStream createTestStream(String streamName, StreamInfo.StreamingType streamingType, long maxLatency, long bufferDuration) {
        KinesisVideoProducerStream kinesisVideoProducerStream = null;
        final byte[] AVCC_EXTRA_DATA = {
                (byte) 0x01, (byte) 0x42, (byte) 0x00, (byte) 0x1E, (byte) 0xFF, (byte) 0xE1, (byte) 0x00, (byte) 0x22,
                (byte) 0x27, (byte) 0x42, (byte) 0x00, (byte) 0x1E, (byte) 0x89, (byte) 0x8B, (byte) 0x60, (byte) 0x50,
                (byte) 0x1E, (byte) 0xD8, (byte) 0x08, (byte) 0x80, (byte) 0x00, (byte) 0x13, (byte) 0x88,
                (byte) 0x00, (byte) 0x03, (byte) 0xD0, (byte) 0x90, (byte) 0x70, (byte) 0x30, (byte) 0x00, (byte) 0x5D,
                (byte) 0xC0, (byte) 0x00, (byte) 0x17, (byte) 0x70, (byte) 0x5E, (byte) 0xF7, (byte) 0xC1, (byte) 0xF0,
                (byte) 0x88, (byte) 0x46, (byte) 0xE0, (byte) 0x01, (byte) 0x00, (byte) 0x04, (byte) 0x28, (byte) 0xCE,
                (byte) 0x1F, (byte) 0x20};

        int fps = 25;
        StreamInfo streamInfo = new StreamInfo(VERSION_ZERO,
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
                fps,
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

    public void freeTestStream(KinesisVideoProducerStream kinesisVideoProducerStream) {
        try {
            kinesisVideoProducer.freeStream(kinesisVideoProducerStream);
        } catch(Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    public void freeStreams() {
        try {
            kinesisVideoProducer.freeStreams();
        } catch(ProducerException e) {
            e.printStackTrace();
            fail();
        }
    }

    public void cacheStreamingEndpoint(boolean all, String testStreamName) {

        CachedInfoMultiAuthServiceCallbacksImpl cacheServiceCallbacks = new CachedInfoMultiAuthServiceCallbacksImpl(log, executor, configuration, serviceClient);
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

        if(all) {
            cacheServiceCallbacks.addCredentialsProviderToCache(testStreamName, awsCredentialsProvider);
            DescribeStreamResult streamInfo = kvsClient.describeStream(new DescribeStreamRequest().withStreamName(testStreamName));
            cacheServiceCallbacks.addStreamInfoToCache(testStreamName, streamInfo);
        }

        GetDataEndpointResult dataEndpoint =
                kvsClient.getDataEndpoint(new GetDataEndpointRequest().withAPIName(APIName.PUT_MEDIA).withStreamName(testStreamName));
        cacheServiceCallbacks.addStreamingEndpointToCache(testStreamName, dataEndpoint.getDataEndpoint());
    }

}
