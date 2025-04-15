package com.amazonaws.kinesisvideo.producer;

/**
 * This interface holds the retry strategy callback functions.
 * <p>
 * NOTE: This should follow the structure defined in PIC's /kvspic-src/src/utils/include/com/amazonaws/kinesis/video/utils/Include.h
 *
 * @see <a href="https://github.com/awslabs/amazon-kinesis-video-streams-pic/blob/master/src/utils/include/com/amazonaws/kinesis/video/utils/Include.h">PIC</a>
 */
public interface KvsRetryStrategyCallbacks {

    // Create new retry strategy
    void createRetryStrategyFn(KvsRetryStrategy kvsRetryStrategy) throws ProducerException;

    // Get retry count
    void getCurrentRetryAttemptNumberFn(KvsRetryStrategy kvsRetryStrategy, int retryCount) throws ProducerException;

    // Release allocated resources associated with the retry strategy
    void freeRetryStrategyFn(KvsRetryStrategy kvsRetryStrategy) throws ProducerException;

    // Actual handler for the given retry strategy
    void executeRetryStrategyFn(KvsRetryStrategy kvsRetryStrategy, long retryWaitTime) throws ProducerException;

}
