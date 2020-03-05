package com.amazonaws.kinesisvideo.demoapp;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.kinesisvideo.client.KinesisVideoClient;
import com.amazonaws.kinesisvideo.client.KinesisVideoClientConfiguration;
import com.amazonaws.kinesisvideo.common.logging.Log;
import com.amazonaws.kinesisvideo.common.logging.LogLevel;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSource;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.demoapp.auth.AuthHelper;
import com.amazonaws.kinesisvideo.java.auth.JavaCredentialsProviderImpl;
import com.amazonaws.kinesisvideo.java.client.KinesisVideoJavaClientFactory;
import com.amazonaws.kinesisvideo.java.logging.SysOutLogChannel;
import com.amazonaws.kinesisvideo.java.mediasource.file.ImageFileMediaSource;
import com.amazonaws.kinesisvideo.java.mediasource.file.ImageFileMediaSourceConfiguration;
import com.amazonaws.kinesisvideo.java.service.CachedInfoMultiAuthServiceCallbacksImpl;
import com.amazonaws.kinesisvideo.java.service.JavaKinesisVideoServiceClient;
import com.amazonaws.kinesisvideo.storage.DefaultStorageCallbacks;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClientBuilder;
import com.amazonaws.services.kinesisvideo.model.APIName;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamResult;
import com.amazonaws.services.kinesisvideo.model.GetDataEndpointRequest;
import com.amazonaws.services.kinesisvideo.model.GetDataEndpointResult;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Demo Java Producer with Cached Stream Information to lower start latency.
 */
public final class DemoAppCachedInfo {
    // Use a different stream name when testing audio/video sample
    private static final String STREAM_NAME = "my-stream-cached";
    private static final int FPS_25 = 25;
    private static final int RETENTION_ONE_HOUR = 1;
    private static final String IMAGE_DIR = "src/main/resources/data/h264/";
    private static final String FRAME_DIR = "src/main/resources/data/audio-video-frames";
    private static final int STREAM_DURATION_IN_MS = 10000;
    // CHECKSTYLE:SUPPRESS:LineLength
    // This is a reference pipline to extract frames. Need to get key frame configured properly so the output can be
    // decoded. h264 files can be decoded using gstreamer plugin
    // gst-launch-1.0 rtspsrc location="YourRtspUri" short-header=TRUE protocols=tcp ! rtph264depay ! decodebin ! videorate ! videoscale ! vtenc_h264_hw allow-frame-reordering=FALSE max-keyframe-interval=25 bitrate=1024 realtime=TRUE ! video/x-h264,stream-format=avc,alignment=au,profile=baseline,width=640,height=480,framerate=1/25 ! multifilesink location=./frame-%03d.h264 index=1
    private static final String IMAGE_FILENAME_FORMAT = "frame-%03d.h264";
    private static final int START_FILE_INDEX = 1;
    private static final int END_FILE_INDEX = 375;
    private static final int NUMBER_OF_THREADS_IN_POOL = 10;

    private DemoAppCachedInfo() {
        throw new UnsupportedOperationException();
    }

    public static void main(final String[] args) {
        try {
            final ScheduledExecutorService executor = Executors.newScheduledThreadPool(NUMBER_OF_THREADS_IN_POOL,
                    new ThreadFactoryBuilder().setNameFormat("KVS-JavaClientExecutor-%d").build());
            final AWSCredentialsProvider awsCredentialsProvider = AuthHelper.getSystemPropertiesCredentialsProvider();
            final KinesisVideoClientConfiguration configuration = KinesisVideoClientConfiguration.builder()
                    .withRegion(Regions.US_WEST_2.getName())
                    .withCredentialsProvider(new JavaCredentialsProviderImpl(awsCredentialsProvider))
                    .withLogChannel(new SysOutLogChannel())
                    .withStorageCallbacks(new DefaultStorageCallbacks())
                    .build();
            final Log log = new Log(configuration.getLogChannel(), LogLevel.DEBUG, "KinesisVideo");

            // Create CachedInfoServiceCallback
            final CachedInfoMultiAuthServiceCallbacksImpl serviceCallbacks =
                    new CachedInfoMultiAuthServiceCallbacksImpl(log, executor,
                            configuration, new JavaKinesisVideoServiceClient(log));
            // create Kinesis Video high level client
            final KinesisVideoClient kinesisVideoClient = KinesisVideoJavaClientFactory
                    .createKinesisVideoClient(log, configuration, executor, null, serviceCallbacks);


            final String streamName1 = STREAM_NAME + "-account-1";
            final String streamName2 = STREAM_NAME + "-account-2";
            // Cached stream info can be added to callback provider any time, different credentials callback could be
            // used for different streams
            addCachedStreamInfoWithCredentialsProvider(serviceCallbacks, streamName1, awsCredentialsProvider,
                    configuration.getRegion());
            addCachedStreamInfoWithCredentialsProvider(serviceCallbacks, streamName2, awsCredentialsProvider,
                    configuration.getRegion());

            // create a media source. this class produces the data and pushes it into
            // Kinesis Video Producer lower level components
            final MediaSource mediaSource1 = createImageFileMediaSource(streamName1);

            // register media source with Kinesis Video Client
            // NOTE: CachedInfoMultiAuthServiceCallbacksImpl can be used with registerMediaSourceAsync only now
            kinesisVideoClient.registerMediaSourceAsync(mediaSource1);

            // start streaming
            mediaSource1.start();

            final MediaSource mediaSource2 = createImageFileMediaSource(streamName2);

            // register media source with Kinesis Video Client
            // NOTE: CachedInfoMultiAuthServiceCallbacksImpl can be used with registerMediaSourceAsync only now
            kinesisVideoClient.registerMediaSourceAsync(mediaSource2);

            // start streaming
            mediaSource2.start();

            // Run for 10 seconds then stop
            try {
                Thread.sleep(STREAM_DURATION_IN_MS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // unregister stream from client and free client
            serviceCallbacks.removeStreamFromCache(streamName1);
            kinesisVideoClient.unregisterMediaSource(mediaSource1);
            serviceCallbacks.removeStreamFromCache(streamName2);
            kinesisVideoClient.unregisterMediaSource(mediaSource2);
            kinesisVideoClient.free();
            executor.shutdown();
        } catch (final KinesisVideoException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create a MediaSource based on local sample H.264 frames.
     *
     * @return a MediaSource backed by local H264 frame files
     */
    private static MediaSource createImageFileMediaSource(String streamName) {
        final ImageFileMediaSourceConfiguration configuration =
                new ImageFileMediaSourceConfiguration.Builder()
                        .fps(FPS_25)
                        .dir(IMAGE_DIR)
                        .filenameFormat(IMAGE_FILENAME_FORMAT)
                        .startFileIndex(START_FILE_INDEX)
                        .endFileIndex(END_FILE_INDEX)
                        //.contentType("video/hevc") // for h265
                        .build();
        final ImageFileMediaSource mediaSource = new ImageFileMediaSource(streamName);
        mediaSource.configure(configuration);

        return mediaSource;
    }



    private static void addCachedStreamInfoWithCredentialsProvider(CachedInfoMultiAuthServiceCallbacksImpl serviceCallbacks,
                                                                   String streamName,
                                                                   AWSCredentialsProvider credentialsProvider,
                                                                   String region) {
        // Set up credentials provider for the stream name
        serviceCallbacks.addCredentialsProviderToCache(streamName, credentialsProvider);

        // Set up stream info for the stream name
        AmazonKinesisVideo kvsClient = AmazonKinesisVideoClientBuilder.standard()
                .withRegion(region)
                .withCredentials(credentialsProvider)
                .build();
        DescribeStreamResult streamInfo = kvsClient.describeStream(new DescribeStreamRequest().withStreamName(streamName));
        serviceCallbacks.addStreamInfoToCache(streamName, streamInfo);

        // Set up endpoint for the stream name
        GetDataEndpointResult dataEndpoint =
                kvsClient.getDataEndpoint(new GetDataEndpointRequest().withAPIName(APIName.PUT_MEDIA).withStreamName(streamName));
        serviceCallbacks.addStreamingEndpointToCache(streamName, dataEndpoint.getDataEndpoint());
    }
}
