package com.amazonaws.kinesisvideo.producer;

/**
 * Java model for native code in PIC.
 * <p>
 * In some streaming scenarios video is not constantly being produced,
 * in this case special handling must take place to handle various streaming
 * scenarios.
 *
 * @see <a href="https://github.com/awslabs/amazon-kinesis-video-streams-pic/blob/master/src/client/include/com/amazonaws/kinesis/video/client/Include.h">PIC</a>
 */
public enum AutomaticStreamingFlags {
    /**
     * With this option we'll create a timer (burns a thread) and periodically check
     * if there are any streams which haven't had any PutFrame calls
     * over fixed period of time, in which case we'll close out the fragment
     * to prevent back-end from timing out and closing the session
     */
    AUTOMATIC_STREAMING_INTERMITTENT_PRODUCER(0),

    /**
     * This option indicates a desire to do continuous recording with no gaps
     * this doesn't mean we can't have dropped packets, this mode should NOT
     * be used if for example only motion or event based video is to be recorded
     */
    AUTOMATIC_STREAMING_ALWAYS_CONTINUOUS((1 << 8));

    private final int streamingFlagValue;

    AutomaticStreamingFlags(final int streamingFlagValue) {
        this.streamingFlagValue = streamingFlagValue;
    }

    public int getStreamingFlagValue() {
        return streamingFlagValue;
    }
}
