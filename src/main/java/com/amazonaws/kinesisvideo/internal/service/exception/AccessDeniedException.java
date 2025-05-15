package com.amazonaws.kinesisvideo.internal.service.exception;

import javax.annotation.Nonnull;

public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(@Nonnull final String message) {
        super(message);
    }
}
