package com.amazonaws.kinesisvideo.internal.producer;

import com.amazonaws.kinesisvideo.producer.KinesisVideoFragmentAck;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.StreamCallbacks;
import com.amazonaws.kinesisvideo.producer.StreamInfo;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * KinesisVideo stream interface
 */

public interface KinesisVideoProducerStream extends StreamCallbacks {
    /**
     * Ready timeout value in milliseconds for the sync API
     */
    public static final long READY_TIMEOUT_IN_MILLISECONDS = 15000;

    /**
     * Stopped timeout value in milliseconds for the sync API
     */
    public static final long STOPPED_TIMEOUT_IN_MILLISECONDS = 15000;

    /**
     * Returns the input stream to retrieve the data from.
     *
     * NOTE: The returned stream should ideally be buffered
     * like {@link java.io.BufferedInputStream}
     *
     * IMPORTANT: During the streaming token rotation or when the underlying stream
     * gets closed the stream returned by this function will also close.
     * The caller then should re-acquire a new stream by calling this API again.
     *
     * @param uploadHandle Client stream upload handle.
     * @return {@link InputStream} for retrieving the data
     * @throws ProducerException
     */
    @Nonnull
    InputStream getDataStream(final long uploadHandle) throws ProducerException;

    /**
     * Get stream data from the buffer.
     *
     * @param uploadHandle Client stream upload handle.
     * @param fillBuffer The buffer to fill
     * @param offset The start of the buffer
     * @param length The number of bytes to fill
     * @param readResult The result of the read
     * @throws ProducerException
     */
    void getStreamData(final long uploadHandle, final @Nonnull byte[] fillBuffer, int offset, int length,
                       @Nonnull final ReadResult readResult)
            throws ProducerException;

    /**
     * Puts a frame into the stream.
     */
    void putFrame(final @Nonnull KinesisVideoFrame kinesisVideoFrame) throws ProducerException;

    /**
     * Puts a fragment metadata into the stream.
     */
    void putFragmentMetadata(final @Nonnull String metadataName, @Nonnull String metadataValue, boolean persistent) throws ProducerException;

    /**
     * Puts an event metadata into the stream.
     */
    void putEventMetadata(int event, @Nullable StreamEventMetadata streamEventMetadata) throws ProducerException;

    /**
     * Reports an ACK for a fragment.
     *
     * @param uploadHandle Client stream upload handle.
     * @param kinesisVideoFragmentAck ACK string returned from the service.
     * @throws ProducerException
     */
    void fragmentAck(final long uploadHandle, final @Nonnull KinesisVideoFragmentAck kinesisVideoFragmentAck) throws ProducerException;

    /**
     * Parses and processes a response which can contain partial/multiple fragment ACK.
     * @param uploadHandle Client stream upload handle.
     * @param kinesisVideoFragmentAck ACK string returned from the service.
     * @throws ProducerException
     */
    void parseFragmentAck(final long uploadHandle, final @Nonnull String kinesisVideoFragmentAck) throws ProducerException;

    /**
     * Indicates that the stream format has changed.
     *
     * NOTE: CPD is @Nullable - specifying a null will remove the CPD.
     * NOTE: currently, only Codec Private Data is supported while not streaming.
     */
    void streamFormatChanged(final @Nullable byte[] codecPrivateData, final int trackId) throws ProducerException;

    /**
     * Returns the underlying native stream handle
     * @return
     */
    long getStreamHandle();

    /**
     * Returns the stream name
     */
    @Nonnull
    String getStreamName();

    /**
     * Stops the Kinesis Video stream. This simply kicks off stop stream operation but doesn't await for the resulting
     * stream stopped notification.
     */
    void stopStream() throws ProducerException;

    /**
     * Stops the Kinesis Video stream and awaits for the stream stopped notification in a sync call.
     *
     * From an application callback, prefer {@link #stopStreamAsync()}: waiting there also holds up the client's later
     * callbacks until the stream has closed. Called from inside a native callback, it only kicks off the stop, like
     * {@link #stopStream()}: that thread can hold the lock the stream needs to finish stopping.
     */
    void stopStreamSync() throws ProducerException;

    /**
     * Stops the Kinesis Video stream without waiting. The future completes when the stream has sent its remaining data
     * and closed, or exceptionally if the stop could not be started. Safe to call from any thread, including callbacks.
     * Use {@code get(timeout, unit)} to bound the wait.
     */
    default CompletableFuture<Void> stopStreamAsync()
    {
        return CompletableFuture.runAsync(() -> {
            try {
                stopStreamSync();
            } catch (final ProducerException e) {
                throw new CompletionException(e);
            }
        });
    }

    /**
     * Reports an abnormal stream termination
     * @param uploadHandle - Client stream upload handle.
     * @param statusCode - Status code of the termination.
     * @throws ProducerException
     */
    void streamTerminated(long uploadHandle, int statusCode) throws ProducerException;

    /**
     * Returns stream specific metrics.
     * @return Stream metrics
     * @throws ProducerException
     */
    @Nonnull
    KinesisVideoStreamMetrics getMetrics() throws ProducerException;

    /**
     * Free the Kinesis Video stream.
     */
    void streamFreed() throws ProducerException;

    /**
     * Reset current connection of producer stream
     */
    void resetConnection() throws ProducerException;

    @Nonnull
    StreamInfo getStreamInfo();
}
