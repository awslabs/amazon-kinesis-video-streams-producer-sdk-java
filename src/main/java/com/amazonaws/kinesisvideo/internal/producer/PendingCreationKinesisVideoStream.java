package com.amazonaws.kinesisvideo.internal.producer;

import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFragmentAck;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.StreamCallbacks;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStream;

/**
 * KinesisVideoProducerStream that only supports:
 * <ul>
 *  <li>{@link #getStreamInfo()}</li>
 *  <li>{@link #streamErrorReport(long, long, long)}</li>
 *  <li>{@link #getStreamHandle()}</li>
 * </ul>
 *
 * @see KinesisVideoProducerStream
 */
public class PendingCreationKinesisVideoStream implements KinesisVideoProducerStream {

    private static final Logger log = LogManager.getLogger(PendingCreationKinesisVideoStream.class);

    final StreamInfo streamInfo;
    final StreamCallbacks streamCallbacks;
    final long streamHandle;

    @SuppressWarnings("ConstantConditions")
    public PendingCreationKinesisVideoStream(final long streamHandle,
                                             @Nonnull final StreamInfo streamInfo,
                                             @Nonnull final StreamCallbacks streamCallbacks) {

        Preconditions.checkArgument(streamInfo != null, "streamInfo cannot be null!");
        Preconditions.checkArgument(streamCallbacks != null, "streamCallbacks cannot be null!");

        this.streamHandle = streamHandle;
        this.streamInfo = streamInfo;
        this.streamCallbacks = streamCallbacks;

        log.debug("Created Pending Kinesis Video Stream ({}) with handle: 0x{}",
                streamInfo.getSummary(), Long.toHexString(streamHandle));
    }

    @Override
    public long getStreamHandle() {
        return this.streamHandle;
    }

    @Override
    public StreamInfo getStreamInfo() {
        return this.streamInfo;
    }

    @Override
    public void streamErrorReport(final long uploadHandle, final long fragmentTimecode, final long statusCode) throws ProducerException {
        this.streamCallbacks.streamErrorReport(uploadHandle, fragmentTimecode, statusCode);
    }

    @Nonnull
    @Override
    public InputStream getDataStream(final long uploadHandle) throws ProducerException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void getStreamData(final long uploadHandle, @Nonnull final byte[] fillBuffer, final int offset, final int length, @Nonnull final ReadResult readResult) throws ProducerException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putFrame(@Nonnull final KinesisVideoFrame kinesisVideoFrame) throws ProducerException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putFragmentMetadata(@Nonnull final String metadataName, @Nonnull final String metadataValue, final boolean persistent) throws ProducerException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void fragmentAck(final long uploadHandle, @Nonnull final KinesisVideoFragmentAck kinesisVideoFragmentAck) throws ProducerException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void parseFragmentAck(final long uploadHandle, @Nonnull final String kinesisVideoFragmentAck) throws ProducerException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void streamFormatChanged(@Nullable final byte[] codecPrivateData, final int trackId) throws ProducerException {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public String getStreamName() {
        return this.streamInfo.getName();
    }

    @Override
    public void stopStream() throws ProducerException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void stopStreamSync() throws ProducerException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void streamTerminated(final long uploadHandle, final int statusCode) throws ProducerException {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public KinesisVideoStreamMetrics getMetrics() throws ProducerException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void streamFreed() throws ProducerException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void resetConnection() throws ProducerException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void streamUnderflowReport() throws ProducerException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void streamLatencyPressure(final long duration) throws ProducerException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void streamConnectionStale(final long lastAckDuration) throws ProducerException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void fragmentAckReceived(final long uploadHandle, @Nonnull final KinesisVideoFragmentAck fragmentAck) throws ProducerException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void droppedFrameReport(final long frameTimecode) throws ProducerException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void droppedFragmentReport(final long fragmentTimecode) throws ProducerException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void streamDataAvailable(final long uploadHandle, final long duration, final long availableSize) throws ProducerException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void streamReady() throws ProducerException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void streamClosed(final long uploadHandle) throws ProducerException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void bufferDurationOverflowPressure(final long remainDuration) throws ProducerException {
        throw new UnsupportedOperationException();
    }
}
