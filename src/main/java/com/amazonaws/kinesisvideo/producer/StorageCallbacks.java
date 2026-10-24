package com.amazonaws.kinesisvideo.producer;

/**
 * Interface to the Kinesis Video Producer Storage Callbacks functionality.
 *
 * <p>These will be used to integrate with the device storage.</p>
 *
 * <p>The producer calls these on its callback thread, one per client, in the order the events happened, after the native
 * call that raised them has returned and with no SDK or PIC lock held. Implementations may call any producer API from
 * them, including stop and free; a slow callback delays only the client's later callbacks.</p>
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
