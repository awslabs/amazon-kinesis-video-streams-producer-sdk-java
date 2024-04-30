package com.amazonaws.kinesisvideo.producer;

public enum AutomaticStreamingFlags {
    AUTOMATIC_STREAMING_INTERMITTENT_PRODUCER(0), AUTOMATIC_STREAMING_ALWAYS_CONTINUOUS(256);
    private final int streamingFlagValue;

    private AutomaticStreamingFlags(int streamingFlagValue) {
        this.streamingFlagValue = streamingFlagValue;
    }

    public int getStreamingFlagValue() {
        return streamingFlagValue;
    }
}
