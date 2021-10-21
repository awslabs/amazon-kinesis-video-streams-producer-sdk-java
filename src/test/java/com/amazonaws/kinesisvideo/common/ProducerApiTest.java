package com.amazonaws.kinesisvideo.common;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.kinesisvideo.producer.Time;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.producer.ProducerException;

public class ProducerApiTest extends ProducerTestBase{

    private static final int TEST_STREAM_COUNT = 10;
    private static final int TEST_START_STOP_ITERATION_COUNT = 200;

    @Test
    public void createFreeStream() {
        KinesisVideoProducerStream [] kinesisVideoProducerStreams = new KinesisVideoProducerStream[TEST_STREAM_COUNT];
        createProducer();
        String testStreamName;

        for(int i = 0 ; i < TEST_STREAM_COUNT ; i++) {
            testStreamName = "JavaProducerApiTestStream_createFreeStream" + i;
            kinesisVideoProducerStreams[i] = createTestStream(testStreamName,
                    StreamInfo.StreamingType.STREAMING_TYPE_REALTIME, TEST_LATENCY, TEST_BUFFER_DURATION);
        }
        for(int i = 0 ; i < TEST_STREAM_COUNT ; i++) {
            testStreamName = "JavaProducerApiTestStream_createFreeStream" + i;
            freeTestStream(kinesisVideoProducerStreams[i]);
            kinesisVideoProducerStreams[i] = null;
            kinesisVideoProducerStreams[i] = createTestStream(testStreamName,
                    StreamInfo.StreamingType.STREAMING_TYPE_REALTIME, TEST_LATENCY, TEST_BUFFER_DURATION);
        }
        try {
            freeStreams();
        } catch(Exception e) {
            e.printStackTrace();
            fail();
        }
        for(int i = 0 ; i < TEST_STREAM_COUNT ; i++) {
            testStreamName = "JavaProducerApiTestStream_createFreeStream" + i;
            kinesisVideoProducerStreams[i] = createTestStream(testStreamName,
                    StreamInfo.StreamingType.STREAMING_TYPE_REALTIME, TEST_LATENCY, TEST_BUFFER_DURATION);
        }
    }

    @Test
    public void createProduceStartStopStream() {
        KinesisVideoProducerStream [] kinesisVideoProducerStreams = new KinesisVideoProducerStream[TEST_STREAM_COUNT];
        KinesisVideoFrame frame;
        String testStreamName;

        int flags;
        long currentTimeMs;
        byte[][] framesData = new byte[][]{
                new byte[TEST_FRAME_SIZE_BYTES]
        };

        createProducer();

        fps_ = 25;
        keyFrameInterval_ = 50;
        frameDuration_ = 1000 * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND / fps_;

        for(int i = 0; i < 3; i++) {
            testStreamName = "JavaProducerApiTestStream_createProduceStartStopStream" + i;
            kinesisVideoProducerStreams[i] = createTestStream(testStreamName,
                    StreamInfo.StreamingType.STREAMING_TYPE_REALTIME, TEST_LATENCY, TEST_BUFFER_DURATION);
            for (int index = 0; index < TEST_START_STOP_ITERATION_COUNT; index++) {

                currentTimeMs = System.currentTimeMillis() * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;

                flags = index % keyFrameInterval_ == 0 ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE;
                frame = new KinesisVideoFrame(index++, flags, currentTimeMs, currentTimeMs,
                        frameDuration_, ByteBuffer.wrap(framesData[index % framesData.length]));
                try {
                    kinesisVideoProducerStreams[i].putFrame(frame);
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
                kinesisVideoProducerStreams[i].stopStreamSync();
            } catch(ProducerException e) {
                e.printStackTrace();
                fail();
            }
            assertTrue(stopCalled_);
            freeTestStream(kinesisVideoProducerStreams[i]);
            kinesisVideoProducerStreams[i] = null;
        }
    }

    @Test
    public void createProduceStartStopStreamEndpointCached() {
        KinesisVideoProducerStream [] kinesisVideoProducerStreams = new KinesisVideoProducerStream[TEST_STREAM_COUNT];
        KinesisVideoFrame frame;
        String testStreamName;

        int flags;
        long currentTimeMs;
        byte[][] framesData = new byte[][]{
                new byte[TEST_FRAME_SIZE_BYTES]
        };

        createProducer();

        fps_ = 25;
        keyFrameInterval_ = 50;
        frameDuration_ = 1000 * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND / fps_;

        for(int i = 0; i < 2; i++) {
            testStreamName = "JavaProducerApiTestStream_createProduceStartStopStreamEndpointCached" + i;
            kinesisVideoProducerStreams[i] = createTestStream(testStreamName,
                    StreamInfo.StreamingType.STREAMING_TYPE_REALTIME, TEST_LATENCY, TEST_BUFFER_DURATION);
            cacheStreamingEndpoint(false, testStreamName);
            for (int index = 0; index < 100; index++) {

                currentTimeMs = System.currentTimeMillis() * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;

                flags = index % keyFrameInterval_ == 0 ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE;
                frame = new KinesisVideoFrame(index++, flags, currentTimeMs, currentTimeMs,
                        frameDuration_, ByteBuffer.wrap(framesData[index % framesData.length]));
                try {
                    kinesisVideoProducerStreams[i].putFrame(frame);
                } catch(ProducerException e) {
                    e.printStackTrace();
                    fail();
                }

                try {
                    Thread.sleep(frameDuration_ / 10000); //converting hundreds nanoseconds to milliseconds
                } catch(InterruptedException e) {
                    e.printStackTrace();
                    fail();
                }
            }

            try {
                kinesisVideoProducerStreams[i].stopStreamSync();
            } catch(ProducerException e) {
                e.printStackTrace();
                fail();
            }
            assertTrue(stopCalled_);
            freeTestStream(kinesisVideoProducerStreams[i]);
            kinesisVideoProducerStreams[i] = null;
        }
    }

    @Test
    public void createProduceStartStopStreamAllCached() {
        KinesisVideoProducerStream [] kinesisVideoProducerStreams = new KinesisVideoProducerStream[TEST_STREAM_COUNT];
        KinesisVideoFrame frame;
        String testStreamName;

        int flags;
        long currentTimeMs;
        byte[][] framesData = new byte[][]{
                new byte[TEST_FRAME_SIZE_BYTES]
        };

        createProducer();

        fps_ = 25;
        keyFrameInterval_ = 50;
        frameDuration_ = 1000 * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND / fps_;

        for(int i = 0; i < 2; i++) {
            testStreamName = "JavaProducerApiTestStream_createProduceStartStopStreamAllCached" + i;
            kinesisVideoProducerStreams[i] = createTestStream(testStreamName,
                    StreamInfo.StreamingType.STREAMING_TYPE_REALTIME, TEST_LATENCY, TEST_BUFFER_DURATION);
            cacheStreamingEndpoint(true, testStreamName);
            for (int index = 0; index < 100; index++) {

                currentTimeMs = System.currentTimeMillis() * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;

                flags = index % keyFrameInterval_ == 0 ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE;
                frame = new KinesisVideoFrame(index++, flags, currentTimeMs, currentTimeMs,
                        frameDuration_, ByteBuffer.wrap(framesData[index % framesData.length]));
                try {
                    kinesisVideoProducerStreams[i].putFrame(frame);
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
                kinesisVideoProducerStreams[i].stopStreamSync();
            } catch(ProducerException e) {
                e.printStackTrace();
                fail();
            }
            assertTrue(stopCalled_);
            freeTestStream(kinesisVideoProducerStreams[i]);
            kinesisVideoProducerStreams[i] = null;
        }
    }
}
