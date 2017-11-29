package com.amazonaws.kinesisvideo.service.exception;

import javax.annotation.Nonnull;

public class AmazonServiceException extends RuntimeException {
    public AmazonServiceException(@Nonnull final String message) {
        super(message);
    }
}
