package com.amazonaws.kinesisvideo.producer;

/**
 * Definition of the Kinesis Video Fragment ACK type.
 *
 * NOTE: This structure must be the same as defined in /client/Include.h
 *
 * @see <a href="https://docs.aws.amazon.com/kinesisvideostreams/latest/dg/API_dataplane_PutMedia.html">PutMedia API</a>
 */
public class FragmentAckType {
    /**
     * Ack type undefined or not specified. Used as a sentinel
     */
    public static final int FRAGMENT_ACK_TYPE_UNDEFINED = 0;

    /**
     * Fragment started buffering on the ingestion host
     */
    public static final int FRAGMENT_ACK_TYPE_BUFFERING = 1;

    /**
     * Fragment has been received and parsed
     */
    public static final int FRAGMENT_ACK_TYPE_RECEIVED = 2;

    /**
     * Fragment has been persisted
     */
    public static final int FRAGMENT_ACK_TYPE_PERSISTED = 3;

    /**
     * Fragment errored
     */
    public static final int FRAGMENT_ACK_TYPE_ERROR = 4;

    /**
     * Idle ACK to keep alive
     */
    public static final int FRAGMENT_ACK_TYPE_IDLE = 5;

    private final int mType;

    public FragmentAckType(int type) {
        mType = type;
    }

    /**
     * Returns the type as an integer which can be consumed by the native layer
     */
    public int getIntType() {
        return mType;
    }

    /**
     * @return a string representation of the acknowledgement type
     * @see <a href="https://docs.aws.amazon.com/kinesisvideostreams/latest/dg/API_dataplane_PutMedia.html">PutMedia API</a>
     */
    @Override
    public String toString() {
        switch (mType) {
            case FRAGMENT_ACK_TYPE_BUFFERING:
                return "BUFFERING";
            case FRAGMENT_ACK_TYPE_RECEIVED:
                return "RECEIVED";
            case FRAGMENT_ACK_TYPE_PERSISTED:
                return "PERSISTED";
            case FRAGMENT_ACK_TYPE_ERROR:
                return "ERROR";
            case FRAGMENT_ACK_TYPE_IDLE:
                return "IDLE";
            default:
                return "UNDEFINED";
        }
    }
}