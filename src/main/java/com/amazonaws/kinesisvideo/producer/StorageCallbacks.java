package com.amazonaws.kinesisvideo.producer;

/**
 *
 * Interface to the Kinesis Video Producer Storage Callbacks functionality.
 *
 * These will be used to integrate with the the device storage.
 */
public interface StorageCallbacks
{
    /**
     * Reports storage overflow pressure
     */
    void storageOverflowPressure(long remainingSize);
}
