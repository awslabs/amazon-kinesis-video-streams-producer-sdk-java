package com.amazonaws.kinesisvideo.mediasource.bytes;

import com.amazonaws.kinesisvideo.mediasource.OnFrameDataAvailable;
import com.amazonaws.kinesisvideo.stream.throttling.DiscreteTimePeriodsThrottler;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BytesGenerator {
    private static final int DISCRETENESS_10HZ = 10;
    private static final int MAX_FRAME_SIZE_BYTES_1024 = 1024;

    private OnFrameDataAvailable onFrameDataAvailable;

    private final ExecutorService executor = Executors.newFixedThreadPool(1);
    private final DiscreteTimePeriodsThrottler throttler;
    private final byte[][] framesData = new byte[][]{
            new byte[MAX_FRAME_SIZE_BYTES_1024],
            new byte[MAX_FRAME_SIZE_BYTES_1024],
            new byte[MAX_FRAME_SIZE_BYTES_1024],
            new byte[MAX_FRAME_SIZE_BYTES_1024],
            new byte[MAX_FRAME_SIZE_BYTES_1024],
            new byte[MAX_FRAME_SIZE_BYTES_1024]
    };

    private volatile boolean isRunning;
    private int frameCounter;

    public BytesGenerator(final int fps) {
        frameCounter = 0;
        throttler = new DiscreteTimePeriodsThrottler(fps, DISCRETENESS_10HZ);
    }

    public void onFrameDataAvailable(final OnFrameDataAvailable onFrameDataAvailable) {
        this.onFrameDataAvailable = onFrameDataAvailable;
    }

    public synchronized void start() {
        if (isRunning) {
            throw new IllegalStateException("should stop previous generator before starting the new one");
        }

        isRunning = true;

        startGeneratorInBackground();
    }

    public synchronized void stop() {
        isRunning = false;
    }

    private void startGeneratorInBackground() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                generateBytesAndNotifyListener();
            }
        });
    }

    private void generateBytesAndNotifyListener() {
        while (isRunning) {
            fillArrayWithDigitsOfFramesCounter();

            if (onFrameDataAvailable != null) {
                onFrameDataAvailable
                        .onFrameDataAvailable(ByteBuffer.wrap(framesData[frameCounter % framesData.length]));
            }

            frameCounter++;

            throttler.throttle();
        }
    }

    private void fillArrayWithDigitsOfFramesCounter() {
        final String counterString = String.valueOf(frameCounter) + "|";
        final byte[] counterBytes = counterString.getBytes(StandardCharsets.US_ASCII);
        final byte[] frameData = this.framesData[frameCounter % this.framesData.length];

        for (int i = 0; i < frameData.length; i++) {
            frameData[i] = counterBytes[i % counterBytes.length];
        }
    }
}
