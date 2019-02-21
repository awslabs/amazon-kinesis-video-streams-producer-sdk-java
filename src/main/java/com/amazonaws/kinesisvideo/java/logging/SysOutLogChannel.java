package com.amazonaws.kinesisvideo.java.logging;

import javax.annotation.Nonnull;

import com.amazonaws.kinesisvideo.common.logging.LogLevel;
import com.amazonaws.kinesisvideo.common.logging.OutputChannel;

/**
 * Log output channel which uses System.out.println()
 */
public class SysOutLogChannel implements OutputChannel {
    private static final String LOG_FORMAT = "%s / %s: %s";
    @Override
    public void print(final int level,
                      @Nonnull final String tag,
                      @Nonnull final String message) {
        System.out.println(String.format(LOG_FORMAT, getLevel(level), tag, message));
    }

    private String getLevel(final int level) {
        return LogLevel.fromInt(level).name();
    }
}
