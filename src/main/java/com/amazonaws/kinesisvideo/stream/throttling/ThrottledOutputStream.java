package com.amazonaws.kinesisvideo.stream.throttling;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Calls Throttler class each write operation
 * so that Throttler measures and enforces the bandwidth it was configured to enforce
 */
public class ThrottledOutputStream extends OutputStream {
    private final OutputStream mUnthrottledOutputStream;
    private final Throttler mThrottler;

    public ThrottledOutputStream(final OutputStream unthrottledOutputStream,
                                 final Throttler throttler) {
        mUnthrottledOutputStream = unthrottledOutputStream;
        mThrottler = throttler;
    }

    @Override
    public void write(final int b) throws IOException {
        mThrottler.throttle();
        mUnthrottledOutputStream.write(b);
    }
}
