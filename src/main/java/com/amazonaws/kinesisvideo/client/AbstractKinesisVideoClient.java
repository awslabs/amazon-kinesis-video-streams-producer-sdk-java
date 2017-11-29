package com.amazonaws.kinesisvideo.client;

import com.amazonaws.kinesisvideo.client.mediasource.MediaSource;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.common.logging.Log;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.producer.DeviceInfo;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;

import static com.amazonaws.kinesisvideo.common.preconditions.Preconditions.checkState;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract Kinesis Video Client implementation which handles some of the common pieces
 * and delegates platform specifics to the implementations.
 */
@NotThreadSafe
public abstract class AbstractKinesisVideoClient implements KinesisVideoClient {

    /**
     * Stores the list of streams
     */
    private final List<MediaSource> mMediaSources = new ArrayList<MediaSource>();

    /**
     * Whether the object has been initialized
     */
    protected boolean mIsInitialized = false;

    /**
     * Logging through this object
     */
    protected final Log mLog;

    public AbstractKinesisVideoClient(@Nonnull final Log log) {
        mLog = Preconditions.checkNotNull(log);
    }

    /**
     * Returns whether the client has been initialized
     */
    @Override
    public boolean isInitialized() {
        return mIsInitialized;
    }

    /**
     * Initializes the client object.
     */
    @Override
    public void initialize(@Nonnull final DeviceInfo deviceInfo) throws KinesisVideoException
    {
        mLog.info("Initializing Kinesis Video client");

        // Make sure we are not yet initialized
        checkState(!mIsInitialized, "Already initialized");

        // The actual initialization happens in the derived classes.
        mIsInitialized = true;
    }

    /**
     * Resumes the processing
     */
    @Override
    public void startAllMediaSources() throws KinesisVideoException {
        mLog.verbose("Resuming Kinesis Video client");

        checkState(isInitialized(), "Must initialize first.");
        for (final MediaSource mediaSource : mMediaSources) {
            mediaSource.start();
        }
    }

    /**
     * Pauses the processing
     */
    @Override
    public void stopAllMediaSources() throws KinesisVideoException {
        mLog.verbose("Pausing Kinesis Video client");

        if (!isInitialized()) {
            // Idempotent call
            return;
        }

        for (final MediaSource mediaSource : mMediaSources) {
            mediaSource.stop();
        }
    }

    /**
     * Stops the streams and frees/releases the underlying object
     */
    @Override
    public void free() throws KinesisVideoException {
        mLog.verbose("Releasing Kinesis Video client");

        if (!isInitialized()) {
            // Idempotent call
            return;
        }

        for (final MediaSource mediaSource : mMediaSources) {
            if (!mediaSource.isStopped()) {
                mediaSource.stop();
            }

            mediaSource.free();
        }

        // Clean the list
        mMediaSources.clear();
    }

    /**
     * Adds an already created {@link MediaSource} to the list.
     */
    @Override
    public void registerMediaSource(@Nonnull final String streamName,
                                    @Nonnull final MediaSource mediaSource) throws KinesisVideoException {
        // The actual media source creation happens in the derived class
        mMediaSources.add(mediaSource);
    }
}
