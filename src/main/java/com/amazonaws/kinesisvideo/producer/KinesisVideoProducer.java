package com.amazonaws.kinesisvideo.producer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Interface to the Kinesis Video Producer functionality
 */
public interface KinesisVideoProducer {
    /**
     * Ready timeout value in milliseconds for the sync API
     */
    public static final long READY_TIMEOUT_IN_MILLISECONDS = 5000;

    /**
     * Returns whether the client has been initialized
     */
    boolean isInitialized();

    /**
     * Returns whether the client has been initialized and ready for the stream to be created.
     */
    boolean isReady();

    /**
     * Creates the underlying producer client.
     *
     * @param deviceInfo {@link DeviceInfo} object
     * @throws ProducerException
     */
    void create(@Nonnull final DeviceInfo deviceInfo) throws ProducerException;

    /**
     * Creates the underlying producer client synchronously.
     *
     * @param deviceInfo {@link DeviceInfo} object
     * @throws ProducerException
     */
    void createSync(@Nonnull final DeviceInfo deviceInfo) throws ProducerException;

    /**
     * Stops the streams and frees/releases the underlying object
     */
    void free() throws ProducerException;

    /**
     * Frees all of the underlying streams
     * @throws ProducerException
     */
    void freeStreams() throws ProducerException;

    /**
     * Stops all the streams
     */
    void stopStreams() throws ProducerException;

    /**
     * Creates Kinesis Video stream
     *
     * @param streamInfo      Stream information {@link StreamInfo} object
     * @param streamCallbacks Optional stream callnbacks {@link StreamCallbacks}
     * @return The newly created stream
     * @throws ProducerException
     */
    @Nonnull
    KinesisVideoProducerStream createStream(final @Nonnull StreamInfo streamInfo,
                                      final @Nullable StreamCallbacks streamCallbacks) throws ProducerException;

    /**
     * Creates Kinesis Video stream synchronously
     *
     * @param streamInfo      Stream information {@link StreamInfo} object
     * @param streamCallbacks Optional stream callnbacks {@link StreamCallbacks}
     * @return The newly created stream
     * @throws ProducerException
     */
    @Nonnull
    KinesisVideoProducerStream createStreamSync(final @Nonnull StreamInfo streamInfo,
                                          final @Nullable StreamCallbacks streamCallbacks) throws ProducerException;

    /**
     * Frees the specified stream
     * @param stream Stream to free
     * @throws ProducerException
     */
    void freeStream(final @Nonnull KinesisVideoProducerStream stream) throws ProducerException;

    /**
     * CreateStream result event
     *
     * @param customData     Custom data that should be passed to the engine
     * @param streamArn      Newly create stream ARN on success
     * @param httpStatusCode HTTP status code
     * @throws ProducerException
     */
    void createStreamResult(final long customData, final @Nullable String streamArn, int httpStatusCode) throws ProducerException;

    /**
     * DescribeStream result event
     *
     * @param customData        Custom data that should be passed to the engine
     * @param streamDescription Stream description object
     * @param httpStatusCode    HTTP status code
     * @throws ProducerException
     */
    void describeStreamResult(final long customData,
                              final @Nullable StreamDescription streamDescription,
                              int httpStatusCode) throws ProducerException;

    /**
     * GetStreamingEndpoint result event
     *
     * @param customData     Custom data that should be passed to the engine
     * @param endpoint       Streaming endpoint if successful
     * @param httpStatusCode HTTP status code
     * @throws ProducerException
     */
    void getStreamingEndpointResult(final long customData, final @Nullable String endpoint,
                                    int httpStatusCode)
            throws ProducerException;

    /**
     * GetStreamingToken result event
     *
     * @param customData     Custom data that should be passed to the engine
     * @param token          Streaming token if successful
     * @param expiration     Streaming token expiration in absolute time in 100ns
     * @param httpStatusCode HTTP status code
     * @throws ProducerException
     */
    void getStreamingTokenResult(final long customData, final @Nullable byte[] token, long expiration, int httpStatusCode)
            throws ProducerException;

    /**
     * PutStream result event
     *
     * @param customData         Custom data that should be passed to the engine
     * @param clientStreamHandle A stream handle identifier from the client side
     * @param httpStatusCode     HTTP status code
     * @throws ProducerException
     */
    void putStreamResult(final long customData, long clientStreamHandle, int httpStatusCode)
            throws ProducerException;

    /**
     * TagResource result event
     *
     * @param customData     Custom data that should be passed to the engine
     * @param httpStatusCode HTTP status code
     * @throws ProducerException
     */
    void tagResourceResult(final long customData, int httpStatusCode)
            throws ProducerException;

    /**
     * CreateDevice result event
     *
     * @param customData     Custom data that should be passed to the engine
     * @param deviceArn      Newly create device ARN on success
     * @param httpStatusCode HTTP status code
     * @throws ProducerException
     */
    void createDeviceResult(final long customData, final @Nullable String deviceArn, int httpStatusCode)
            throws ProducerException;

    /**
     * DeviceCertToToken result event
     *
     * @param customData     Custom data that should be passed to the engine
     * @param token          Security token if successful
     * @param expiration     Streaming token expiration in absolute time in 100ns
     * @param httpStatusCode HTTP status code
     * @throws ProducerException
     */
    void deviceCertToTokenResult(final long customData, final @Nullable byte[] token, long expiration, int httpStatusCode)
            throws ProducerException;

    @Nonnull
    KinesisVideoMetrics getMetrics()
            throws ProducerException;
}