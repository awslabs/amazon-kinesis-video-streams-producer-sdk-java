package com.amazonaws.kinesisvideo.producer;

import com.amazonaws.kinesisvideo.internal.producer.jni.NativeKinesisVideoProducerJni;
import com.amazonaws.kinesisvideo.util.CalledByNativeCode;
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
    public static final int DEVICE_INFO_CURRENT_VERSION = 1;

    private final int mVersion;
    private final String mName;
    private final StorageInfo mStorageInfo;
    private final int mStreamCount;
    private final Tag[] mTags;
    private final String mClientId;
    private final ClientInfo mClientInfo;

    public DeviceInfo(int version, @Nullable final String name, @Nonnull final StorageInfo storageInfo,
            int streamCount, @Nullable final Tag[] tags) {
        this(version, name, storageInfo, streamCount, tags,
                "JNI " + NativeKinesisVideoProducerJni.EXPECTED_LIBRARY_VERSION, new ClientInfo());
    }

    public DeviceInfo(int version, @Nullable final String name, @Nonnull final StorageInfo storageInfo,
                      int streamCount, @Nullable final Tag[] tags, @Nonnull final String clientId,
                      @Nonnull final ClientInfo clientInfo) {
        mStorageInfo = Preconditions.checkNotNull(storageInfo);
        mName = name;
        mTags = tags;
        mVersion = version;
        mStreamCount = streamCount;
        mClientId = clientId;
        mClientInfo = clientInfo;
    }

    @CalledByNativeCode
    public int getVersion() {
        return mVersion;
    }

    @CalledByNativeCode
    public String getName() {
        return mName;
    }

    @Nonnull
    public StorageInfo getStorageInfo() {
        return mStorageInfo;
    }

    @CalledByNativeCode
    public int getStreamCount() {
        return mStreamCount;
    }

    @CalledByNativeCode
    public int getStorageInfoVersion() {
        return mStorageInfo.getVersion();
    }

    @CalledByNativeCode
    public int getDeviceStorageType() {
        return mStorageInfo.getDeviceStorageType();
    }

    @CalledByNativeCode
    public long getStorageSize() {
        return mStorageInfo.getStorageSize();
    }

    @CalledByNativeCode
    public int getSpillRatio() {
        return mStorageInfo.getSpillRatio();
    }

    @Nullable
    @CalledByNativeCode
    public String getRootDirectory() {
        return mStorageInfo.getRootDirectory();
    }

    @Nullable
    @CalledByNativeCode
    public Tag[] getTags() {
        return mTags;
    }

    @Nonnull
    @CalledByNativeCode
    public String getClientId() {
        return mClientId;
    }

    @Nonnull
    @CalledByNativeCode
    public ClientInfo getClientInfo() {
        return mClientInfo;
    }
}
