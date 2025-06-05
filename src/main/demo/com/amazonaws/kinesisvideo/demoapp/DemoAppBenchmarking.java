package com.amazonaws.kinesisvideo.demoapp;

import com.amazonaws.kinesisvideo.client.IPVersionFilter;
import com.amazonaws.kinesisvideo.client.KinesisVideoClient;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSource;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.demoapp.auth.AuthHelper;
import com.amazonaws.kinesisvideo.java.client.KinesisVideoJavaClientFactory;
import com.amazonaws.kinesisvideo.java.mediasource.file.ImageFileMediaSource;
import com.amazonaws.kinesisvideo.java.mediasource.file.ImageFileMediaSourceConfiguration;
import com.amazonaws.regions.Regions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

/**
 * Demo Java Producer.
 */
public final class DemoAppBenchmarking {

    private static final Logger log = LogManager.getLogger(DemoAppBenchmarking.class);

    // Use a different stream name when testing audio/video sample
    private static final String STREAM_NAME = Optional.ofNullable(System.getProperty("kvs-stream")).orElse("");
    private static final int FPS_25 = 25;
    private static final String IMAGE_DIR = "src/main/resources/data/h264/";
    // CHECKSTYLE:SUPPRESS:LineLength
    // Need to get key frame configured properly so the output can be decoded. h264 files can be decoded using gstreamer plugin
    // gst-launch-1.0 rtspsrc location="YourRtspUri" short-header=TRUE protocols=tcp ! rtph264depay ! decodebin ! videorate ! videoscale ! vtenc_h264_hw allow-frame-reordering=FALSE max-keyframe-interval=25 bitrate=1024 realtime=TRUE ! video/x-h264,stream-format=avc,alignment=au,profile=baseline,width=640,height=480,framerate=1/25 ! multifilesink location=./frame-%03d.h264 index=1
    private static final String IMAGE_FILENAME_FORMAT = "frame-%03d.h264";
    private static final int START_FILE_INDEX = 1;
    private static final int END_FILE_INDEX = 375;

    private static final int STREAM_COUNT = Integer.parseInt(System.getProperty("stream-count"));
    // private static final int STREAM_INTERVALED_COUNT = Integer.parseInt(System.getProperty("stream-intervaled-count"));
    private static final int STREAM_INTERVAL_MS =  Integer.parseInt(System.getProperty("stream-interval-ms"));
    private static final boolean DO_START_STOP = Boolean.parseBoolean(System.getProperty("do-start-stop", "false"));

    private DemoAppBenchmarking() {
        throw new UnsupportedOperationException();
    }

    public static void main(final String[] args) {
        try {
            // create Kinesis Video high level client
            final KinesisVideoClient kinesisVideoClient = KinesisVideoJavaClientFactory
                    .createKinesisVideoClient(
                            Regions.US_WEST_2,
                            AuthHelper.getSystemPropertiesCredentialsProvider(),
                            null,
                            true, IPVersionFilter.IPV4_AND_IPV6);

            // Create an array of media sources
            final MediaSource[] mediaSources = new MediaSource[STREAM_COUNT];

            for (int i = 0; i < mediaSources.length; i++) {
                // create a stream
                // create a media source. this class produces the data and pushes it into
                // Kinesis Video Producer lower level components
                mediaSources[i] = createImageFileMediaSource(String.valueOf(i));

                // register media source with Kinesis Video Client
                kinesisVideoClient.registerMediaSource(mediaSources[i]);

                // start streaming
                mediaSources[i].start();

                // sleep for the interval
                log.warn("Sleeping for {} ms", STREAM_INTERVAL_MS);
                Thread.sleep(STREAM_INTERVAL_MS);
                
            }

            // Stop and start streams if doing start/stop
            if (DO_START_STOP) {
                log.warn("Starting to stop and start streams");
                for (int i = 0; i < mediaSources.length; i++) {
                    log.warn("Stopping stream {}", i);
                    kinesisVideoClient.unregisterMediaSource(mediaSources[i]);
                    log.warn("Sleeping for {} ms", STREAM_INTERVAL_MS);
                    Thread.sleep(STREAM_INTERVAL_MS);  
                }

                log.warn("Sleeping for 60 seconds to allow streams to stabilize");
                Thread.sleep(60000);

                for (int i = 0; i < mediaSources.length; i++) {
                    log.warn("Starting stream {}", i);

                    mediaSources[i] = createImageFileMediaSource(String.valueOf(i));
                    kinesisVideoClient.registerMediaSource(mediaSources[i]);
                    mediaSources[i].start();
                    
                    log.warn("Sleeping for {} ms", STREAM_INTERVAL_MS);
                    Thread.sleep(STREAM_INTERVAL_MS);
                }

                log.warn("Done stopping and starting streams");
                log.warn("Sleeping for 60 seconds to allow streams to stabilize");
                Thread.sleep(60000);
            }

            // Stop the streams
            for (int i = 0; i < mediaSources.length; i++) {
                log.warn("unregistering stream {}", i);
                kinesisVideoClient.unregisterMediaSource(mediaSources[i]);
            }

            log.warn("freeing client");
            kinesisVideoClient.free();
            log.warn("done freeing client");

        } catch (final KinesisVideoException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create a MediaSource based on local sample H.264 frames.
     *
     * @return a MediaSource backed by local H264 frame files
     */
    private static MediaSource createImageFileMediaSource(String streamNameSuffix) {
        final ImageFileMediaSourceConfiguration configuration =
                new ImageFileMediaSourceConfiguration.Builder()
                        .fps(FPS_25)
                        .dir(IMAGE_DIR)
                        .filenameFormat(IMAGE_FILENAME_FORMAT)
                        .startFileIndex(START_FILE_INDEX)
                        .endFileIndex(END_FILE_INDEX)
                        //.contentType("video/hevc") // for h265
                        .allowStreamCreation(false)
                        .build();
        final ImageFileMediaSource mediaSource = new ImageFileMediaSource(STREAM_NAME + "_" + streamNameSuffix);
        mediaSource.configure(configuration);

        return mediaSource;
    }
}
