package com.amazonaws.kinesisvideo.demoapp;

import com.amazonaws.kinesisvideo.client.IPVersionFilter;
import com.amazonaws.kinesisvideo.client.KinesisVideoClient;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.demoapp.auth.AuthHelper;
import com.amazonaws.kinesisvideo.demoapp.debug.StatsWriter;
import com.amazonaws.kinesisvideo.internal.client.NativeKinesisVideoClient;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSource;
import com.amazonaws.kinesisvideo.java.client.KinesisVideoJavaClientFactory;
import com.amazonaws.kinesisvideo.java.mediasource.file.ImageFileMediaSource;
import com.amazonaws.kinesisvideo.java.mediasource.file.ImageFileMediaSourceConfiguration;
import com.amazonaws.regions.Regions;
import com.amazonaws.util.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/**
 * Demo Java Producer.
 */
public final class DemoAppMainWithHeapTracking {

    private static final Logger log = LogManager.getLogger(DemoAppMainWithHeapTracking.class);

    private static final String STREAM_NAME = Optional.ofNullable(System.getProperty("kvs-stream")).orElse("");
    private static final int FPS_25 = 25;
    private static final String IMAGE_DIR = "src/main/resources/data/h264/";
    private static final String IMAGE_FILENAME_FORMAT = "frame-%03d.h264";
    private static final int START_FILE_INDEX = 1;
    private static final int END_FILE_INDEX = 375;
    private static final boolean USE_INSTRUMENTED_ALLOCATORS = true;
    private static final Duration DEFAULT_DEFAULT_MALLOC_POLLING_INTERVAL = Duration.ofMillis(200);

    private static final Duration DEFAULT_DURATION_TO_STREAM = Duration.ofSeconds(10);
    private static final Duration DURATION_TO_STREAM = Optional.ofNullable(System.getProperty("stream-duration-ms"))
            .map(value -> {
                try {
                    return Duration.ofMillis(Long.parseLong(value));
                } catch (final NumberFormatException e) {
                    log.error("Invalid stream-duration value: {}. Using default {} ms.", value, DEFAULT_DURATION_TO_STREAM.toMillis());
                    return null;
                }
            })
            .orElse(DEFAULT_DURATION_TO_STREAM);
    private static final String MALLOC_DATA_OUTPUT_PATH = Optional.ofNullable(System.getProperty("malloc-data-output-path"))
            .filter(s -> !StringUtils.isNullOrEmpty(s))
            .orElse("./memory-data.csv");
    private static final Duration MALLOC_DATA_POLLING_INTERVAL = Optional.ofNullable(System.getProperty("malloc-polling-interval-ms"))
            .map(value -> {
                try {
                    return Duration.ofMillis(Long.parseLong(value));
                } catch (final NumberFormatException e) {
                    log.error("Invalid malloc polling interval value: {}. Using default {} ms.", value, DEFAULT_DEFAULT_MALLOC_POLLING_INTERVAL.toMillis());
                    return null;
                }
            })
            .orElse(DEFAULT_DEFAULT_MALLOC_POLLING_INTERVAL);

    private DemoAppMainWithHeapTracking() {
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
                            true,
                            IPVersionFilter.IPV4_AND_IPV6,
                            USE_INSTRUMENTED_ALLOCATORS);

            try (final StatsWriter statsWriter = new StatsWriter(MALLOC_DATA_OUTPUT_PATH,
                    MALLOC_DATA_POLLING_INTERVAL, (NativeKinesisVideoClient) kinesisVideoClient)) {

                final MediaSource mediaSource = createImageFileMediaSource();
                kinesisVideoClient.registerMediaSource(mediaSource);
                mediaSource.start();

                log.info("Main thread sleeping {} ms.", DURATION_TO_STREAM.toMillis());
                Thread.sleep(DURATION_TO_STREAM.toMillis());

                log.info("Stopping stream...");
                mediaSource.stop();
                kinesisVideoClient.unregisterMediaSource(mediaSource);

            } catch (final IOException e) {
                throw new RuntimeException(e);
            }

            kinesisVideoClient.free();
        } catch (final KinesisVideoException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create a MediaSource based on local sample H.264 frames.
     *
     * @return a MediaSource backed by local H264 frame files
     */
    private static MediaSource createImageFileMediaSource() {
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
        final ImageFileMediaSource mediaSource = new ImageFileMediaSource(STREAM_NAME);
        mediaSource.configure(configuration);

        return mediaSource;
    }

}
