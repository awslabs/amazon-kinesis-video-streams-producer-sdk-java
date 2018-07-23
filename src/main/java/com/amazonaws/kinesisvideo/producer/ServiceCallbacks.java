package com.amazonaws.kinesisvideo.producer;

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
     * Schedules a call to create stream
     * @param deviceName - Device name
     * @param streamName - Stream name
     * @param contentType - Stream content type
     * @param kmsKeyId - KMS Key Id
     * @param retentionPeriod - Stream retention period - 100ns
     * @param callAfter - Call after this time - 100ns
     * @param timeout - Time out for the call - 100ns
     * @param authData - Authentication bits
     * @param authType - Authentication type - this is the AUTH_INFO_TYPE defined in /src/client/Include.h
     * @param customData - Custom data to use to call the event functions
     * @throws ProducerException
     */
    void createStream(final @Nonnull String deviceName,
            final @Nonnull String streamName,
            final @Nonnull String contentType,
            final @Nullable String kmsKeyId,
            long retentionPeriod,
            long callAfter,
            long timeout,
            final @Nullable byte[] authData,
            int authType,
            long customData) throws ProducerException;

    /**
     * Schedules a call to describe stream
     * @param streamName - Stream name
     * @param callAfter - Call after this time - 100ns
     * @param timeout - Time out for the call - 100ns
     * @param authData - Authentication bits
     * @param authType - Authentication type - this is the AUTH_INFO_TYPE defined in /src/client/Include.h
     * @param customData - Custom data to use to call the event functions
     * @throws ProducerException
     */
    void describeStream(final @Nonnull String streamName,
            long callAfter,
            long timeout,
            final @Nullable byte[] authData,
            int authType,
            long customData) throws ProducerException;

    /**
     * Schedules a call to get streaming endpoint
     * @param streamName - Stream name
     * @param apiName - API name to call
     * @param callAfter - Call after this time - 100ns
     * @param timeout - Time out for the call - 100ns
     * @param authData - Authentication bits
     * @param authType - Authentication type - this is the AUTH_INFO_TYPE defined in /src/client/Include.h
     * @param customData - Custom data to use to call the event functions
     * @throws ProducerException
     */
    void getStreamingEndpoint(final @Nonnull String streamName,
            final @Nonnull String apiName,
            long callAfter,
            long timeout,
            final @Nullable byte[] authData,
            int authType,
            long customData) throws ProducerException;

    /**
     * Schedules a call to get streaming token
     * @param streamName - Stream name
     * @param callAfter - Call after this time - 100ns
     * @param timeout - Time out for the call - 100ns
     * @param authData - Authentication bits
     * @param authType - Authentication type - this is the AUTH_INFO_TYPE defined in /src/client/Include.h
     * @param customData - Custom data to use to call the event functions
     * @throws ProducerException
     */
    void getStreamingToken(final @Nonnull String streamName,
            long callAfter,
            long timeout,
            final @Nullable byte[] authData,
            int authType,
            long customData) throws ProducerException;

    /**
     * Schedules a call to put stream API
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
     * @param customData - Custom data to use to call the event functions
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
            long customData) throws ProducerException;

    /**
     * Schedules a call to tag resource API
     * @param resourceArn - Resource ARN
     * @param tags - Tags to apply
     * @param callAfter - Call after this time - 100ns
     * @param timeout - Time out for the call - 100ns
     * @param authData - Authentication bits
     * @param authType - Authentication type - this is the AUTH_INFO_TYPE defined in /src/client/Include.h
     * @param customData - Custom data to use to call the event functions
     * @throws ProducerException
     */
    void tagResource(final @Nonnull String resourceArn,
            final @Nullable Tag[] tags,
            long callAfter,
            long timeout,
            final @Nullable byte[] authData,
            int authType,
            long customData) throws ProducerException;

    /**
     * Schedules a call to create device
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
            long customData) throws ProducerException;

    /**
     * Schedules a call to device certificate to token API
     * @param deviceName - Device name
     * @param callAfter - Call after this time - 100ns
     * @param timeout - Time out for the call - 100ns
     * @param authData - Authentication bits
     * @param authType - Authentication type - this is the AUTH_INFO_TYPE defined in /src/client/Include.h
     * @param customData - Custom data to use to call the event functions
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
}
