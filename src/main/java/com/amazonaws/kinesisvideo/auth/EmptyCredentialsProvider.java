package com.amazonaws.kinesisvideo.auth;

import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;

/**
 * Empty credentials provider
 */
public final class EmptyCredentialsProvider implements KinesisVideoCredentialsProvider{

    @Override
    public KinesisVideoCredentials getCredentials() throws KinesisVideoException{
        return KinesisVideoCredentials.EMPTY_KINESIS_VIDEO_CREDENTIALS;
    }

    @Override
    public KinesisVideoCredentials getUpdatedCredentials() throws KinesisVideoException{
        return KinesisVideoCredentials.EMPTY_KINESIS_VIDEO_CREDENTIALS;
    }
}