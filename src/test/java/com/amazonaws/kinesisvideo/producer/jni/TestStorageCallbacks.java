package com.amazonaws.kinesisvideo.producer.jni;

import com.amazonaws.kinesisvideo.common.logging.Log;
import com.amazonaws.kinesisvideo.producer.StorageCallbacks;

import javax.annotation.Nonnull;

public class TestStorageCallbacks implements StorageCallbacks {
    private final Log mLog;

    public TestStorageCallbacks(final @Nonnull Log log) {
        mLog = log;
    }

    @Override
    public void storageOverflowPressure(long remainingSize) {

        mLog.verbose("Called storageOverflowPressure");
    }
}