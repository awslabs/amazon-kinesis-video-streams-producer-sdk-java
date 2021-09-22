package com.amazonaws.kinesisvideo.common;

import static com.amazonaws.kinesisvideo.java.client.KinesisVideoJavaClientFactory.createKinesisVideoClient;
import static com.amazonaws.kinesisvideo.producer.StreamInfo.NalAdaptationFlags.NAL_ADAPTATION_FLAG_NONE;
import static com.amazonaws.kinesisvideo.producer.StreamInfo.codecIdFromContentType;
import static com.amazonaws.kinesisvideo.producer.Time.HUNDREDS_OF_NANOS_IN_AN_HOUR;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.*;
import static org.junit.Assert.*;

import javax.annotation.Nonnull;

import com.amazonaws.kinesisvideo.auth.DefaultAuthCallbacks;
import com.amazonaws.kinesisvideo.client.KinesisVideoClient;
import com.amazonaws.kinesisvideo.client.KinesisVideoClientConfiguration;
import com.amazonaws.kinesisvideo.common.logging.Log;
import com.amazonaws.kinesisvideo.common.logging.LogLevel;
import com.amazonaws.kinesisvideo.internal.client.NativeKinesisVideoClient;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducer;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.internal.producer.ServiceCallbacks;
import com.amazonaws.kinesisvideo.internal.producer.jni.NativeKinesisVideoProducerJni;
import com.amazonaws.kinesisvideo.internal.service.DefaultServiceCallbacksImpl;
import com.amazonaws.kinesisvideo.java.auth.JavaCredentialsProviderImpl;
import com.amazonaws.kinesisvideo.java.client.JavaKinesisVideoClient;
import com.amazonaws.kinesisvideo.java.logging.SysOutLogChannel;
import com.amazonaws.kinesisvideo.java.service.JavaKinesisVideoServiceClient;
import com.amazonaws.kinesisvideo.producer.*;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.storage.DefaultStorageCallbacks;
import com.amazonaws.kinesisvideo.streaming.DefaultStreamCallbacks;
import com.amazonaws.regions.Regions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.junit.Before;
import org.junit.Test;
import com.amazonaws.auth.AWSCredentialsProvider;

import com.amazonaws.kinesisvideo.demoapp.auth.AuthHelper;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;


public class ProducerApiTest {

    private static final int DEVICE_VERSION = 0;
    private static final int TEN_STREAMS = 1;
    private static final int SPILL_RATIO_90_PERCENT = 90;
    private static final int STORAGE_SIZE_MEGS = 256 * 1000 * 1000;
    private static final String DEVICE_NAME = "java-test-application";
    private static final int TEST_STREAM_COUNT = 1;
    private static final String STORAGE_PATH = "/tmp";
    private static final int NUMBER_OF_THREADS_IN_POOL = 2;
    private static final int FPS_25 = 25;
    private static final long RETENTION_ONE_HOUR = 1L * HUNDREDS_OF_NANOS_IN_AN_HOUR;

    private NativeKinesisVideoClient kinesisVideoClient = null;
    private KinesisVideoProducer kinesisVideoProducer = null;
    private KinesisVideoProducerStream [] kinesisVideoProducerStreams = new KinesisVideoProducerStream[TEST_STREAM_COUNT];

    private AuthCallbacks authCallbacks;
    private StorageCallbacks storageCallbacks;
    private ServiceCallbacks serviceCallbacks;
    private StreamCallbacks streamCallbacks;

    private DeviceInfo deviceInfo = new DeviceInfo(
            DEVICE_VERSION,
            DEVICE_NAME,
            getStorageInfo(),
            TEN_STREAMS,
            getDeviceTags());


    private String streamName = "HappyStreaming";

    private static DeviceInfo getDeviceInfo() {
        return new DeviceInfo(
                DEVICE_VERSION,
                DEVICE_NAME,
                getStorageInfo(),
                TEN_STREAMS,
                getDeviceTags());
    }

    private static StorageInfo getStorageInfo() {
        return new StorageInfo(0,
                StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM,
                STORAGE_SIZE_MEGS,
                SPILL_RATIO_90_PERCENT,
                STORAGE_PATH);
    }

    private static Tag[] getDeviceTags() {
        // Tags for devices are not supported yet

        return null;
    }

    public void createProducer() {
        final AWSCredentialsProvider awsCredentialsProvider = AuthHelper.getSystemPropertiesCredentialsProvider();
        final KinesisVideoClientConfiguration configuration = KinesisVideoClientConfiguration.builder()
                .withRegion(Regions.US_WEST_2.getName())
                .withCredentialsProvider(new JavaCredentialsProviderImpl(awsCredentialsProvider))
                .withLogChannel(new SysOutLogChannel())
                .withStorageCallbacks(new DefaultStorageCallbacks())
                .build();

        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(NUMBER_OF_THREADS_IN_POOL,
                new ThreadFactoryBuilder().setNameFormat("KVS-JavaClientExecutor-%d").build());

        final Log log = new Log(configuration.getLogChannel(), LogLevel.VERBOSE, "KinesisVideoProducerApiTest");

        final JavaKinesisVideoServiceClient serviceClient = new JavaKinesisVideoServiceClient(log);
        authCallbacks = new DefaultAuthCallbacks(configuration.getCredentialsProvider(),
                executor,
                log);
        storageCallbacks = configuration.getStorageCallbacks();
        serviceCallbacks = new DefaultServiceCallbacksImpl(log,
                executor,
                configuration,
                serviceClient);
        streamCallbacks = new DefaultStreamCallbacks();

        try {

            /*also initializes DefaultAuthCallbacks, configuration.getStorageCallbacks()
             DefaultServiceCallbacksImpl(log, executor, configuration, serviceClient)
             DefaultStreamCallbacks()
             privately, authcallbacks, storagecallbacks, servicecallbacks and
             streamcallbacks set with the next line
            */

            /* kinesisVideoClient = new JavaKinesisVideoClient(log,
                    configuration,
                    serviceClient,
                    executor);

             */

            /*
            to have access to auth, storage, service, stream callbacks we use NativeKinesisVideoClient
            */
            kinesisVideoClient = new NativeKinesisVideoClient(log,
                    authCallbacks,
                    storageCallbacks,
                    serviceCallbacks,
                    streamCallbacks);

            //kinesisVideoClient.initialize(deviceInfo);
            kinesisVideoProducer = kinesisVideoClient.initializeNewKinesisVideoProducer(deviceInfo);
            /* kinesisVideoProducer = new NativeKinesisVideoProducerJni(
                    authCallbacks,
                    storageCallbacks,
                    serviceCallbacks,
                    log);
            kinesisVideoProducer.createSync(deviceInfo);

            /*
            kinesisVideoProducer = new NativeKinesisVideoProducerJni(
                    authCallbacks,
                    storageCallbacks,
                    serviceCallbacks,
                    log);

            kinesisVideoProducer.createSync(getDeviceInfo()); */

        } catch(Exception e) {
            assertTrue(false);
        }

    }

    private KinesisVideoProducerStream createTestStream(String streamName) {

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
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME,
                "video/h264",
                NO_KMS_KEY_ID,
                RETENTION_ONE_HOUR,
                NOT_ADAPTIVE,
                MAX_LATENCY_ZERO,
                DEFAULT_GOP_DURATION,
                KEYFRAME_FRAGMENTATION,
                USE_FRAME_TIMECODES,
                RELATIVE_TIMECODES,
                REQUEST_FRAGMENT_ACKS,
                RECOVER_ON_FAILURE,
                "V_MPEG4/ISO/AVC",
                "test-track",
                DEFAULT_BITRATE,
                FPS_25,
                DEFAULT_BUFFER_DURATION,
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

    private void freeTestStream(KinesisVideoProducerStream kinesisVideoProducerStream) {
        try {
            kinesisVideoProducer.freeStream(kinesisVideoProducerStream);
        } catch(Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void createFreeStream() {
        createProducer();
        String testStreamName = "";
        for(int i = 0 ; i < TEST_STREAM_COUNT ; i++) {
            testStreamName = "JavaProducerApiTestStream" + i;
            kinesisVideoProducerStreams[i] = createTestStream(testStreamName);
        }
        for(int i = 0 ; i < TEST_STREAM_COUNT ; i++) {
            testStreamName = "JavaProducerApiTestStream" + i;
            freeTestStream(kinesisVideoProducerStreams[i]);
            kinesisVideoProducerStreams[i] = null;
            kinesisVideoProducerStreams[i] = createTestStream(testStreamName);
        }
        try {
            kinesisVideoProducer.freeStreams();
        } catch(Exception e) {
            assertTrue(false);
        }
        for(int i = 0 ; i < TEST_STREAM_COUNT ; i++) {
            testStreamName = "JavaProducerApiTestStream" + i;
            kinesisVideoProducerStreams[i] = createTestStream(testStreamName);
        }
    }
}
