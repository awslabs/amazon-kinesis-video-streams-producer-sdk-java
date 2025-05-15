package com.amazonaws.kinesisvideo.client.mediasource;

/**
 * Represents the media source states.
 *
 * NOTE: Simple states for the media source. Initially, the media source is in the Initialized state.
 *
 * Later, if we need, we could add more state transitions to mimic lower-level encoders/hardware states for
 * more granularity if we need.
 *
 * Initialized - Ready (allocate the buffers and configure the source)
 * Ready - Running (start streaming)
 * Running - Ready (pause stream. Doesn't de-allocate the buffers)
 * Running - Stopped (stop the stream)
 * Ready - Stopped (stop the stream)
 * Stopped - NULL (need to re-initialize)
 *
 *
 */
public enum MediaSourceState {
    /**
     * Created/initialized
     */
    INITIALIZED,

    /**
     * Ready state. The buffers are allocated and configured.
     */
    READY,

    /**
     * Running state.
     */
    RUNNING,

    /**
     * Stopped state. Not initialized.
     */
    STOPPED
}
