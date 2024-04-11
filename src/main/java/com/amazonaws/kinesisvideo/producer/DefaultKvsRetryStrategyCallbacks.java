package com.amazonaws.kinesisvideo.producer;

import javax.annotation.Nullable;

public class DefaultKvsRetryStrategyCallbacks implements KvsRetryStrategyCallbacks {

    @Nullable
    @Override
    public void createRetryStrategyFn(KvsRetryStrategy kvsRetryStrategy) throws ProducerException {
        // no-op
    }

    @Nullable
    @Override
    public void getCurrentRetryAttemptNumberFn(KvsRetryStrategy kvsRetryStrategy, int retryCount) throws ProducerException {
        // no-op
    }

    @Nullable
    @Override
    public void freeRetryStrategyFn(KvsRetryStrategy kvsRetryStrategy) throws ProducerException {
        // no-op
    }

    @Nullable
    @Override
    public void executeRetryStrategyFn(KvsRetryStrategy kvsRetryStrategy, long retryWaitTime) throws ProducerException {
        // no-op
    }

}
