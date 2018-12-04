package com.amazonaws.kinesisvideo.stream.throttling;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Measures bytes per second, notifies the consumer callback about each measurement
 */
public class BandwidthMeasuringOutputStream extends OutputStream {

    private final OutputStream mOutputStream;
    private final OpsPerSecondMeasurer mOpsPerSecondMeasurer;

    public BandwidthMeasuringOutputStream(final OutputStream outputStream,
            final OpsPerSecondMeasurer opsPerSecondMeasurer) {
        this.mOutputStream = outputStream;
        this.mOpsPerSecondMeasurer = opsPerSecondMeasurer;
    }

    @Override
    public void write(final int b) throws IOException {
        mOpsPerSecondMeasurer.recordOperation();
        mOutputStream.write(b);
    }

}
