package com.amazonaws.kinesisvideo.common.logging;

import javax.annotation.Nonnull;

/**
 * Interface representing the actual output channel which is platform dependent.
 *
 */
public interface OutputChannel {
    /**
     * Prints out an already formatted string to the actual messaging bus
     *
     * @param level
     *         Log level
     * @param tag
     *         Tag to be used with the message
     * @param message
     *         The actual message to log
     */
    void print(int level, final @Nonnull String tag, final @Nonnull String message);
}