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
import com.amazonaws.kinesisvideo.internal.producer.ServiceCallbacks;
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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.amazonaws.kinesisvideo.producer.StreamInfo.NalAdaptationFlags.NAL_ADAPTATION_FLAG_NONE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.*;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.RECALCULATE_METRICS;
import static org.junit.Assert.assertTrue;

public class ProducerTestBase {

    private static int fps = 25;
    private static final int NUMBER_OF_THREADS_IN_POOL = 2;
    private static final int NUMBER_OF_STREAMS = 10;

    private static final int DEVICE_VERSION = 0;
    private static final String DEVICE_NAME = "java-test-application";

    private static final int STORAGE_SIZE_MEGS = 256 * 1000 * 1000;
    private static final int SPILL_RATIO_90_PERCENT = 90;
    private static final String STORAGE_PATH = "/tmp";

    private StreamCallbacks streamCallbacks;
    private DefaultServiceCallbacksImpl defaultServiceCallbacks;
    private CachedInfoMultiAuthServiceCallbacksImpl cacheServiceCallbacks;
    private KinesisVideoClientConfiguration configuration;
    private AWSCredentialsProvider awsCredentialsProvider;

    private KinesisVideoProducer kinesisVideoProducer;


    private static StorageInfo getStorageInfo() {
        return new StorageInfo(0,
                StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM,
                STORAGE_SIZE_MEGS,
                SPILL_RATIO_90_PERCENT,
                STORAGE_PATH);
    }

    private static DeviceInfo getDeviceInfo() {
        return new DeviceInfo(
                DEVICE_VERSION,
                DEVICE_NAME,
                getStorageInfo(),
                NUMBER_OF_STREAMS,
                null);
    }

    public void createProducer(boolean cache) {
        NativeKinesisVideoClient kinesisVideoClient;

        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(NUMBER_OF_THREADS_IN_POOL,
                new ThreadFactoryBuilder().setNameFormat("KVS-JavaClientExecutor-%d").build());

        awsCredentialsProvider = AuthHelper.getSystemPropertiesCredentialsProvider();
        configuration = KinesisVideoClientConfiguration.builder()
                .withRegion(Regions.US_WEST_2.getName())
                .withCredentialsProvider(new JavaCredentialsProviderImpl(awsCredentialsProvider))
                .withLogChannel(new SysOutLogChannel())
                .withStorageCallbacks(new DefaultStorageCallbacks())
                .build();

        final Log log = new Log(configuration.getLogChannel(), LogLevel.VERBOSE, "KinesisVideoProducerApiTest");

        final JavaKinesisVideoServiceClient serviceClient = new JavaKinesisVideoServiceClient(log);
        AuthCallbacks authCallbacks = new DefaultAuthCallbacks(configuration.getCredentialsProvider(),
                executor,
                log);
        StorageCallbacks storageCallbacks = configuration.getStorageCallbacks();
        streamCallbacks = new DefaultStreamCallbacks();


        if(cache) {
            cacheServiceCallbacks = new CachedInfoMultiAuthServiceCallbacksImpl(log, executor, configuration, serviceClient);
            kinesisVideoClient = new NativeKinesisVideoClient(log,
                    authCallbacks,
                    storageCallbacks,
                    cacheServiceCallbacks,
                    streamCallbacks);
        } else {
            defaultServiceCallbacks = new DefaultServiceCallbacksImpl(log, executor, configuration, serviceClient);
            kinesisVideoClient = new NativeKinesisVideoClient(log,
                    authCallbacks,
                    storageCallbacks,
                    defaultServiceCallbacks,
                    streamCallbacks);
        }

        try {
            kinesisVideoProducer = kinesisVideoClient.initializeNewKinesisVideoProducer(getDeviceInfo());
        } catch(Exception e) {
            assertTrue(false);
        }

    }

    public KinesisVideoProducerStream createTestStream(String streamName, StreamInfo.StreamingType streamingType, long maxLatency, long bufferDuration) {

        final byte[] AVCC_EXTRA_DATA = {
                (byte) 0x01, (byte) 0x42, (byte) 0x00, (byte) 0x1E, (byte) 0xFF, (byte) 0xE1, (byte) 0x00, (byte) 0x22,
                (byte) 0x27, (byte) 0x42, (byte) 0x00, (byte) 0x1E, (byte) 0x89, (byte) 0x8B, (byte) 0x60, (byte) 0x50,
                (byte) 0x1E, (byte) 0xD8, (byte) 0x08, (byte) 0x80, (byte) 0x00, (byte) 0x13, (byte) 0x88,
                (byte) 0x00, (byte) 0x03, (byte) 0xD0, (byte) 0x90, (byte) 0x70, (byte) 0x30, (byte) 0x00, (byte) 0x5D,
                (byte) 0xC0, (byte) 0x00, (byte) 0x17, (byte) 0x70, (byte) 0x5E, (byte) 0xF7, (byte) 0xC1, (byte) 0xF0,
                (byte) 0x88, (byte) 0x46, (byte) 0xE0, (byte) 0x01, (byte) 0x00, (byte) 0x04, (byte) 0x28, (byte) 0xCE,
                (byte) 0x1F, (byte) 0x20};

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
            return kinesisVideoProducer.createStreamSync(streamInfo, streamCallbacks);

        } catch(Exception e) {
            assertTrue(false);
        }
        return null;
    }

    public void freeTestStream(KinesisVideoProducerStream kinesisVideoProducerStream) {
        try {
            kinesisVideoProducer.freeStream(kinesisVideoProducerStream);
        } catch(Exception e) {
            assertTrue(false);
        }
    }

    public void freeStreams() {
        try {
            kinesisVideoProducer.freeStreams();
        } catch(ProducerException e) {
            assertTrue(false);
        }
    }

    public void cacheStreamingEndpoint(String testStreamName) {
        String region = configuration.getRegion();
        AmazonKinesisVideo kvsClient = AmazonKinesisVideoClientBuilder.standard()
                .withRegion(region)
                .withCredentials(awsCredentialsProvider)
                .build();

        GetDataEndpointResult dataEndpoint =
                kvsClient.getDataEndpoint(new GetDataEndpointRequest().withAPIName(APIName.PUT_MEDIA).withStreamName(testStreamName));
        cacheServiceCallbacks.addStreamingEndpointToCache(testStreamName, dataEndpoint.getDataEndpoint());
    }

}
