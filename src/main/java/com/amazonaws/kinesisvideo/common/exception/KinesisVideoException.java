package com.amazonaws.kinesisvideo.common.exception;

import javax.annotation.Nonnull;

/**
 *
 * Kinesis Video Streams common codebase exception class.
 *
 * The Kinesis Video Streams exceptions will derive from this base class.
 *
 */
public class KinesisVideoException extends Exception {
    public KinesisVideoException() {
        super();
    }

    public KinesisVideoException(@Nonnull final String message) {
        super(message);
    }

    public KinesisVideoException(@Nonnull final String message, @Nonnull final Throwable cause) {
        super(message, cause);
    }

    public KinesisVideoException(@Nonnull final Throwable cause) {
        super(cause);
    }

}
