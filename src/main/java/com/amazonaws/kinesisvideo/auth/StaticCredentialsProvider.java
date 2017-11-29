package com.amazonaws.kinesisvideo.auth;

import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;

import javax.annotation.Nonnull;

/**
 * Empty credentials provider
 */
public final class StaticCredentialsProvider implements KinesisVideoCredentialsProvider {
    private final KinesisVideoCredentials credentials;

    public StaticCredentialsProvider(@Nonnull KinesisVideoCredentials credentials) {
        this.credentials = Preconditions.checkNotNull(credentials);
    }

    @Override
    public KinesisVideoCredentials getCredentials() throws KinesisVideoException {
        return credentials;
    }

    @Override
    public KinesisVideoCredentials getUpdatedCredentials() throws KinesisVideoException {
        return credentials;
    }
}