package com.amazonaws.kinesisvideo.producer;

import com.amazonaws.kinesisvideo.producer.FragmentAckType;

import javax.annotation.Nonnull;

/**
 * KinesisVideo fragment ack representation.
 *
 * NOTE: This class must match the Frame declaration in native code in
 * /client/Include.h
 *
 */
public class KinesisVideoFragmentAck {
    /**
     * The current version
     */
    private final static int FRAGMENT_ACK_CURRENT_VERSION = 0;

    /**
     * Fragment ACK type
     */
    private final FragmentAckType mAckType;

    /**
     * Fragment ACK timestamp in 100ns precision
     */
    private final long mTimestamp;

    /**
     * The sequence number for the fragment
     */
    private final String mSequenceNumber;

    /**
     * The service call result for the ACK in case of an error.
     */
    private final int mResult;

    public KinesisVideoFragmentAck(int ackType,
                                   long timestamp,
                                   @Nonnull final String sequenceNumber,
                                   int result) {
        this(new FragmentAckType(ackType), timestamp, sequenceNumber, result);
    }

    public KinesisVideoFragmentAck(@Nonnull final FragmentAckType ackType,
                                   long timestamp,
                                   @Nonnull final String sequenceNumber,
                                   int result) {
        mAckType = ackType;
        mTimestamp = timestamp;
        mSequenceNumber = sequenceNumber;
        mResult = result;
    }

    public int getVersion() {
        return FRAGMENT_ACK_CURRENT_VERSION;
    }

    @Nonnull
    public FragmentAckType getAckType() {
        return mAckType;
    }

    public long getTimestamp() {
        return mTimestamp;
    }

    @Nonnull
    public String getSequenceNumber() {
        return mSequenceNumber;
    }

    public int getResult() {
        return mResult;
    }
}
