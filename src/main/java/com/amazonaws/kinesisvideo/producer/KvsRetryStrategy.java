package com.amazonaws.kinesisvideo.producer;

public class KvsRetryStrategy {

    private final long mRetryStrategy;
    private final long mRetryStrategyConfig;
    private final KvsRetryStrategyType mKvsRetryStrategyType;

    public KvsRetryStrategy() {
        mRetryStrategy = 0;
        mRetryStrategyConfig = 0;
        mKvsRetryStrategyType = KvsRetryStrategyType.EXPONENTIAL_BACKOFF_WAIT;
    }

    /**
     * NOTE: The below getters are not supported for setting/getting in Java. These will return
     * null to be initialized to default/null values in the JNI and C layers.
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
