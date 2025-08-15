package com.amazonaws.kinesisvideo.streaming;

import com.amazonaws.kinesisvideo.producer.KinesisVideoFragmentAck;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.StreamCallbacks;

import javax.annotation.Nonnull;

/**
 * No-op implementation of stream callbacks. Extending this class allows convenient
 * implementation of certain callbacks while leaving the rest as no-op.
 *
 * <p>For example:</p>
 *
 * <pre>
 *StreamCallbacks streamCallbacks =
 *    new DefaultStreamCallbacks() {
 *        &#64;Override
 *        public void fragmentAckReceived(final long uploadHandle, &#64;Nonnull final KinesisVideoFragmentAck fragmentAck) throws ProducerException {
 *            super.fragmentAckReceived(uploadHandle, fragmentAck);
 *
 *            // Stop submitting frames on user errors
 *            if (fragmentAck.getAckType().getIntType() == FragmentAckType.FRAGMENT_ACK_TYPE_ERROR &amp;&amp;
 *                4000 &lt;= fragmentAck.getResult() &amp;&amp; fragmentAck.getResult() &lt; 5000) {
 *                log.error("{} - Received an error ack: {}", fragmentAck);
 *                mediaSource.stop();
 *            }
 *        }
 *
 *        &#64;Override
 *        public void streamErrorReport(final long uploadHandle, final long frameTimecode, final long statusCode) throws ProducerException {
 *            super.streamErrorReport(uploadHandle, frameTimecode, statusCode);
 *            log.error("{} Encountered a streaming error with status code: 0x", streamName, Long.toHexString(statusCode));
 *            mediaSource.stop();
 *        }
 *    };
 *</pre>
 */
public class DefaultStreamCallbacks implements StreamCallbacks {
    @Override
    public void streamUnderflowReport() throws ProducerException {
        // no-op
    }

    @Override
    public void streamLatencyPressure(final long duration) throws ProducerException {
        // no-op
    }

    @Override
    public void streamConnectionStale(final long lastAckDuration) throws ProducerException {
        // no-op
    }

    @Override
    public void fragmentAckReceived(final long uploadHandle, @Nonnull final KinesisVideoFragmentAck fragmentAck) throws ProducerException {
        // no-op
    }

    @Override
    public void droppedFrameReport(final long frameTimecode) throws ProducerException {
        // no-op
    }

    @Override
    public void streamErrorReport(final long uploadHandle, final long frameTimecode, final long statusCode) throws ProducerException {
        // no-op
    }

    @Override
    public void droppedFragmentReport(final long fragmentTimecode) throws ProducerException {
        // no-op
    }

    @Override
    public void streamDataAvailable(final long uploadHandle, final long duration, final long availableSize)
            throws ProducerException {
        // no-op
    }

    @Override
    public void streamReady() throws ProducerException {
        // no-op
    }

    @Override
    public void streamClosed(final long uploadHandle) throws ProducerException {
        // no-op
    }

    @Override
    public void bufferDurationOverflowPressure(final long remainDuration) throws ProducerException {
        // no-op
    }
}
