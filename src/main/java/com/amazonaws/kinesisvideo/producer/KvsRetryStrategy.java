package com.amazonaws.kinesisvideo.producer;

/**
 * Java model for native code in PIC.
 * <p>
 * A generic retry strategy
 *
 * @see <a href="https://github.com/awslabs/amazon-kinesis-video-streams-pic/blob/master/src/utils/include/com/amazonaws/kinesis/video/utils/Include.h">PIC</a>
 */
public class KvsRetryStrategy {

    // Pointer to metadata/state/details for the retry strategy.
    // The actual data type is abstracted and will be inferred by
    // the RetryHandlerFn
    private final long mRetryStrategy;

    // Optional configuration used to build the retry strategy. Once the retry strategy is created,
    // any changes to the config will be useless.
    private final long mRetryStrategyConfig;

    // Retry strategy type
    private final KvsRetryStrategyType mKvsRetryStrategyType;

    public KvsRetryStrategy() {
        mRetryStrategy = 0;
        mRetryStrategyConfig = 0;
        mKvsRetryStrategyType = KvsRetryStrategyType.EXPONENTIAL_BACKOFF_WAIT;
    }

    /**
     * NOTE: The below getters are not supported for setting/getting in Java. These will return
     * null to be initialized to default/null values in the JNI and C layers.
     * <p>
     * Check {@code setupDefaultKvsRetryStrategyParameters} in PIC for the default initialization.
     *
     * @see <a href="https://github.com/awslabs/amazon-kinesis-video-streams-pic/blob/master/src/client/src/Client.c">PIC</a>
     */
    public long getRetryStrategy() {
        return 0;
    }

    public long getRetryStrategyConfig() {
        return 0;
    }

    public int getRetryStrategyType() {
        return mKvsRetryStrategyType.getKvsRetryStrategyType();
    }
}
