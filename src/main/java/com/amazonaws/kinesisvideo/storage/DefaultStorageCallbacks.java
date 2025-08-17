package com.amazonaws.kinesisvideo.storage;

import com.amazonaws.kinesisvideo.producer.StorageCallbacks;

/**
 * No-op implementation of storage callbacks. Extending this class allows convenient
 * implementation of certain callbacks while leaving the rest as no-op.
 *
 * <p>For example:</p>
 *
 * <pre>
 *StorageCallbacks storageCallbacks =
 *    new DefaultStorageCallbacks() {
 *        &#64;Override
 *        public void storageOverflowPressure(final long remainingSizeBytes) {
 *            super.storageOverflowPressure(remainingSize);
 *
 *            final long remainingSizeInMB = remainingSizeBytes / 1024L / 1024L;
 *
 *            log.warn("The buffer is approaching capacity: {} MB remaining", remainingSizeInMB);
 *        }
 *    };
 *</pre>
 */
public class DefaultStorageCallbacks implements StorageCallbacks {
    @Override
    public void storageOverflowPressure(final long remainingSizeBytes) {
        // No-op
    }
}
