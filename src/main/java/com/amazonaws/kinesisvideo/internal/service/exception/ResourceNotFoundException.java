package com.amazonaws.kinesisvideo.internal.service.exception;

import javax.annotation.Nonnull;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(@Nonnull final String message) {
        super(message);
    }
}
