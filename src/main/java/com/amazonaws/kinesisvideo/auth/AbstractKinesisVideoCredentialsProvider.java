package com.amazonaws.kinesisvideo.auth;

import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;

/**
 * Interface to mimic the credentials provider in AWS SDKs.
 */
public abstract class AbstractKinesisVideoCredentialsProvider implements KinesisVideoCredentialsProvider {
    private KinesisVideoCredentials credentials;
    private Object syncObj;

    protected AbstractKinesisVideoCredentialsProvider() {
        credentials = null;
        syncObj = new Object();
    }

    @Override
    public KinesisVideoCredentials getCredentials() throws KinesisVideoException {
        synchronized (syncObj) {
            refreshCredentials(false);
            return credentials;
        }
    }

    @Override
    public KinesisVideoCredentials getUpdatedCredentials() throws KinesisVideoException {
        synchronized (syncObj) {
            refreshCredentials(true);
            return credentials;
        }
    }

    private void refreshCredentials(boolean forceUpdate) throws KinesisVideoException {
        final long currentMillis = System.currentTimeMillis();
        if (null == credentials
                || forceUpdate
                || currentMillis <= credentials.getExpiration().getTime()) {
            // Force update the credentials which in derived classes will actually retrieve new credentials.
            credentials = updateCredentials();
        }
    }

    protected abstract KinesisVideoCredentials updateCredentials() throws KinesisVideoException;
}