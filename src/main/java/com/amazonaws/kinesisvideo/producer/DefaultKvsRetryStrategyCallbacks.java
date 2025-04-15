package com.amazonaws.kinesisvideo.producer;

/**
 * No-op implementation.
 */
public class DefaultKvsRetryStrategyCallbacks implements KvsRetryStrategyCallbacks {

    @Override
    public void createRetryStrategyFn(KvsRetryStrategy kvsRetryStrategy) throws ProducerException {
        // no-op
    }

    @Override
    public void getCurrentRetryAttemptNumberFn(KvsRetryStrategy kvsRetryStrategy, int retryCount) throws ProducerException {
        // no-op
    }

    @Override
    public void freeRetryStrategyFn(KvsRetryStrategy kvsRetryStrategy) throws ProducerException {
        // no-op
    }

    @Override
    public void executeRetryStrategyFn(KvsRetryStrategy kvsRetryStrategy, long retryWaitTime) throws ProducerException {
        // no-op
    }

}
