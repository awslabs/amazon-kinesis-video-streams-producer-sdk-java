package com.amazonaws.kinesisvideo.common;

import javax.annotation.Nonnull;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import com.amazonaws.kinesisvideo.producer.FragmentAckType;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFragmentAck;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.storage.DefaultStorageCallbacks;
import com.amazonaws.kinesisvideo.streaming.DefaultStreamCallbacks;

class TestStorageCallbacks extends DefaultStorageCallbacks {

    private final ProducerTestBase producerTestBase;
    private final Logger log = LogManager.getLogger(TestStorageCallbacks.class);

    protected TestStorageCallbacks(ProducerTestBase producerTestBase) {
        this.producerTestBase = producerTestBase;
    }

    @Override
    public void storageOverflowPressure(long remainingSize) {
        producerTestBase.storageOverflow_ = true;
        log.warn("Reporting storage overflow. Bytes remaining: {}", remainingSize);
    }

}

class TestStreamCallBacks extends DefaultStreamCallbacks {

    private final ProducerTestBase producerTestBase;
    private final Logger log = LogManager.getLogger(TestStreamCallBacks.class);

    protected TestStreamCallBacks(ProducerTestBase producerTestBase) {
        this.producerTestBase = producerTestBase;
    }

    @Override
    public void streamLatencyPressure(final long duration) throws ProducerException {
        producerTestBase.latencyPressureCount_++;
        log.warn("Reporting stream latency pressure. Current buffer duration: {}", duration);
    }

    @Override
    public void streamConnectionStale(final long lastAckDuration) throws ProducerException {
        log.warn("Reporting stream stale. Last ACK received: {}", lastAckDuration);
    }

    @Override
    public void fragmentAckReceived(final long uploadHandle, @Nonnull final KinesisVideoFragmentAck fragmentAck)
            throws ProducerException {
        FragmentAckType bufferingAck = new FragmentAckType(FragmentAckType.FRAGMENT_ACK_TYPE_BUFFERING);

        log.trace("Reporting fragment ack");
        if(fragmentAck.getAckType().equals(bufferingAck)) {
            if(producerTestBase.previousBufferingAckTimestamp_.containsKey(uploadHandle)) { //uploadHandle exists in the Map
                if(fragmentAck.getTimestamp() != producerTestBase.previousBufferingAckTimestamp_.get(uploadHandle) && // can be the same in case of retransmits
                    fragmentAck.getTimestamp() - producerTestBase.previousBufferingAckTimestamp_.get(uploadHandle) >
                            producerTestBase.getFragmentDurationMs()) { //curr - prev > fragmentDuration
                    log.error("Buffering ack not in sequence. Previous ack ts: {} Current ack ts: {}",
                            producerTestBase.previousBufferingAckTimestamp_.get(uploadHandle), fragmentAck.getTimestamp());
                    producerTestBase.bufferingAckInSequence_ = false;
                }
            }
            producerTestBase.previousBufferingAckTimestamp_.put(uploadHandle, fragmentAck.getTimestamp());
        }
    }

    @Override
    public void droppedFrameReport(final long frameTimecode) throws ProducerException {
        producerTestBase.frameDropped_ = true;
        log.warn("Reporting dropped frame. Frame timecode {}", frameTimecode);
    }

    @Override
    public void streamErrorReport(final long uploadHandle, final long frameTimecode, final long statusCode)
            throws ProducerException {
        log.error("Reporting stream error. Errored time code {} with status code {}", frameTimecode, statusCode);
        producerTestBase.errorStatus_ = statusCode;
    }

    @Override
    public void droppedFragmentReport(final long fragmentTimecode) throws ProducerException {
        log.warn("Reporting dropped frame. Fragment timecode {}", fragmentTimecode);
    }

    @Override
    public void streamDataAvailable(final long uploadHandle, final long duration, final long availableSize)
            throws ProducerException {
        log.trace("Reporting stream data available");
    }

    @Override
    public void streamReady() throws ProducerException {
        log.trace("Reporting stream ready");
    }

    @Override
    public void streamClosed(final long uploadHandle) throws ProducerException {
        log.info("Reporting stream stopped");
        producerTestBase.stopCalled_ = true;
    }

    @Override
    public void bufferDurationOverflowPressure(final long remainDuration) throws ProducerException {
        producerTestBase.bufferDurationPressure_ = true;
        log.warn("Reporting buffer duration overflow pressure. remaining duration {}", remainDuration);
    }
}
