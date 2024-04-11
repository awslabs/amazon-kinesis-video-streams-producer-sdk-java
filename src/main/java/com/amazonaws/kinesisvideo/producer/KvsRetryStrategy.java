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
        return mRetryStrategy;
    }

    public long getRetryStrategyConfig() {
        return mRetryStrategyConfig;
    }

    public KvsRetryStrategyType getKvsRetryStrategyType() {
        return mKvsRetryStrategyType;
    }
}
