package com.amazonaws.kinesisvideo.java.mediasource.file;

import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.internal.mediasource.OnStreamDataAvailable;

import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.amazonaws.kinesisvideo.producer.FrameFlags.FRAME_FLAG_KEY_FRAME;
import static com.amazonaws.kinesisvideo.producer.FrameFlags.FRAME_FLAG_NONE;
import static com.amazonaws.kinesisvideo.producer.Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
import static com.amazonaws.kinesisvideo.producer.Time.NANOS_IN_A_MILLISECOND;

/**
 * Frame source backed by local image files to be loaded into static memory.
 */
@NotThreadSafe
public class PreloadedSampleImageFrameSource {
    public static final int METADATA_INTERVAL = 8;
    private static final long FRAME_DURATION_20_MS = 20L;
    private final ExecutorService executor = Executors.newFixedThreadPool(1);
    private final int fps;
    private final ImageFileMediaSourceConfiguration configuration;

    private OnStreamDataAvailable mkvDataAvailableCallback;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private int frameCounter;
    private final Log log = LogFactory.getLog(PreloadedSampleImageFrameSource.class);
    private final String metadataName = "ImageLoop";
    private int metadataCount = 0;
    private long currentFrameTimestampMs;
    private final long executorShutdownTimeoutSeconds = 5L;

    private static final int FPS_25 = 25;
    private static final String IMAGE_DIR = "src/main/resources/data/h264/";
    private static final String IMAGE_FILENAME_FORMAT = "frame-%03d.h264";
    private static final int START_FILE_INDEX = 1;
    private static final int END_FILE_INDEX = 375;

    private static final List<ByteBuffer> preloadedFrames;

    // Preload image frames into memory.
    static {
        preloadedFrames = new ArrayList<>();
        try {
            final ImageFileMediaSourceConfiguration defaultConfig = new ImageFileMediaSourceConfiguration.Builder()
                        .fps(FPS_25)
                        .dir(IMAGE_DIR)
                        .filenameFormat(IMAGE_FILENAME_FORMAT)
                        .startFileIndex(START_FILE_INDEX)
                        .endFileIndex(END_FILE_INDEX)
                        .allowStreamCreation(false)
                        .build();

            for (int i = defaultConfig.getStartFileIndex(); i <= defaultConfig.getEndFileIndex(); i++) {
                String filename = String.format(defaultConfig.getFilenameFormat(), i);
                Path path = Paths.get(defaultConfig.getDir() + filename);
                byte[] data = Files.readAllBytes(path);
                preloadedFrames.add(ByteBuffer.wrap(data));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to preload image frames", e);
        }
    }


    public PreloadedSampleImageFrameSource(final ImageFileMediaSourceConfiguration configuration) {
        this.configuration = configuration;
        this.fps = configuration.getFps();
        this.currentFrameTimestampMs = configuration.getStartTimeMs();
    }

    public void start() {
        if (isRunning.get()) {
            throw new IllegalStateException("Frame source is already running");
        }

        isRunning.set(true);
        startFrameGenerator();
    }

    public void stop() {
        isRunning.set(false);
        stopFrameGenerator();
    }

    public void onStreamDataAvailable(final OnStreamDataAvailable onMkvDataAvailable) {
        this.mkvDataAvailableCallback = onMkvDataAvailable;
    }

    private void startFrameGenerator() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    generateFrameAndNotifyListener();
                } catch (final KinesisVideoException e) {
                    log.error("Failed to keep generating frames with Exception", e);
                }
            }
        });
    }

    private void generateFrameAndNotifyListener() throws KinesisVideoException {
        final double frameDurationMs = (double) Duration.ofSeconds(1L).toMillis() / fps;
        long nextFrameTimeNs = System.nanoTime(); // to prevent time drift

        while (isRunning.get()) {
            if (mkvDataAvailableCallback != null) {
                mkvDataAvailableCallback.onFrameDataAvailable(createKinesisVideoFrameFromImage(frameCounter, currentFrameTimestampMs));
                if (isMetadataReady()) {
                    mkvDataAvailableCallback.onFragmentMetadataAvailable(metadataName + metadataCount,
                            Integer.toString(metadataCount++), false);
                }
            }

            frameCounter++;
            currentFrameTimestampMs = configuration.getStartTimeMs() + Math.round(frameCounter * frameDurationMs);
            nextFrameTimeNs += (long)(frameDurationMs * NANOS_IN_A_MILLISECOND);

            long sleepTimeMs = (nextFrameTimeNs - System.nanoTime()) / NANOS_IN_A_MILLISECOND; // Convert to Ms
            if (sleepTimeMs > 0) {
                try {
                    Thread.sleep(sleepTimeMs);
                } catch (final InterruptedException e) {
                    log.error("Frame interval wait interrupted by Exception ", e);
                }
            }
        }
    }

    private boolean isMetadataReady() {
        return frameCounter % METADATA_INTERVAL == 0;
    }

    private KinesisVideoFrame createKinesisVideoFrameFromImage(final long index, final long timestampMs) {
        final int flags = isKeyFrame() ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE;
        int preloadIndex = (int) (index % preloadedFrames.size());
        ByteBuffer frameData = preloadedFrames.get(preloadIndex).duplicate(); // duplicate() used so each instance can track its own position.

        return new KinesisVideoFrame(
                frameCounter,
                flags,
                timestampMs * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                timestampMs * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                FRAME_DURATION_20_MS * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                frameData);
    }

    private boolean isKeyFrame() {
        return frameCounter % configuration.getFps() == 0;
    }


    private void stopFrameGenerator() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(this.executorShutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                log.warn("Executor did not terminate in time. Forcing shutdown.");
                final List<Runnable> droppedTasks = executor.shutdownNow();
                log.warn("Number of dropped tasks: " + droppedTasks.size());
                for (final Runnable task : droppedTasks) {
                    log.warn("Dropped task of type: " + task.getClass().getName());
                }
            }
        } catch (final InterruptedException e) {
            log.error("Executor shutdown interrupted with Exception ", e);
            final List<Runnable> droppedTasks = executor.shutdownNow();
            log.warn("Number of dropped tasks: " + droppedTasks.size());
            for (final Runnable task : droppedTasks) {
                log.warn("Dropped task of type: " + task.getClass().getName());
            }
            Thread.currentThread().interrupt();
        }
    }
}
