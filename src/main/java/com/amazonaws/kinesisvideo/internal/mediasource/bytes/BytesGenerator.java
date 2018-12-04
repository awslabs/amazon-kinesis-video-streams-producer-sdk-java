package com.amazonaws.kinesisvideo.internal.mediasource.bytes;

import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.internal.mediasource.OnStreamDataAvailable;
import com.amazonaws.kinesisvideo.stream.throttling.DiscreteTimePeriodsThrottler;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class BytesGenerator {
    private static final int DISCRETENESS_10HZ = 10;
    private static final int MAX_FRAME_SIZE_BYTES_1024 = 1024;

    private OnStreamDataAvailable streamDataAvailable;

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

    private final Log log = LogFactory.getLog(BytesGenerator.class);
    private volatile boolean isRunning;
    private int frameCounter;

    public BytesGenerator(final int fps) {
        frameCounter = 0;
        throttler = new DiscreteTimePeriodsThrottler(fps, DISCRETENESS_10HZ);
    }

    public void onStreamDataAvailable(final OnStreamDataAvailable streamDataAvailable) {
        this.streamDataAvailable = streamDataAvailable;
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
                try {
                    generateBytesAndNotifyListener();
                } catch (final KinesisVideoException e) {
                    log.error("Failed to keep generating frames with Exception", e);
                }
            }
        });
    }

    private void generateBytesAndNotifyListener() throws KinesisVideoException {
        while (isRunning) {
            fillArrayWithDigitsOfFramesCounter();

            if (streamDataAvailable != null) {
                streamDataAvailable
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
