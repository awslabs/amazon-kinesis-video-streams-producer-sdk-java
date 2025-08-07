package com.amazonaws.kinesisvideo.java.mediasource.file;

import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.internal.mediasource.OnStreamDataAvailable;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;

import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.amazonaws.kinesisvideo.producer.FrameFlags.*;
import static com.amazonaws.kinesisvideo.producer.Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;

/**
 * Frame source backed by local audio files (PCM).
 * 
 * <p>This class provides functionality to read audio frames from local PCM files and stream them
 * at a specified frame rate. It supports looping through a range of audio files and generates
 * KinesisVideoFrame objects that can be consumed by Kinesis Video Streams.</p>
 * 
 * <p>Key features:</p>
 * <ul>
 *   <li>Reads PCM audio files from a specified directory</li>
 *   <li>Supports configurable frame rate (FPS)</li>
 *   <li>Loops through a range of files (startFileIndex to endFileIndex)</li>
 *   <li>Generates metadata at regular intervals</li>
 *   <li>Thread-safe operation with proper lifecycle management</li>
 * </ul>
 * 
 * <p>Usage example:</p>
 * <pre>{@code
 * AudioFileMediaSourceConfiguration config = new AudioFileMediaSourceConfiguration.Builder()
 *     .fps(50)
 *     .dir("audio/")
 *     .filenameFormat("audio-%03d.pcm")
 *     .startFileIndex(1)
 *     .endFileIndex(100)
 *     .build();
 * 
 * try (AudioFrameSource source = new AudioFrameSource(config)) {
 *     source.onStreamDataAvailable(callback);
 *     source.start();
 *     // Audio frames will be generated and sent to callback
 * }
 * }</pre>
 * 
 * @see AudioFileMediaSourceConfiguration
 * @see KinesisVideoFrame
 */
public class AudioFrameSource implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(AudioFrameSource.class);

    public static final int METADATA_INTERVAL = 8;
    private static final long FRAME_DURATION_20_MS = 20L;
    private static final long SHUTDOWN_TIMEOUT_MS = 5000L;

    private final ExecutorService executor;
    private final int fps;
    private final AudioFileMediaSourceConfiguration configuration;
    private final int totalFiles;
    private final Object lock = new Object();
    final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicInteger frameCounter = new AtomicInteger(0);
    private final AtomicInteger metadataCount = new AtomicInteger(0);

    @GuardedBy("lock")
    private OnStreamDataAvailable streamDataCallback;

    /**
     * Constructs a new AudioFrameSource with the specified configuration.
     * 
     * @param configuration the audio file media source configuration containing
     *                     directory path, filename format, file range, and FPS settings
     * @throws IllegalArgumentException if configuration is null or contains invalid values
     */
    public AudioFrameSource(final AudioFileMediaSourceConfiguration configuration) {
        Preconditions.checkNotNull(configuration, "Configuration cannot be null");
        this.configuration = configuration;
        this.totalFiles = validateAndGetTotalFiles(configuration.getStartFileIndex(), configuration.getEndFileIndex());
        this.fps = validateFps(configuration.getFps());
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AudioFrameGenerator");
            t.setDaemon(true);
            return t;
        });
    }

    private int validateFps(int fps) {
        Preconditions.checkState(fps > 0, "FPS must be positive");
        return fps;
    }

    private int validateAndGetTotalFiles(final int startIndex, final int endIndex) {
        Preconditions.checkState(endIndex >= startIndex, "End index must be greater than or equal to start index");
        return endIndex - startIndex + 1;
    }

    /**
     * Starts the audio frame generation process.
     * 
     * <p>This method begins reading audio files and generating frames at the configured FPS.
     * Frames are delivered to the registered callback asynchronously.</p>
     * 
     * @throws KinesisVideoException if frame generation fails to start
     * @throws IllegalStateException if the source is already running
     */
    public void start() throws KinesisVideoException {
        if (!isRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("Audio frame source is already running");
        }
        startFrameGenerator();
    }

    /**
     * Stops the audio frame generation process.
     * 
     * <p>This method gracefully shuts down the frame generation thread and stops
     * delivering frames to the callback. It's safe to call this method multiple times.</p>
     */
    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            stopFrameGenerator();
        }
    }

    /**
     * Registers a callback to receive generated audio frames and metadata.
     * 
     * <p>The callback will be invoked for each generated frame and periodic metadata.
     * Only one callback can be registered at a time.</p>
     * 
     * @param callback the callback to receive frame data and metadata
     * @throws IllegalArgumentException if callback is null
     */
    public void onStreamDataAvailable(final OnStreamDataAvailable callback) {
        Preconditions.checkNotNull(callback, "Callback cannot be null");
        synchronized (lock) {
            this.streamDataCallback = callback;
        }
    }

    private void startFrameGenerator() {
        executor.execute(() -> {
            try {
                generateFrameAndNotifyListener();
            } catch (final Exception e) {
                log.error("Failed to generate audio frames", e);
                stop();
            }
        });
    }

    private void generateFrameAndNotifyListener() throws KinesisVideoException {
        while (isRunning.get()) {
            OnStreamDataAvailable callback;
            synchronized (lock) {
                callback = streamDataCallback;
            }

            if (callback != null) {
                KinesisVideoFrame frame = createKinesisVideoFrameFromAudio(frameCounter.get());
                if (frame != null) {
                    callback.onFrameDataAvailable(frame);
                    if (isMetadataReady()) {
                        String metadataName = "AudioLoop";
                        callback.onFragmentMetadataAvailable(
                                metadataName + metadataCount.get(),
                                String.valueOf(metadataCount.getAndIncrement()),
                                false
                        );
                    }
                }
            }

            frameCounter.incrementAndGet();
            try {
                Thread.sleep(Duration.ofSeconds(1L).toMillis() / fps);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Frame generation interrupted", e);
                break;
            }
        }
    }

    private boolean isMetadataReady() {
        return frameCounter.get() % METADATA_INTERVAL == 0;
    }

    private KinesisVideoFrame createKinesisVideoFrameFromAudio(final int index) {
        final String filename = String.format(
                configuration.getFilenameFormat(),
                index % totalFiles + configuration.getStartFileIndex());
        final Path path = Paths.get(configuration.getDir(), filename);
        final long currentTimeMs = System.currentTimeMillis();
        // Using similar construct to video for packaging it into a fragment
        final int flags = isKeyFrame() ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE;

        try {
            byte[] data = Files.readAllBytes(path);
            return new KinesisVideoFrame(
                    index,
                    flags,
                    currentTimeMs * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                    currentTimeMs * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                    FRAME_DURATION_20_MS * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                    ByteBuffer.wrap(data));
        } catch (final IOException e) {
            log.error("Failed to read audio file: {}", path, e);
            return null;
        }
    }

    private boolean isKeyFrame() {
        return frameCounter.get() % fps == 0;
    }

    private void stopFrameGenerator() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    /**
     * Closes the audio frame source and releases all resources.
     * 
     * <p>This method stops frame generation and shuts down the executor service.
     * After calling this method, the AudioFrameSource cannot be reused.</p>
     */
    @Override
    public void close() {
        stop();
    }
}