package com.amazonaws.kinesisvideo.common;

import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.producer.Time;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.StorageInfo;
import com.amazonaws.kinesisvideo.producer.StreamInfo;

import org.junit.Test;
import static org.junit.Assert.fail;

import java.nio.ByteBuffer;

public class ProducerFunctionalityTest {

    private final int TEST_TOTAL_FRAME_COUNT = 60 * 20;

    @Test
    public void startStopSyncTerminate() {
        ProducerTestBase producerTestBase = new ProducerTestBase();
        KinesisVideoProducerStream kinesisVideoProducerStream;
        producerTestBase.createProducer();
        kinesisVideoProducerStream = producerTestBase.createTestStream("JavaFuncTest_startStopSyncTerminate",
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME, ProducerTestBase.TEST_LATENCY, ProducerTestBase.TEST_BUFFER_DURATION);
        try {
            kinesisVideoProducerStream.stopStreamSync();
        } catch(ProducerException e) {
            e.printStackTrace();
            fail();
        }
        producerTestBase.freeStreams();
    }

    @Test
    public void offlineUploadLimitedBufferDuration() {
        ProducerTestBase producerTestBase = new ProducerTestBase();
        KinesisVideoProducerStream kinesisVideoProducerStream;
        int fps = 25, keyFrameInterval = 50;
        int flags;
        long currentTimeMs = 0;
        long frameDuration = 1000 * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND / fps;
        byte[][] framesData = new byte[][]{
                new byte[ProducerTestBase.MAX_FRAME_SIZE_BYTES_1024],
        };

        KinesisVideoFrame frame;
        producerTestBase.createProducer();
        kinesisVideoProducerStream = producerTestBase.createTestStream("JavaFuncTest_offlineUploadLimitedBufferDuration",
                StreamInfo.StreamingType.STREAMING_TYPE_OFFLINE, ProducerTestBase.TEST_LATENCY, 400L * Time.HUNDREDS_OF_NANOS_IN_A_SECOND);

        for(int index = 0; index < TEST_TOTAL_FRAME_COUNT; index++) {

            flags = index % keyFrameInterval == 0 ? ProducerTestBase.FRAME_FLAG_KEY_FRAME : ProducerTestBase.FRAME_FLAG_NONE;
            frame = new KinesisVideoFrame(index, flags, currentTimeMs, currentTimeMs,
                    frameDuration, ByteBuffer.wrap(framesData[index % framesData.length]));
            try {
                kinesisVideoProducerStream.putFrame(frame);
            } catch(ProducerException e) {
                e.printStackTrace();
                fail();
            }
            currentTimeMs += frameDuration;
        }
        try {
            Thread.sleep(5000);
        } catch(InterruptedException e) {
            e.printStackTrace();
            fail();
        }

        try {
            kinesisVideoProducerStream.stopStreamSync();
        } catch(ProducerException e) {
            e.printStackTrace();
            fail();
        }
        producerTestBase.freeStreams();
    }

    @Test
    public void offlineUploadLimitedStorage() {
        ProducerTestBase producerTestBase = new ProducerTestBase();
        KinesisVideoProducerStream kinesisVideoProducerStream;
        int fps = 25;
        int keyFrameInterval = 50;
        int flags;
        long currentTimeMs = 0;
        long frameDuration = 1000 * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND / fps;
        byte[][] framesData = new byte[][]{
                new byte[12500]
        };

        producerTestBase.setStorageInfo(0,
                StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM, 1024 * 1024,
                90, "/tmp");
        producerTestBase.setDeviceInfo(0,
                "java-test-application", producerTestBase.getStorageInfo(), 10, null);

        KinesisVideoFrame frame;
        producerTestBase.createProducer();
        kinesisVideoProducerStream = producerTestBase.createTestStream("JavaFuncTest_offlineUploadLimitedStorage",
                StreamInfo.StreamingType.STREAMING_TYPE_OFFLINE, ProducerTestBase.TEST_LATENCY, ProducerTestBase.TEST_BUFFER_DURATION);

        for(int index = 0; index < TEST_TOTAL_FRAME_COUNT; index++) {

            flags = index % keyFrameInterval == 0 ? ProducerTestBase.FRAME_FLAG_KEY_FRAME : ProducerTestBase.FRAME_FLAG_NONE;
            frame = new KinesisVideoFrame(index, flags, currentTimeMs, currentTimeMs,
                    frameDuration, ByteBuffer.wrap(framesData[index % framesData.length]));
            try {
                kinesisVideoProducerStream.putFrame(frame);
            } catch(ProducerException e) {
                e.printStackTrace();
                fail();
            }
            currentTimeMs += frameDuration;
        }
        try {
            Thread.sleep(5000);
        } catch(InterruptedException e) {
            e.printStackTrace();
            fail();
        }

        try {
            kinesisVideoProducerStream.stopStreamSync();
        } catch(ProducerException e) {
            e.printStackTrace();
            fail();
        }
        producerTestBase.freeStreams();
    }

    @Test
    public void intermittentFileUpload() {
        ProducerTestBase producerTestBase = new ProducerTestBase();
        KinesisVideoProducerStream kinesisVideoProducerStream;

        int fps = 25;
        int keyFrameInterval = 50;
        int flags;
        long currentTimeMs = 0;
        long frameDuration = 1000 * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND / fps;
        byte[][] framesData = new byte[][]{
                new byte[ProducerTestBase.MAX_FRAME_SIZE_BYTES_1024]
        };

        int clipDurationSeconds = 15;
        int clipCount = 20;
        int framesPerClip = clipDurationSeconds * fps;
        int totalFrames = framesPerClip * clipCount;
        int[] pauseBetweenclipSeconds = new int[]{2, 15};

        producerTestBase.createProducer();

        for(int pauseSeconds: pauseBetweenclipSeconds) {
            KinesisVideoFrame frame;
            kinesisVideoProducerStream = producerTestBase.createTestStream("JavaFuncTest_intermittentFileUpload",
                    StreamInfo.StreamingType.STREAMING_TYPE_OFFLINE, ProducerTestBase.TEST_LATENCY, ProducerTestBase.TEST_BUFFER_DURATION);

            for(int index = 0; index < totalFrames; index++) {

                flags = index % keyFrameInterval == 0 ? ProducerTestBase.FRAME_FLAG_KEY_FRAME : ProducerTestBase.FRAME_FLAG_NONE;
                frame = new KinesisVideoFrame(index, flags, currentTimeMs, currentTimeMs,
                        frameDuration, ByteBuffer.wrap(framesData[index % framesData.length]));
                try {
                    kinesisVideoProducerStream.putFrame(frame);
                } catch(ProducerException e) {
                    e.printStackTrace();
                    fail();
                }
                currentTimeMs += frameDuration;

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
                Thread.sleep(5000);
            } catch(InterruptedException e) {
                e.printStackTrace();
                fail();
            }

            try {
                kinesisVideoProducerStream.stopStreamSync();
            } catch(ProducerException e) {
                e.printStackTrace();
                fail();
            }
            producerTestBase.freeStreams();
        }
    }

    @Test
    public void offlineModeTokenRotationBlockOnSpace() {
        ProducerTestBase producerTestBase = new ProducerTestBase();
        KinesisVideoProducerStream kinesisVideoProducerStream;
        int fps = 25, keyFrameInterval = 50, flags;
        int testFrameTotalCount = 10 * 1000;
        long currentTimeMs = 0;
        long frameDuration = 1000 * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND / fps;
        byte[][] framesData = new byte[][]{
                new byte[ProducerTestBase.MAX_FRAME_SIZE_BYTES_1024]
        };

        producerTestBase.setStorageInfo(0,
                StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM, 10 * 1024 * 1024,
                90, "/tmp");
        producerTestBase.setDeviceInfo(0,
                "java-test-application", producerTestBase.getStorageInfo(), 10, null);

        KinesisVideoFrame frame;
        producerTestBase.createProducer();
        kinesisVideoProducerStream = producerTestBase.createTestStream("JavaFuncTest_offlineModeTokenRotationBlockOnSpace",
                StreamInfo.StreamingType.STREAMING_TYPE_OFFLINE, ProducerTestBase.TEST_LATENCY, ProducerTestBase.TEST_BUFFER_DURATION);

        for(int index = 0; index < testFrameTotalCount; index++) {

            flags = index % keyFrameInterval == 0 ? ProducerTestBase.FRAME_FLAG_KEY_FRAME : ProducerTestBase.FRAME_FLAG_NONE;
            frame = new KinesisVideoFrame(index, flags, currentTimeMs, currentTimeMs,
                    frameDuration, ByteBuffer.wrap(framesData[index % framesData.length]));
            try {
                kinesisVideoProducerStream.putFrame(frame);
            } catch(ProducerException e) {
                e.printStackTrace();
                fail();
            }
            currentTimeMs += frameDuration;
        }
        try {
            Thread.sleep(5000);
        } catch(InterruptedException e) {
            e.printStackTrace();
            fail();
        }

        try {
            kinesisVideoProducerStream.stopStreamSync();
        } catch(ProducerException e) {
            e.printStackTrace();
            fail();
        }
        producerTestBase.freeStreams();
    }

    @Test
    public void realtimeIntermittentNoLatencyPressureEofr() {
        ProducerTestBase producerTestBase = new ProducerTestBase();
        KinesisVideoProducerStream kinesisVideoProducerStream;
        int fps = 25, keyFrameInterval = 60, flags;
        int testFrameTotalCount = 6 * keyFrameInterval;
        long currentTimeMs = 0;
        long frameDuration = 1000 * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
        byte[][] framesData = new byte[][]{
                new byte[ProducerTestBase.MAX_FRAME_SIZE_BYTES_1024]
        };
        byte[][] eofrData = new byte[][]{ new byte[0] };

        producerTestBase.setStorageInfo(0,
                StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM, 10 * 1024 * 1024,
                90, "/tmp");
        producerTestBase.setDeviceInfo(0,
                "java-test-application", producerTestBase.getStorageInfo(), 10, null);

        KinesisVideoFrame frame;
        KinesisVideoFrame eofr = new KinesisVideoFrame(0, 8, 0, 0, 0, ByteBuffer.wrap(eofrData[0 % eofrData.length]));
        producerTestBase.createProducer();
        kinesisVideoProducerStream = producerTestBase.createTestStream("JavaFuncTest_realtimeIntermittentNoLatencyPressureEofr",
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME, 1500L * Time.HUNDREDS_OF_NANOS_IN_A_SECOND, ProducerTestBase.TEST_BUFFER_DURATION);

        for(int index = 0; index < testFrameTotalCount; index++) {
            flags = index % keyFrameInterval == 0 ? ProducerTestBase.FRAME_FLAG_KEY_FRAME : ProducerTestBase.FRAME_FLAG_NONE;

            if(index == 5 * keyFrameInterval) {
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
                    frameDuration, ByteBuffer.wrap(framesData[index % framesData.length]));
            try {
                kinesisVideoProducerStream.putFrame(frame);
            } catch(ProducerException e) {
                e.printStackTrace();
                fail();
            }
            currentTimeMs += frameDuration;

            try {
                Thread.sleep(30);
            } catch(InterruptedException e) {
                e.printStackTrace();
                fail();
            }

        }
        try {
            Thread.sleep(5000);
        } catch(InterruptedException e) {
            e.printStackTrace();
            fail();
        }

        try {
            kinesisVideoProducerStream.stopStreamSync();
        } catch(ProducerException e) {
            e.printStackTrace();
            fail();
        }
        producerTestBase.freeStreams();
    }

    @Test
    public void highFragmentRateFileUpload() {
        ProducerTestBase producerTestBase = new ProducerTestBase();
        KinesisVideoProducerStream kinesisVideoProducerStream;
        int fps = 25;
        int keyFrameInterval = 4;
        int flags;
        long currentTimeMs = 0;
        long frameDuration = 1000 * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND / fps;
        byte[][] framesData = new byte[][]{
                new byte[ProducerTestBase.MAX_FRAME_SIZE_BYTES_1024]
        };

        KinesisVideoFrame frame;
        producerTestBase.createProducer();
        kinesisVideoProducerStream = producerTestBase.createTestStream("JavaFuncTest_highFragmentRateFileUpload",
                StreamInfo.StreamingType.STREAMING_TYPE_OFFLINE, ProducerTestBase.TEST_LATENCY, ProducerTestBase.TEST_BUFFER_DURATION);

        for(int index = 0; index < TEST_TOTAL_FRAME_COUNT; index++) {
           
            flags = index % keyFrameInterval == 0 ? ProducerTestBase.FRAME_FLAG_KEY_FRAME : ProducerTestBase.FRAME_FLAG_NONE;
            frame = new KinesisVideoFrame(index, flags, currentTimeMs, currentTimeMs,
                    frameDuration, ByteBuffer.wrap(framesData[index % framesData.length]));
            try {
                kinesisVideoProducerStream.putFrame(frame);
            } catch(ProducerException e) {
                e.printStackTrace();
                fail();
            }
            currentTimeMs += frameDuration;
        }
        try {
            Thread.sleep(5000);
        } catch(InterruptedException e) {
            e.printStackTrace();
            fail();
        }

        try {
            kinesisVideoProducerStream.stopStreamSync();
        } catch(ProducerException e) {
            e.printStackTrace();
            fail();
        }
        producerTestBase.freeStreams();
    }

    @Test
    public void realtimeIntermittentLatencyPressure() {
        ProducerTestBase producerTestBase = new ProducerTestBase();
        KinesisVideoProducerStream kinesisVideoProducerStream;
        int keyFrameInterval = 60, flags;
        long currentTimeMs;
        long delta = 0;
        long frameDuration = 16 * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
        byte[][] framesData = new byte[][]{
                new byte[ProducerTestBase.MAX_FRAME_SIZE_BYTES_1024]
        };

        KinesisVideoFrame frame;

        producerTestBase.createProducer();
        kinesisVideoProducerStream = producerTestBase.createTestStream("JavaFuncTest_realtimeIntermittentLatencyPressure",
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME, 1500L * Time.HUNDREDS_OF_NANOS_IN_A_SECOND, ProducerTestBase.TEST_BUFFER_DURATION);

        for(int index = 0; index < TEST_TOTAL_FRAME_COUNT; index++) {
            flags = index % keyFrameInterval == 0 ? ProducerTestBase.FRAME_FLAG_KEY_FRAME : ProducerTestBase.FRAME_FLAG_NONE;

            if(index == 5 * keyFrameInterval) {
                long start = System.currentTimeMillis();
                try {
                    Thread.sleep(60000);
                } catch(InterruptedException e) {
                    e.printStackTrace();
                    fail();
                }
                delta = System.currentTimeMillis() - start;
            }
            currentTimeMs = System.currentTimeMillis() + index * frameDuration + delta;

            frame = new KinesisVideoFrame(index, flags, currentTimeMs, currentTimeMs,
                    frameDuration, ByteBuffer.wrap(framesData[index % framesData.length]));
            try {
                kinesisVideoProducerStream.putFrame(frame);
            } catch(ProducerException e) {
                e.printStackTrace();
                fail();
            }

            try {
                Thread.sleep(frameDuration / 10000);
            } catch(InterruptedException e) {
                e.printStackTrace();
                fail();
            }
        }

        try {
            Thread.sleep(5000);
        } catch(InterruptedException e) {
            e.printStackTrace();
            fail();
        }

        try {
            kinesisVideoProducerStream.stopStreamSync();
        } catch(ProducerException e) {
            e.printStackTrace();
            fail();
        }
        producerTestBase.freeStreams();
    }

}
