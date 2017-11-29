package com.amazonaws.kinesisvideo.java.mediasource.file;

import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.mediasource.OnFrameDataAvailable;
import com.amazonaws.kinesisvideo.stream.throttling.DiscreteTimePeriodsThrottler;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Frame source backed by local image files.
 */
@NotThreadSafe
public class ImageFrameSource {
    public static final int DISCRETENESS_HZ = 25;
    private final ExecutorService executor = Executors.newFixedThreadPool(1);
    private final DiscreteTimePeriodsThrottler throttler;
    private final ImageFileMediaSourceConfiguration configuration;

    private final int totalFiles;
    private OnFrameDataAvailable onFrameDataAvailable;
    private boolean isRunning = false;
    private long frameCounter;

    public ImageFrameSource(final ImageFileMediaSourceConfiguration configuration) {
        this.configuration = configuration;
        this.totalFiles = getTotalFiles(configuration.getStartFileIndex(), configuration.getEndFileIndex());
        this.throttler = new DiscreteTimePeriodsThrottler(configuration.getFps(), DISCRETENESS_HZ);
    }

    private int getTotalFiles(final int startIndex, final int endIndex) {
        Preconditions.checkState(endIndex >= startIndex);
        return endIndex - startIndex + 1;
    }

    public void start() {
        if (isRunning) {
            throw new IllegalStateException("Frame source is already running");
        }

        isRunning = true;
        startFrameGenerator();
    }

    public void stop() {
        isRunning = false;
        stopFrameGenerator();
    }

    public void onBytesAvailable(final OnFrameDataAvailable onFrameDataAvailable) {
        this.onFrameDataAvailable = onFrameDataAvailable;
    }

    private void startFrameGenerator() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                generateFrameAndNotifyListener();
            }
        });
    }

    private void generateFrameAndNotifyListener() {
        while (isRunning) {
            // TODO: Throttler is not limiting first time call when input param
            // are the same
            throttler.throttle();
            if (onFrameDataAvailable != null) {
                onFrameDataAvailable.onFrameDataAvailable(createKinesisVideoFrameFromImage(frameCounter));
            }

            frameCounter++;
        }
    }

    private ByteBuffer createKinesisVideoFrameFromImage(final long index) {
        final String filename = String.format(
                configuration.getFilenameFormat(),
                index % totalFiles + configuration.getStartFileIndex());
        final Path path = Paths.get(configuration.getDir() + filename);

        try {
            final byte[] bytes = Files.readAllBytes(path);
            return ByteBuffer.wrap(bytes);
        } catch (final IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private void stopFrameGenerator() {

    }
}
