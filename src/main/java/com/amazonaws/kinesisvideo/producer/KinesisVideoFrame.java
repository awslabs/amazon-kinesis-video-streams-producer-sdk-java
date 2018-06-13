package com.amazonaws.kinesisvideo.producer;

import static java.util.Objects.requireNonNull;

import java.nio.ByteBuffer;

import javax.annotation.Nonnull;

import com.google.common.base.MoreObjects;

/**
 * Kinesis Video frame representation.
 *
 * NOTE: This class must match the Frame declaration in native code in
 * /mkvgen/Include.h
 */

public class KinesisVideoFrame {
    /**
     * Index of the frame
     */
    private final int mIndex;

    /**
     * Frame flags
     */
    private final int mFlags;

    /**
     * The decoding timestamp of the frame in 100ns precision
     */
    private final long mDecodingTs;

    /**
     * The presentation timestamp of the frame in 100ns precision
     */
    private final long mPresentationTs;

    /**
     * The duration of the frame in 100ns precision
     */
    private final long mDuration;

    /**
     * The actual frame data
     */
    private final ByteBuffer mData;

    public KinesisVideoFrame(int index, int flags, long decodingTs, long presentationTs, long duration,
            @Nonnull ByteBuffer data) {
        mIndex = index;
        mFlags = flags;
        mDecodingTs = decodingTs;
        mPresentationTs = presentationTs;
        mDuration = duration;
        mData = requireNonNull(data);
    }

    public int getIndex() {
        return mIndex;
    }

    public int getFlags() {
        return mFlags;
    }

    public long getDecodingTs() {
        return mDecodingTs;
    }

    public long getPresentationTs() {
        return mPresentationTs;
    }

    public long getDuration() {
        return mDuration;
    }

    public int getSize() {
        return mData.remaining();
    }

    @Nonnull
    public ByteBuffer getData() {
        ByteBuffer byteBuffer = mData;
        try {
            if (mData.hasArray()) {
                byteBuffer = ByteBuffer.allocateDirect(mData.remaining());
                byteBuffer.put(mData);
            }
        } catch(final Exception e) {
            // Some Android implementations throw when accessing hasArray() API. We will ignore it
        }

        return byteBuffer;
    }

    @Override public String toString() {
        return MoreObjects.toStringHelper(this).add("mIndex", mIndex).add("mFlags", mFlags).add("mDecodingTs", mDecodingTs).add("mPresentationTs", mPresentationTs).add("mDuration", mDuration)
                .add("mData", mData).toString();
    }
}
