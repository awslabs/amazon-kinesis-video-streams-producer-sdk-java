package com.amazonaws.kinesisvideo.producer;

import com.amazonaws.kinesisvideo.producer.StorageInfo;
import com.amazonaws.kinesisvideo.producer.Tag;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;

/**
 * Device information object.
 *
 * NOTE: This should follow the structure defined in /client/Include.h
 *
 * NOTE: Suppressing Findbug error as this code will be accessed from native codebase.
 */
@SuppressFBWarnings("EI_EXPOSE_REP")
public class DeviceInfo {
    /**
     * Current version for the structure as defined in the native code
     */
    public static final int DEVICE_INFO_CURRENT_VERSION = 0;

    private final int mVersion;
    private final String mName;
    private final StorageInfo mStorageInfo;
    private final int mStreamCount;
    private final Tag[] mTags;

    public DeviceInfo(int version, @Nullable final String name, @Nonnull final StorageInfo storageInfo,
            int streamCount, @Nullable final Tag[] tags) {
        mStorageInfo = Preconditions.checkNotNull(storageInfo);
        mName = name;
        mTags = tags;
        mVersion = version;
        mStreamCount = streamCount;
    }

    public int getVersion() {
        return mVersion;
    }

    public String getName() {
        return mName;
    }

    @Nonnull
    public StorageInfo getStorageInfo() {
        return mStorageInfo;
    }

    public int getStreamCount() {
        return mStreamCount;
    }

    public int getStorageInfoVersion() {
        return mStorageInfo.getVersion();
    }

    public int getDeviceStorageType() {
        return mStorageInfo.getDeviceStorageType();
    }

    public long getStorageSize() {
        return mStorageInfo.getStorageSize();
    }

    public int getSpillRatio() {
        return mStorageInfo.getSpillRatio();
    }

    @Nullable
    public String getRootDirectory() {
        return mStorageInfo.getRootDirectory();
    }

    @Nullable
    public Tag[] getTags() {
        return mTags;
    }
}
