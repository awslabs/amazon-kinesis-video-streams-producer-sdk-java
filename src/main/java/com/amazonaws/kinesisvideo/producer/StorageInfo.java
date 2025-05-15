package com.amazonaws.kinesisvideo.producer;

import javax.annotation.Nonnull;

/**
 * Storage info class.
 *
 * NOTE: This structure should follow the one defined in /client/Include.h
 */
public class StorageInfo {
    /**
     * Current version for the structure as defined in the native code
     */
    public static final int STORAGE_INFO_CURRENT_VERSION = 0;

    public enum DeviceStorageType {
        /**
         * In-memory storage type
         */
        DEVICE_STORAGE_TYPE_IN_MEM(0),

        /**
         * File based storage type
         */
        DEVICE_STORAGE_TYPE_HYBRID_FILE(1);

        private int value;

        private DeviceStorageType(int i) {
            this.value = i;
        }

        public int getIntValue() {
            return this.value;
        }
    }

    private final int mVersion;
    private final DeviceStorageType mDeviceStorageType;
    private final long mStorageSize;
    private final int mSpillRatio;
    private final String mRootDirectory;

    public StorageInfo(int version, DeviceStorageType deviceStorageType, long storageSize, int spillRatio,
            @Nonnull String rootDirectory) {
        mVersion = version;
        mDeviceStorageType = deviceStorageType;
        mStorageSize = storageSize;
        mSpillRatio = spillRatio;
        mRootDirectory = rootDirectory;
    }

    public int getVersion() {
        return mVersion;
    }

    public int getDeviceStorageType() {
        return mDeviceStorageType.getIntValue();
    }

    public long getStorageSize() {
        return mStorageSize;
    }

    public int getSpillRatio() {
        return mSpillRatio;
    }

    @Nonnull
    public String getRootDirectory() {
        return mRootDirectory;
    }
}
