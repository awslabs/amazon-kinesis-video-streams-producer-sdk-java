package com.amazonaws.kinesisvideo.common;

import com.amazonaws.kinesisvideo.common.logging.Log;
import com.amazonaws.kinesisvideo.common.logging.LogLevel;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.producer.*;

import org.junit.Ignore;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.*;

public class ProducerFunctionalityTest extends ProducerTestBase{

    @Test
    public void startStopSyncTerminate() {
        KinesisVideoProducerStream kinesisVideoProducerStream;

        storageInfo_ = new StorageInfo(0,
                StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM, STORAGE_SIZE_MEGS,
                SPILL_RATIO_PERCENT, STORAGE_PATH);
        deviceInfo_ = new DeviceInfo(DEVICE_VERSION,
                DEVICE_NAME, storageInfo_, NUMBER_OF_STREAMS, null);

        createProducer();
        kinesisVideoProducerStream = createTestStream("JavaFuncTest_startStopSyncTerminate",
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME, TEST_LATENCY, TEST_BUFFER_DURATION);
        try {
            kinesisVideoProducerStream.stopStreamSync();
        } catch(ProducerException e) {
            e.printStackTrace();
            fail();
        }
        freeStreams();
    }

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
                400L * Time.HUNDREDS_OF_NANOS_IN_A_SECOND); // buffer duration is 4 seconds

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

    @Ignore
    @Test
    public void offlineUploadLimitedStorage() {
        final Log log = new Log(Log.SYSTEM_OUT, LogLevel.VERBOSE,
                "ProducerFunctionalityTest/offlineUploadLimitedStorage");
        int flags;
        long currentTimeMs = System.currentTimeMillis() * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
        byte[][] framesData = new byte[][]{
                new byte[TEST_FRAME_SIZE_BYTES]
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

                if(index > 0 && (index + 1) % framesPerClip == 0) {
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
        keyFrameInterval_ = 4;

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
        assertTrue(bufferingAckInSequence_);

        freeStreams();
    }

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

            if(index == 5 * keyFrameInterval_) {
                try {
                    kinesisVideoProducerStream.putFrame(eofr);
                } catch(ProducerException e) {
                    e.printStackTrace();
                    fail();
                }
                try {
                    Thread.sleep(60000);
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
