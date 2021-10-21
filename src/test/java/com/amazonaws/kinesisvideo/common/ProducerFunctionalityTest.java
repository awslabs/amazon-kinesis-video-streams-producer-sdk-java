package com.amazonaws.kinesisvideo.common;

import java.nio.ByteBuffer;

import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import com.amazonaws.kinesisvideo.common.logging.Log;
import com.amazonaws.kinesisvideo.common.logging.LogLevel;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.producer.*;

public class ProducerFunctionalityTest extends ProducerTestBase{

    /**
     * This test creates a stream, stops it and frees it
     */
    @Test
    public void startStopSyncTerminate() {
        KinesisVideoProducerStream kinesisVideoProducerStream;

        storageInfo_ = new StorageInfo(0,
                StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM, STORAGE_SIZE_MEGS,
                SPILL_RATIO_PERCENT, STORAGE_PATH);
        deviceInfo_ = new DeviceInfo(DEVICE_VERSION,
                DEVICE_NAME, storageInfo_, NUMBER_OF_STREAMS, null);

        createProducer(); // resets all flags, creates callbacks and creates a KinesisVideoProducer
        kinesisVideoProducerStream = createTestStream("JavaFuncTest_startStopSyncTerminate",
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME, TEST_LATENCY, TEST_BUFFER_DURATION);
        // uses the KinesisVideoProducer to create a stream
        try {
            kinesisVideoProducerStream.stopStreamSync();
        } catch(ProducerException e) {
            e.printStackTrace();
            fail();
        }
        freeStreams(); // frees all the streams associated with the Producer
    }

    /**
     * This test sets the buffer-duration to 4 seconds and checks if there are any frames dropped, any errors received
     * or any buffering acks that are not in sequence.
     */
    @Test
    public void offlineUploadLimitedBufferDuration() {
        final Log log = new Log(Log.SYSTEM_OUT, LogLevel.VERBOSE,
                "ProducerFunctionalityTest/offlineUploadLimitedBufferDuration");
        int flags;
        long currentTimeMs = 0;
        byte[][] framesData = new byte[][]{
                new byte[TEST_FRAME_SIZE_BYTES],
        };

        KinesisVideoProducerStream kinesisVideoProducerStream;
        KinesisVideoFrame frame;

        storageInfo_ = new StorageInfo(0,
                StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM, STORAGE_SIZE_MEGS,
                SPILL_RATIO_PERCENT, STORAGE_PATH);
        deviceInfo_ = new DeviceInfo(DEVICE_VERSION,
                DEVICE_NAME, storageInfo_, NUMBER_OF_STREAMS, null);

        createProducer();
        
        kinesisVideoProducerStream = createTestStream("JavaFuncTest_offlineUploadLimitedBufferDuration",
                StreamInfo.StreamingType.STREAMING_TYPE_OFFLINE, TEST_LATENCY, 
                400L * Time.HUNDREDS_OF_NANOS_IN_A_SECOND); // buffer-duration is 4 seconds

        for(int index = 0; index < TEST_TOTAL_FRAME_COUNT; index++) {

            flags = index % TEST_KEY_FRAME_INTERVAL == 0 ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE;
            frame = new KinesisVideoFrame(index, flags, currentTimeMs, currentTimeMs,
                    TEST_FRAME_DURATION, ByteBuffer.wrap(framesData[index % framesData.length]));
            try {
                kinesisVideoProducerStream.putFrame(frame);
            } catch(ProducerException e) {
                e.printStackTrace();
                fail();
            }
            currentTimeMs += TEST_FRAME_DURATION;
        }
        try {
            Thread.sleep(WAIT_5_SECONDS_FOR_ACKS);
        } catch(InterruptedException e) {
            e.printStackTrace();
            fail();
        }

        log.debug("Stopping the stream: %s", kinesisVideoProducerStream.getStreamName());
        try {
            kinesisVideoProducerStream.stopStreamSync();
        } catch(ProducerException e) {
            e.printStackTrace();
            fail();

        }

        log.debug("Status of frame drop: %b", frameDropped_);
        log.debug("Status of stream error: %d", errorStatus_);

        assertFalse(frameDropped_);
        // frameDropped_ is set to false initially. It can be set to true by droppedFrameReport callback in case there
        // was a frame that was dropped during the test
        assertEquals(errorStatus_, 0x00000000);
        //errorStatus_ is set to 0x00000000 which is STATUS_SUCCESS. It can be set to a different statusCode by
        // streamErrorReport callback in case an error is encountered during the test
        assertTrue(bufferingAckInSequence_);
        //bufferingAckInSequence_ is true initially. It can be set to false by fragmentAckReceived callback in case the
        // (current timestamp - previous timestamp of the ack) > fragment duration
        freeStreams();
    }

    /**
     * This test sets the device storage size to 1 MB(which is less than what is needed by framesData). It is disabled
     * because of timestamp decoding errors
     */
    @Ignore
    @Test
    public void offlineUploadLimitedStorage() {
        final Log log = new Log(Log.SYSTEM_OUT, LogLevel.VERBOSE,
                "ProducerFunctionalityTest/offlineUploadLimitedStorage");
        int flags;
        long currentTimeMs = System.currentTimeMillis() * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
        byte[][] framesData = new byte[][]{
                new byte[TEST_FRAME_SIZE_BYTES * 12]
        };

        KinesisVideoProducerStream kinesisVideoProducerStream;
        KinesisVideoFrame frame;

        storageInfo_ = new StorageInfo(0,
                StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM, 1024 * 1024,
                90, "/tmp");
        deviceInfo_ = new DeviceInfo(0,
                "java-test-application", storageInfo_, NUMBER_OF_STREAMS, null);

        createProducer();

        kinesisVideoProducerStream = createTestStream("JavaFuncTest_offlineUploadLimitedStorage",
                StreamInfo.StreamingType.STREAMING_TYPE_OFFLINE, TEST_LATENCY, TEST_BUFFER_DURATION);

        for(int index = 0; index < TEST_TOTAL_FRAME_COUNT; index++) {

            flags = index % TEST_KEY_FRAME_INTERVAL == 0 ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE;
            frame = new KinesisVideoFrame(index, flags, currentTimeMs, currentTimeMs,
                    TEST_FRAME_DURATION, ByteBuffer.wrap(framesData[index % framesData.length]));
            try {
                kinesisVideoProducerStream.putFrame(frame);
            } catch(ProducerException e) {
                e.printStackTrace();
                fail();
            }
            currentTimeMs += TEST_FRAME_DURATION;
        }

        try {
            Thread.sleep(WAIT_5_SECONDS_FOR_ACKS);
        } catch(InterruptedException e) {
            e.printStackTrace();
            fail();
        }

        log.debug("Stopping the stream: %s", kinesisVideoProducerStream.getStreamName());
        try {
            kinesisVideoProducerStream.stopStreamSync();
        } catch(ProducerException e) {
            e.printStackTrace();
            fail();
        }

        log.debug("Status of frame drop: %b", frameDropped_);
        log.debug("Status of stream error: %d", errorStatus_);

        assertFalse(frameDropped_);
        assertEquals(errorStatus_, 0x00000000);
        assertTrue(bufferingAckInSequence_);

        freeStreams();
    }

    /**
     * This test pauses on the last frame of each clip for pauseSeconds which is less than the timeout
     */
    @Test
    public void intermittentFileUpload() {
        final Log log = new Log(Log.SYSTEM_OUT, LogLevel.VERBOSE,
                "ProducerFunctionalityTest/intermittentFileUpload");
        int flags;
        long currentTimeMs = 0;
        byte[][] framesData = new byte[][]{
                new byte[TEST_FRAME_SIZE_BYTES]
        };
        int clipDurationSeconds = 15;
        int clipCount = 20;
        int framesPerClip = clipDurationSeconds * TEST_FPS;
        int totalFrames = framesPerClip * clipCount;
        int[] pauseBetweenClipSeconds = new int[]{2, 15};

        KinesisVideoProducerStream kinesisVideoProducerStream;
        KinesisVideoFrame frame;

        storageInfo_ = new StorageInfo(0,
                StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM, STORAGE_SIZE_MEGS,
                SPILL_RATIO_PERCENT, STORAGE_PATH);
        deviceInfo_ = new DeviceInfo(DEVICE_VERSION,
                DEVICE_NAME, storageInfo_, NUMBER_OF_STREAMS, null);

        createProducer();

        for(int pauseSeconds: pauseBetweenClipSeconds) {

            previousBufferingAckTimestamp_.clear();
            bufferingAckInSequence_ = true;

            kinesisVideoProducerStream = createTestStream("JavaFuncTest_intermittentFileUpload",
                    StreamInfo.StreamingType.STREAMING_TYPE_OFFLINE, TEST_LATENCY, TEST_BUFFER_DURATION);

            for(int index = 0; index < totalFrames; index++) {

                flags = index % TEST_KEY_FRAME_INTERVAL == 0 ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE;
                frame = new KinesisVideoFrame(index, flags, currentTimeMs, currentTimeMs,
                        TEST_FRAME_DURATION, ByteBuffer.wrap(framesData[index % framesData.length]));
                try {
                    kinesisVideoProducerStream.putFrame(frame);
                } catch(ProducerException e) {
                    e.printStackTrace();
                    fail();
                }
                currentTimeMs += TEST_FRAME_DURATION;;

                if((index + 1) % framesPerClip == 0) { // pause on the last frame of each clip
                    try {
                        Thread.sleep(pauseSeconds * 1000);
                    } catch(InterruptedException e) {
                        e.printStackTrace();
                        fail();
                    }
                }
            }

            try {
                Thread.sleep(WAIT_5_SECONDS_FOR_ACKS);
            } catch(InterruptedException e) {
                e.printStackTrace();
                fail();
            }

            log.debug("Stopping the stream: %s", kinesisVideoProducerStream.getStreamName());
            try {
                kinesisVideoProducerStream.stopStreamSync();
            } catch(ProducerException e) {
                e.printStackTrace();
                fail();
            }

            log.debug("Status of frame drop: %b", frameDropped_);
            log.debug("Status of stream error: %d", errorStatus_);

            assertFalse(frameDropped_);
            assertEquals(errorStatus_, 0x00000000);
            assertTrue(bufferingAckInSequence_);

            freeStreams();
        }
    }

    /**
     * This test sets keyFrameInterval_ to a small value which increases the frequency of key frames. Later, it checks
     * if all the fragments are delivered
     */
    @Ignore
    @Test
    public void highFragmentRateFileUpload() {
        final Log log = new Log(Log.SYSTEM_OUT, LogLevel.VERBOSE,
                "ProducerFunctionalityTest/highFragmentRateFileUpload");
        int flags;
        long currentTimeMs = 0;
        byte[][] framesData = new byte[][]{
                new byte[TEST_FRAME_SIZE_BYTES]
        };

        KinesisVideoProducerStream kinesisVideoProducerStream;
        KinesisVideoFrame frame;

        storageInfo_ = new StorageInfo(0,
                StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM, STORAGE_SIZE_MEGS,
                SPILL_RATIO_PERCENT, STORAGE_PATH);
        deviceInfo_ = new DeviceInfo(DEVICE_VERSION,
                DEVICE_NAME, storageInfo_, NUMBER_OF_STREAMS, null);

        createProducer();
        keyFrameInterval_ = 4; // high key frame interval

        kinesisVideoProducerStream = createTestStream("JavaFuncTest_highFragmentRateFileUpload",
                StreamInfo.StreamingType.STREAMING_TYPE_OFFLINE, TEST_LATENCY,
                TEST_BUFFER_DURATION);

        for(int index = 0; index < TEST_TOTAL_FRAME_COUNT; index++) {

            flags = index % keyFrameInterval_ == 0 ? FRAME_FLAG_KEY_FRAME :
                    FRAME_FLAG_NONE;
            frame = new KinesisVideoFrame(index, flags, currentTimeMs, currentTimeMs,
                    TEST_FRAME_DURATION, ByteBuffer.wrap(framesData[index % framesData.length]));
            try {
                kinesisVideoProducerStream.putFrame(frame);
            } catch(ProducerException e) {
                e.printStackTrace();
                fail();
            }
            currentTimeMs += TEST_FRAME_DURATION;
        }
        try {
            Thread.sleep(WAIT_5_SECONDS_FOR_ACKS);
        } catch(InterruptedException e) {
            e.printStackTrace();
            fail();
        }

        log.debug("Stopping the stream: %s", kinesisVideoProducerStream.getStreamName());
        try {
            kinesisVideoProducerStream.stopStreamSync();
        } catch(ProducerException e) {
            e.printStackTrace();
            fail();
        }

        log.debug("Status of frame drop: %b", frameDropped_);
        log.debug("Status of stream error: %d", errorStatus_);

        assertFalse(frameDropped_);
        assertEquals(errorStatus_, 0x00000000);
        assertTrue(bufferingAckInSequence_); // all fragments should be sent

        freeStreams();
    }

    /**
     * This test is disabled as it is causing timestamp decoding errors
     */
    @Ignore
    @Test
    public void offlineModeTokenRotationBlockOnSpace() {
        final Log log = new Log(Log.SYSTEM_OUT, LogLevel.VERBOSE,
                "ProducerFunctionalityTest/offlineModeTokenRotationBlockOnSpace");
        KinesisVideoProducerStream kinesisVideoProducerStream;
        KinesisVideoFrame frame;

        int flags;
        int testFrameTotalCount = 10 * 1000;
        long currentTimeMs = System.currentTimeMillis() * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
        byte[][] framesData = new byte[][]{
                new byte[TEST_FRAME_SIZE_BYTES]
        };

        storageInfo_ = new StorageInfo(0,
                StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM, 10 * 1024 * 1024,
                90, "/tmp");
        deviceInfo_ = new DeviceInfo(0,
                "java-test-application", storageInfo_, NUMBER_OF_STREAMS, null);

        createProducer();
        kinesisVideoProducerStream = createTestStream("JavaFuncTest_offlineModeTokenRotationBlockOnSpace",
                StreamInfo.StreamingType.STREAMING_TYPE_OFFLINE, TEST_LATENCY, TEST_BUFFER_DURATION);

        for(int index = 0; index < testFrameTotalCount; index++) {

            flags = index % TEST_KEY_FRAME_INTERVAL == 0 ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE;
            frame = new KinesisVideoFrame(index, flags, currentTimeMs, currentTimeMs,
                    TEST_FRAME_DURATION, ByteBuffer.wrap(framesData[index % framesData.length]));
            try {
                kinesisVideoProducerStream.putFrame(frame);
            } catch(ProducerException e) {
                e.printStackTrace();
                fail();
            }
            currentTimeMs += TEST_FRAME_DURATION;
        }
        try {
            Thread.sleep(WAIT_5_SECONDS_FOR_ACKS);
        } catch(InterruptedException e) {
            e.printStackTrace();
            fail();
        }

        log.debug("Stopping the stream: %s", kinesisVideoProducerStream.getStreamName());
        try {
            kinesisVideoProducerStream.stopStreamSync();
        } catch(ProducerException e) {
            e.printStackTrace();
            fail();
        }

        log.debug("Status of frame drop: %b", frameDropped_);
        log.debug("Status of stream error: %d", errorStatus_);

        assertFalse(frameDropped_);
        assertEquals(errorStatus_, 0x00000000);
        assertTrue(bufferingAckInSequence_);

        freeStreams();
    }

    /**
     * This test sets the latency in StreamInfo to 15 seconds. On the 5th keyframe, it sends an EoFR and pauses
     * for 60 seconds which causes the connection to timeout. The EoFR is sent to ensure that we terminate the fragment
     * on the backend which will issue a persistent ACK causing the state machine to not rollback on the next frame
     * produced after the pause. The pause will cause the state machine to change state to a new session.
     * The new session will not roll back as the previous one was closed with a persisted ACK received.
     */
    @Ignore
    @Test
    public void realtimeIntermittentNoLatencyPressureEofr() {
        final Log log = new Log(Log.SYSTEM_OUT, LogLevel.VERBOSE,
                "ProducerFunctionalityTest/realtimeIntermittentNoLatencyPressureEofr");
        KinesisVideoProducerStream kinesisVideoProducerStream;
        KinesisVideoFrame frame;

        int flags;
        int testFrameTotalCount;
        long currentTimeMs = 0;
        byte[][] framesData = new byte[][]{
                new byte[TEST_FRAME_SIZE_BYTES]
        };
        byte[][] eofrData = new byte[][]{ new byte[0] };
        KinesisVideoFrame eofr = new KinesisVideoFrame(0, 8, 0, 0, 0,
                ByteBuffer.wrap(eofrData[0 % eofrData.length]));

        storageInfo_ = new StorageInfo(0,
                StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM, 10 * 1024 * 1024,
                90, "/tmp");
        deviceInfo_ = new DeviceInfo(0,
                "java-test-application", storageInfo_, 10, null);

        createProducer();
        keyFrameInterval_ = 60;
        testFrameTotalCount = 6 * keyFrameInterval_;

        kinesisVideoProducerStream = createTestStream("JavaFuncTest_realtimeIntermittentNoLatencyPressureEofr",
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME,
                1500L * Time.HUNDREDS_OF_NANOS_IN_A_SECOND, TEST_BUFFER_DURATION);

        for(int index = 0; index < testFrameTotalCount; index++) {
            flags = index % keyFrameInterval_ == 0 ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE;

            if(index == 5 * keyFrameInterval_) { // pause on the 5th key frame
                try {
                    kinesisVideoProducerStream.putFrame(eofr);
                } catch(ProducerException e) {
                    e.printStackTrace();
                    fail();
                }
                try {
                    Thread.sleep(60000); // make sure we hit the connection idle timeout of 60 seconds
                } catch(InterruptedException e) {
                    e.printStackTrace();
                    fail();
                }
            }

            frame = new KinesisVideoFrame(index, flags, currentTimeMs, currentTimeMs,
                    TEST_FRAME_DURATION, ByteBuffer.wrap(framesData[index % framesData.length]));
            try {
                kinesisVideoProducerStream.putFrame(frame);
            } catch(ProducerException e) {
                e.printStackTrace();
                fail();
            }
            currentTimeMs += TEST_FRAME_DURATION;

            try {
                Thread.sleep(30);
            } catch(InterruptedException e) {
                e.printStackTrace();
                fail();
            }

        }
        try {
            Thread.sleep(WAIT_5_SECONDS_FOR_ACKS);
        } catch(InterruptedException e) {
            e.printStackTrace();
            fail();
        }

        log.debug("Stopping the stream: %s", kinesisVideoProducerStream.getStreamName());
        try {
            kinesisVideoProducerStream.stopStreamSync();
        } catch(ProducerException e) {
            e.printStackTrace();
            fail();
        }

        log.debug("Status of frame drop: %b", frameDropped_);
        log.debug("Status of stream error: %d", errorStatus_);

        assertFalse(frameDropped_);
        assertEquals(errorStatus_, 0x00000000);
        assertTrue(bufferingAckInSequence_);

        freeStreams();
    }

    /**
     * This test is disabled as Java SDK does not support Auto-intermittent Producer yet
     */
    @Ignore
    @Test
    public void realtimeAutoIntermittentLatencyPressure() {
        final Log log = new Log(Log.SYSTEM_OUT, LogLevel.VERBOSE,
                "ProducerFunctionalityTest/realtimeAutoIntermittentLatencyPressure");
        KinesisVideoProducerStream kinesisVideoProducerStream;

        int totalFrameCount;
        int flags;
        long currentTimeMs;
        long delta = 0;
        byte[][] framesData = new byte[][]{
                new byte[TEST_FRAME_SIZE_BYTES]
        };

        KinesisVideoFrame frame;

        storageInfo_ = new StorageInfo(0,
                StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM, STORAGE_SIZE_MEGS,
                SPILL_RATIO_PERCENT, STORAGE_PATH);
        deviceInfo_ = new DeviceInfo(DEVICE_VERSION,
                DEVICE_NAME, storageInfo_, NUMBER_OF_STREAMS, null);


        createProducer();

        keyFrameInterval_ = 60;
        totalFrameCount = 6 * keyFrameInterval_;
        frameDuration_ = 16 * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;

        kinesisVideoProducerStream = createTestStream("JavaFuncTest_realtimeAutoIntermittentLatencyPressure",
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME, 1500L * Time.HUNDREDS_OF_NANOS_IN_A_SECOND,
                TEST_BUFFER_DURATION);


        for(int index = 0; index < totalFrameCount; index++) {
            flags = index % keyFrameInterval_ == 0 ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE;

            if(index == 5 * keyFrameInterval_) {
                long start = System.currentTimeMillis();
                try {
                    Thread.sleep(60000);
                } catch(InterruptedException e) {
                    e.printStackTrace();
                    fail();
                }
                delta = System.currentTimeMillis() - start;
            }
            currentTimeMs = System.currentTimeMillis() + index * frameDuration_ + delta;

            frame = new KinesisVideoFrame(index, flags, currentTimeMs, currentTimeMs,
                    frameDuration_, ByteBuffer.wrap(framesData[index % framesData.length]));
            try {
                kinesisVideoProducerStream.putFrame(frame);
            } catch(ProducerException e) {
                e.printStackTrace();
                fail();
            }

            try {
                Thread.sleep(frameDuration_ / 10000);
            } catch(InterruptedException e) {
                e.printStackTrace();
                fail();
            }
        }

        try {
            Thread.sleep(WAIT_5_SECONDS_FOR_ACKS);
        } catch(InterruptedException e) {
            e.printStackTrace();
            fail();
        }

        log.debug("Stopping the stream: %s", kinesisVideoProducerStream.getStreamName());
        try {
            kinesisVideoProducerStream.stopStreamSync();
        } catch(ProducerException e) {
            e.printStackTrace();
            fail();
        }

        log.debug("Status of frame drop: %b", frameDropped_);
        log.debug("Status of stream error: %d", errorStatus_);

        assertFalse(frameDropped_);
        assertEquals(errorStatus_, 0x00000000);
        assertTrue(bufferingAckInSequence_);

        freeStreams();
    }
}
