package com.amazonaws.kinesisvideo.stream.throttling;


import com.amazonaws.kinesisvideo.common.function.Consumer;

/**
 * Measures ops per second, notifies the consumer callback about the measurement
 */
public class OpsPerSecondMeasurer {
    private static final int MS_IN_SEC = 1000;
    private final Consumer<Long> mCallback;

    private int mOpsSinceLastMeasurement = 0;
    private long mLastMeasurementTimeMs = 0;

    public OpsPerSecondMeasurer(final Consumer<Long> callback) {
        mCallback = callback;
    }

    public void recordOperation() {
        ensureInitialized();

        mOpsSinceLastMeasurement++;

        tryRecordMeasurementAndReset();
    }

    private void tryRecordMeasurementAndReset() {
        final long elapsedTimeMs = System.currentTimeMillis() - mLastMeasurementTimeMs;
        if (elapsedTimeMs > MS_IN_SEC) {
            mCallback.accept(getOpsPerSecond(elapsedTimeMs));
            reset();
        }
    }

    private long getOpsPerSecond(final long elapsedTimeMs) {
        return (mOpsSinceLastMeasurement * MS_IN_SEC) / elapsedTimeMs;
    }

    private void reset() {
        mOpsSinceLastMeasurement = 0;
        mLastMeasurementTimeMs = System.currentTimeMillis();
    }

    private void ensureInitialized() {
        if (mLastMeasurementTimeMs == 0) {
            mLastMeasurementTimeMs = System.currentTimeMillis();
        }
    }
}
