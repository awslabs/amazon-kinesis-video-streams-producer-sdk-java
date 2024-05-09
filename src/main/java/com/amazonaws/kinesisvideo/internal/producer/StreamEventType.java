package com.amazonaws.kinesisvideo.internal.producer;


public enum StreamEventType {

    // KVS Stream events (bit flags)
    //
    STREAM_EVENT_TYPE_NONE(0),
    //
    STREAM_EVENT_TYPE_IMAGE_GENERATION(1 << 0),
    //
    STREAM_EVENT_TYPE_NOTIFICATION(1 << 1),
    //
    // used to iterative purposes, always keep last.
    STREAM_EVENT_TYPE_LAST(1 << 2);


    private final int mType;

    StreamEventType(int type) {
        mType = type;
    }

    public int getIntType() {
        return mType;
    }
}
