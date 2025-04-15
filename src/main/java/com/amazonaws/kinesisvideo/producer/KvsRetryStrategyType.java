package com.amazonaws.kinesisvideo.producer;

/**
 * Java model for native code in PIC.
 * <p>
 * Retry configuration type for {@link KvsRetryStrategy} in {@link KvsRetryStrategyCallbacks}.
 *
 * @see <a href="https://github.com/awslabs/amazon-kinesis-video-streams-pic/blob/master/src/utils/include/com/amazonaws/kinesis/video/utils/Include.h">PIC</a>
 */
public enum KvsRetryStrategyType {  
    DISABLED(0),
    EXPONENTIAL_BACKOFF_WAIT(1);

    private final int kvsRetryStrategyTypeValue;

    KvsRetryStrategyType(final int kvsRetryStrategyTypeValue) {
        this.kvsRetryStrategyTypeValue = kvsRetryStrategyTypeValue;
    }

    public int getKvsRetryStrategyType() {
        return kvsRetryStrategyTypeValue;
    }
}
