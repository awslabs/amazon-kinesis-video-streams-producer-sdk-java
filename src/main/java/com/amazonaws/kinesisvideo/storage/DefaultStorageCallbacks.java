package com.amazonaws.kinesisvideo.storage;

import com.amazonaws.kinesisvideo.producer.StorageCallbacks;

public class DefaultStorageCallbacks implements StorageCallbacks {
    @Override
    public void storageOverflowPressure(long remainingSize) {
        // TODO: Add actual storage overflow callback later
    }
}
