package com.amazonaws.kinesisvideo.producer;

/**
 * Interface to the Kinesis Video Producer Storage Callbacks functionality.
 *
 * <p>These will be used to integrate with the device storage.</p>
 */
public interface StorageCallbacks
{
    /**
     * Reports storage overflow pressure. The {@code putFrame} will trigger this
     * if the client's configured {@link StorageInfo#getStorageSize()} is 95% full or higher,
     * and if the {@link StreamInfo#getStreamingType()} is not
     * {@link com.amazonaws.kinesisvideo.producer.StreamInfo.StreamingType#STREAMING_TYPE_OFFLINE}.
     */
    void storageOverflowPressure(final long remainingSizeBytes);
}
