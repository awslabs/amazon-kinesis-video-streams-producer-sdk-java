package com.amazonaws.kinesisvideo.producer;

/**
 * This interface holds the retry strategy callback functions.
 *
 * NOTE: This should follow the structure defined in PIC's /kvspic-src/src/utils/include/com/amazonaws/kinesis/video/utils/Include.h
 *
 */
public interface KvsRetryStrategyCallbacks {

    void createRetryStrategyFn(KvsRetryStrategy kvsRetryStrategy) throws ProducerException;
    void getCurrentRetryAttemptNumberFn(KvsRetryStrategy kvsRetryStrategy, int retryCount) throws ProducerException;
    void freeRetryStrategyFn(KvsRetryStrategy kvsRetryStrategy) throws ProducerException;
    void executeRetryStrategyFn(KvsRetryStrategy kvsRetryStrategy, long retryWaitTime) throws ProducerException;

}
