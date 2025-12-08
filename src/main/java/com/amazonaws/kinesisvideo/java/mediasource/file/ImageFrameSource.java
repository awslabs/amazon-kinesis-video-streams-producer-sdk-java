package com.amazonaws.kinesisvideo.java.mediasource.file;

import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.internal.mediasource.OnStreamDataAvailable;
import com.amazonaws.kinesisvideo.internal.producer.StreamEventMetadata;
import com.amazonaws.kinesisvideo.internal.producer.StreamEventType;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
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
 * Frame source backed by local image files.
 */
@NotThreadSafe
public class ImageFrameSource {
    private static final long FRAME_DURATION_20_MS = 20L;
    @Nonnull
    private final ExecutorService executor;
    private final int fps;
    private final ImageFileMediaSourceConfiguration configuration;

    private final int totalFiles;
    private OnStreamDataAvailable mkvDataAvailableCallback;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private int frameCounter;
    private final Log log = LogFactory.getLog(ImageFrameSource.class);
    private final String metadataName = "ImageLoop";
    private int metadataCount = 0;
    private long currentFrameTimestampMs;
    private final long executorShutdownTimeoutSeconds = 5L;
    private final HashMap<String, String> metadataValues = new HashMap<>();
    private final StreamEventMetadata eventMetadata;
    
    {
        metadataValues.put("eventMetadata-name-1", "eventMetadata-value-1");
        eventMetadata = new StreamEventMetadata(metadataValues);
    }

    public ImageFrameSource(final ImageFileMediaSourceConfiguration configuration) {
        this.configuration = configuration;
        this.totalFiles = getTotalFiles(configuration.getStartFileIndex(), configuration.getEndFileIndex());
        this.fps = configuration.getFps();
        this.currentFrameTimestampMs = configuration.getStartTimeMs();
        this.executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(configuration
                .getFrameGeneratorThreadName()).build());
    }

    private int getTotalFiles(final int startIndex, final int endIndex) {
        Preconditions.checkState(endIndex >= startIndex);
        return endIndex - startIndex + 1;
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
                final KinesisVideoFrame frame = createKinesisVideoFrameFromImage(frameCounter, currentFrameTimestampMs);
                mkvDataAvailableCallback.onFrameDataAvailable(frame);
                if (frame.getFlags() == FRAME_FLAG_KEY_FRAME) {
                    mkvDataAvailableCallback.onFragmentMetadataAvailable(metadataName + metadataCount,
                            Integer.toString(metadataCount++), false);
                    
                    if (isKeyFrame()) {
                        // Put event metadata on keyframes.
                        mkvDataAvailableCallback.onEventMetadataAvailable(StreamEventType.STREAM_EVENT_TYPE_IMAGE_GENERATION.getIntType(), eventMetadata);
                    }
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


    private KinesisVideoFrame createKinesisVideoFrameFromImage(final long index, final long timestampMs) {
        final String filename = String.format(
                configuration.getFilenameFormat(),
                index % totalFiles + configuration.getStartFileIndex());
        final Path path = Paths.get(configuration.getDir() + filename);

        final int flags = isKeyFrame() ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE;

        try {
            final byte[] data = Files.readAllBytes(path);
            return new KinesisVideoFrame(
                    frameCounter,
                    flags,
                    timestampMs * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                    timestampMs * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                    FRAME_DURATION_20_MS * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                    ByteBuffer.wrap(data));
        } catch (final IOException e) {
            log.error("Read file failed with Exception ", e);
        }

        return null;
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
