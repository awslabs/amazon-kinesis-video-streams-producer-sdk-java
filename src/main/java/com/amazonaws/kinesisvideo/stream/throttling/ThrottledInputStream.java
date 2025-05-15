package com.amazonaws.kinesisvideo.stream.throttling;

import java.io.IOException;
import java.io.InputStream;

/**
 * Calls Throttler class each read operation
 * so that Throttler measures and enforces the bandwidth it was configured to enforce
 */
public class ThrottledInputStream extends InputStream {
    private static final int MS_IN_SEC = 1000;

    private final InputStream mUnthrottledInputStream;
    private final Throttler mThrottler;

    public ThrottledInputStream(final InputStream unthrottledInputStream,
                                final Throttler throttler) {
        mUnthrottledInputStream = unthrottledInputStream;
        mThrottler = throttler;
    }

    @Override
    public int read() throws IOException {
        mThrottler.throttle();
        return mUnthrottledInputStream.read();
    }
}
