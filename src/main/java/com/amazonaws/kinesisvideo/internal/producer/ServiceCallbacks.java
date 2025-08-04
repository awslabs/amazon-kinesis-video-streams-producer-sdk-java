package com.amazonaws.kinesisvideo.internal.producer;

import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.StreamDescription;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.kinesisvideo.producer.Tag;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 *
 * Interface to the KinesisVideo Producer Stream Service Callbacks functionality.
 *
 * These will be used for scheduling and managing
 * the service calls.
 *
 *
 */
public interface ServiceCallbacks
{
    /**
     * Initializes the callbacks object
     * @param kinesisVideoProducer Reference to {@link KinesisVideoProducer} for the eventing.
     * @throws ProducerException
     */
    void initialize(final @Nonnull KinesisVideoProducer kinesisVideoProducer) throws ProducerException;

    /**
     * Frees the callbacks object.
     */
    void free();

    /**
     * Returns whether the object is initialized
     * @return whether the object is initialized
     */
    boolean isInitialized();

    /**
     * Asynchronous call to create stream with the specified parameters. PIC will attempt to schedule stream creation if
     * the {@link #describeStream(String, long, long, byte[], int, long, KinesisVideoProducerStream)} posts a
     * {@link KinesisVideoProducer#describeStreamResult(KinesisVideoProducerStream, long, StreamDescription, int)} of
     * 404 (Resource Not Found). This call will be skipped if {@link StreamInfo#isAllowStreamCreation()} is not allowed.
     * <p>
     *   This method will notify PIC the success or failure of the operation via
     *   {@link KinesisVideoProducer#createStreamResult(KinesisVideoProducerStream, String, int)}.
     * </p>
     *
     * @param deviceName - Device name
     * @param streamName - Stream name
     * @param contentType - Stream content type
     * @param kmsKeyId - KMS Key Id
     * @param retentionPeriod - Stream retention period - 100ns
     * @param callAfter - Call after this time - 100ns
     * @param timeout - Time out for the call - 100ns
     * @param authData - Authentication bits
     * @param authType - Authentication type - this is the AUTH_INFO_TYPE defined in /src/client/Include.h
     * @param stream - stream object for the result event callback
     * @throws ProducerException
     *
     * @see <a href="https://docs.aws.amazon.com/kinesisvideostreams/latest/dg/API_CreateStream.html">CreateStream API</a>
     */
    void createStream(final @Nonnull String deviceName,
            final @Nonnull String streamName,
            final @Nonnull String contentType,
            final @Nullable String kmsKeyId,
            long retentionPeriod,
            long callAfter,
            long timeout,
            final @Nullable byte[] authData,
            final int authType,
            final @Nonnull KinesisVideoProducerStream stream) throws ProducerException;

    /**
     * Asynchronous call to describe stream with the specified parameters.
     * <p>
     *   This method will notify PIC the success or failure of the operation via
     *   {@link KinesisVideoProducer#describeStreamResult(KinesisVideoProducerStream, long, StreamDescription, int)}
     * </p>
     *
     * @param streamName - Stream name
     * @param callAfter - Call after this time - 100ns
     * @param timeout - Time out for the call - 100ns
     * @param authData - Authentication bits
     * @param authType - Authentication type - this is the AUTH_INFO_TYPE defined in /src/client/Include.h
     * @param streamHandle - stream handle returned by PIC
     * @param stream - stream object for the result event callback
     * @throws ProducerException
     *
     * @see <a href="https://docs.aws.amazon.com/kinesisvideostreams/latest/dg/API_DescribeStream.html">DescribeStream API</a>
     */
    void describeStream(final @Nonnull String streamName,
            long callAfter,
            long timeout,
            final @Nullable byte[] authData,
            int authType,
            long streamHandle,
            KinesisVideoProducerStream stream) throws ProducerException;

    /**
     * Asynchronous call to get streaming endpoint with the specified parameters.
     * <p>
     *   This method will notify PIC the success or failure of the operation via
     *   {@link KinesisVideoProducer#getStreamingEndpointResult(KinesisVideoProducerStream, long, String, int)}.
     * </p>
     *
     * @param streamName - Stream name
     * @param apiName - API name to call
     * @param callAfter - Call after this time - 100ns
     * @param timeout - Time out for the call - 100ns
     * @param authData - Authentication bits
     * @param authType - Authentication type - this is the AUTH_INFO_TYPE defined in /src/client/Include.h
     * @param streamHandle - stream handle returned by PIC
     * @param stream - stream object for the result event callback
     * @throws ProducerException
     *
     * @see <a href="https://docs.aws.amazon.com/kinesisvideostreams/latest/dg/API_GetDataEndpoint.html">GetDataEndpoint API</a>
     */
    void getStreamingEndpoint(final @Nonnull String streamName,
                              final @Nonnull String apiName,
                              long callAfter,
                              long timeout,
                              final @Nullable byte[] authData,
                              int authType,
                              long streamHandle,
                              KinesisVideoProducerStream stream) throws ProducerException;

    /**
     * Asynchronous call to get streaming token
     * @param streamName - Stream name
     * @param callAfter - Call after this time - 100ns
     * @param timeout - Time out for the call - 100ns
     * @param authData - Authentication bits
     * @param authType - Authentication type - this is the AUTH_INFO_TYPE defined in /src/client/Include.h
     * @param streamHandle - stream handle returned by PIC
     * @param stream - stream object for the result event callback
     * @throws ProducerException
     */
    void getStreamingToken(final @Nonnull String streamName,
                           long callAfter,
                           long timeout,
                           final @Nullable byte[] authData,
                           int authType,
                           long streamHandle,
                           KinesisVideoProducerStream stream) throws ProducerException;

    /**
     * Asynchronous call to put stream API
     * @param streamName - Stream name
     * @param containerType - Container type
     * @param streamStartTime - Stream start timestamp
     * @param absoluteFragmentTimes - Whether to use absolute fragment times
     * @param ackRequired - Whether an application level ACK is required
     * @param streamingEndpoint - The streaming endpoint to use
     * @param callAfter - Call after this time - 100ns
     * @param timeout - Time out for the call - 100ns
     * @param authData - Authentication bits
     * @param authType - Authentication type - this is the AUTH_INFO_TYPE defined in /src/client/Include.h
     * @param stream - stream object for the result event callback
     * @throws ProducerException
     */
    void putStream(final @Nonnull String streamName,
            final @Nonnull String containerType,
            long streamStartTime,
            boolean absoluteFragmentTimes,
            boolean ackRequired,
            final @Nonnull String streamingEndpoint,
            long callAfter,
            long timeout,
            final @Nullable byte[] authData,
            int authType,
            KinesisVideoProducerStream stream) throws ProducerException;

    /**
     * Asynchronous call to tag resource API
     * @param resourceArn - Resource ARN
     * @param tags - Tags to apply
     * @param callAfter - Call after this time - 100ns
     * @param timeout - Time out for the call - 100ns
     * @param authData - Authentication bits
     * @param authType - Authentication type - this is the AUTH_INFO_TYPE defined in /src/client/Include.h
     * @param streamHandle - stream handle returned by PIC
     * @param stream - stream object for the result event callback
     * @throws ProducerException
     */
    void tagResource(final @Nonnull String resourceArn,
            final @Nullable Tag[] tags,
            long callAfter,
            long timeout,
            final @Nullable byte[] authData,
            int authType,
            long streamHandle,
            KinesisVideoProducerStream stream) throws ProducerException;

    /**
     * Asynchronous call to create device
     * @param deviceName - Device name
     * @param callAfter - Call after this time - 100ns
     * @param timeout - Time out for the call - 100ns
     * @param authData - Authentication bits
     * @param authType - Authentication type - this is the AUTH_INFO_TYPE defined in /src/client/Include.h
     * @param customData - Custom data to use to call the event functions
     * @throws ProducerException
     */
    void createDevice(final @Nonnull String deviceName,
            long callAfter,
            long timeout,
            final @Nullable byte[] authData,
            int authType,
            final long customData) throws ProducerException;

    /**
     * Asynchronous call to device certificate to token API. Called when AuthType is AUTH_INFO_TYPE_CERT.
     *
     * @param deviceName - Device name
     * @param callAfter - Call after this time - 100ns
     * @param timeout - Time out for the call - 100ns
     * @param authData - Authentication bits
     * @param authType - Authentication type - this is the AUTH_INFO_TYPE defined in /src/client/Include.h
     * @param customData - Custom data to use to call the event functions
     *
     * @throws ProducerException
     */
    void deviceCertToToken(final @Nonnull String deviceName,
            long callAfter,
            long timeout,
            final @Nullable byte[] authData,
            int authType,
            long customData) throws ProducerException;

    /**
     * Add a producer stream to ongoing stream list
     * @param kinesisVideoProducerStream producer stream used by PIC
     */
    void addStream(@Nonnull final KinesisVideoProducerStream kinesisVideoProducerStream);

    /**
     * Removes a producer stream to ongoing stream list
     * @param kinesisVideoProducerStream producer stream used by PIC
     */
    void removeStream(@Nonnull final KinesisVideoProducerStream kinesisVideoProducerStream);
}
