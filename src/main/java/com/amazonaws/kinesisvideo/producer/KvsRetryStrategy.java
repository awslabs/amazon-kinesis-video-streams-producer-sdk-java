package com.amazonaws.kinesisvideo.producer;

public class KvsRetryStrategy {

    private final long mRetryStrategy;
    private final long mRetryStrategyConfig;
    private final KvsRetryStrategyType mKvsRetryStrategyType;

    public KvsRetryStrategy() {
        mRetryStrategy = 0;
        mRetryStrategyConfig = 0;
        mKvsRetryStrategyType = KvsRetryStrategyType.DISABLED;
    }


    public long getRetryStrategy() {
        return 0;
    }

    public long getRetryStrategyConfig() {
        return 0;
    }

    public KvsRetryStrategyType getKvsRetryStrategyType() {
        return null;
    }
}
