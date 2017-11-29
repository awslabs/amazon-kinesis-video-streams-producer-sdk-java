package com.amazonaws.kinesisvideo.service.exception;

import javax.annotation.Nonnull;

public class ResourceInUseException extends RuntimeException {
    public ResourceInUseException(@Nonnull final String message) {
        super(message);
    }
}
