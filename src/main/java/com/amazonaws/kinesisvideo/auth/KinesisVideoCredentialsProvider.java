package com.amazonaws.kinesisvideo.auth;

import javax.annotation.Nullable;

import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;

/**
 * Interface to mimic the credentials provider in AWS SDKs.
 */
public interface KinesisVideoCredentialsProvider {
    @Nullable
    KinesisVideoCredentials getCredentials() throws KinesisVideoException;

    @Nullable
    KinesisVideoCredentials getUpdatedCredentials() throws KinesisVideoException;
}