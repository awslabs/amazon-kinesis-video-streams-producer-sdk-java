package com.amazonaws.kinesisvideo.common;

import java.nio.ByteBuffer;

import static org.junit.Assert.fail;
import org.junit.Test;

import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.kinesisvideo.producer.Time;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.producer.ProducerException;

public class ProducerApiTest {

    private static final int TEST_STREAM_COUNT = 10;
    private static final int TEST_START_STOP_ITERATION_COUNT = 200;

    @Test
    public void createFreeStream() {
        ProducerTestBase producerTestBase = new ProducerTestBase();
        KinesisVideoProducerStream [] kinesisVideoProducerStreams = new KinesisVideoProducerStream[TEST_STREAM_COUNT];
        producerTestBase.createProducer();
        String testStreamName;
        for(int i = 0 ; i < TEST_STREAM_COUNT ; i++) {
            testStreamName = "JavaProducerApiTestStream_createFreeStream" + i;
            kinesisVideoProducerStreams[i] = producerTestBase.createTestStream(testStreamName,
                    StreamInfo.StreamingType.STREAMING_TYPE_REALTIME, ProducerTestBase.TEST_LATENCY, ProducerTestBase.TEST_BUFFER_DURATION);
        }
        for(int i = 0 ; i < TEST_STREAM_COUNT ; i++) {
            testStreamName = "JavaProducerApiTestStream_createFreeStream" + i;
            producerTestBase.freeTestStream(kinesisVideoProducerStreams[i]);
            kinesisVideoProducerStreams[i] = null;
            kinesisVideoProducerStreams[i] = producerTestBase.createTestStream(testStreamName,
                    StreamInfo.StreamingType.STREAMING_TYPE_REALTIME, ProducerTestBase.TEST_LATENCY, ProducerTestBase.TEST_BUFFER_DURATION);
        }
        try {
            producerTestBase.freeStreams();
        } catch(Exception e) {
            e.printStackTrace();
            fail();
        }
        for(int i = 0 ; i < TEST_STREAM_COUNT ; i++) {
            testStreamName = "JavaProducerApiTestStream_createFreeStream" + i;
            kinesisVideoProducerStreams[i] = producerTestBase.createTestStream(testStreamName,
                    StreamInfo.StreamingType.STREAMING_TYPE_REALTIME, ProducerTestBase.TEST_LATENCY, ProducerTestBase.TEST_BUFFER_DURATION);
        }
    }

    @Test
    public void createProduceStartStopStream() {
        ProducerTestBase producerTestBase = new ProducerTestBase();
        KinesisVideoProducerStream [] kinesisVideoProducerStreams = new KinesisVideoProducerStream[TEST_STREAM_COUNT];
        int fps = 25, keyFrameInterval = 50, flags;
        long currentTimeMs, frameDuration = 1000 * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND / fps;
        byte[][] framesData = new byte[][]{
                new byte[ProducerTestBase.MAX_FRAME_SIZE_BYTES_1024]
        };

        KinesisVideoFrame frame;
        String testStreamName;

        producerTestBase.createProducer();

        for(int i = 0; i < 3; i++) {
            testStreamName = "JavaProducerApiTestStream_createProduceStartStopStream" + i;
            kinesisVideoProducerStreams[i] = producerTestBase.createTestStream(testStreamName,
                    StreamInfo.StreamingType.STREAMING_TYPE_REALTIME, ProducerTestBase.TEST_LATENCY, ProducerTestBase.TEST_BUFFER_DURATION);
            for (int index = 0; index < TEST_START_STOP_ITERATION_COUNT; index++) {

                currentTimeMs = System.currentTimeMillis() * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;

                flags = index % keyFrameInterval == 0 ? ProducerTestBase.FRAME_FLAG_KEY_FRAME : ProducerTestBase.FRAME_FLAG_NONE;
                frame = new KinesisVideoFrame(index++, flags, currentTimeMs, currentTimeMs,
                        frameDuration, ByteBuffer.wrap(framesData[index % framesData.length]));
                try {
                    kinesisVideoProducerStreams[i].putFrame(frame);
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
                kinesisVideoProducerStreams[i].stopStreamSync();
            } catch(ProducerException e) {
                e.printStackTrace();
                fail();
            }
            producerTestBase.freeTestStream(kinesisVideoProducerStreams[i]);
            kinesisVideoProducerStreams[i] = null;
        }
    }

    @Test
    public void createProduceStartStopStreamEndpointCached() {
        ProducerTestBase producerTestBase = new ProducerTestBase();
        KinesisVideoProducerStream [] kinesisVideoProducerStreams = new KinesisVideoProducerStream[TEST_STREAM_COUNT];
        int fps = 25, keyFrameInterval = 50, flags;
        long currentTimeMs, frameDuration = 1000 *  Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND / fps;
        byte[][] framesData = new byte[][]{
                new byte[ProducerTestBase.MAX_FRAME_SIZE_BYTES_1024]
        };

        KinesisVideoFrame frame;
        String testStreamName;

        producerTestBase.createProducer();

        for(int i = 0; i < 2; i++) {
            testStreamName = "JavaProducerApiTestStream_createProduceStartStopStreamEndpointCached" + i;
            kinesisVideoProducerStreams[i] = producerTestBase.createTestStream(testStreamName,
                    StreamInfo.StreamingType.STREAMING_TYPE_REALTIME, ProducerTestBase.TEST_LATENCY, ProducerTestBase.TEST_BUFFER_DURATION);
            producerTestBase.cacheStreamingEndpoint(false, testStreamName);
            for (int index = 0; index < 100; index++) {

                currentTimeMs = System.currentTimeMillis() * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;

                flags = index % keyFrameInterval == 0 ? ProducerTestBase.FRAME_FLAG_KEY_FRAME : ProducerTestBase.FRAME_FLAG_NONE;
                frame = new KinesisVideoFrame(index++, flags, currentTimeMs, currentTimeMs,
                        frameDuration, ByteBuffer.wrap(framesData[index % framesData.length]));
                try {
                    kinesisVideoProducerStreams[i].putFrame(frame);
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
                kinesisVideoProducerStreams[i].stopStreamSync();
            } catch(ProducerException e) {
                e.printStackTrace();
                fail();
            }
            producerTestBase.freeTestStream(kinesisVideoProducerStreams[i]);
            kinesisVideoProducerStreams[i] = null;
        }
    }

    @Test
    public void createProduceStartStopStreamAllCached() {
        ProducerTestBase producerTestBase = new ProducerTestBase();
        KinesisVideoProducerStream [] kinesisVideoProducerStreams = new KinesisVideoProducerStream[TEST_STREAM_COUNT];
        int fps = 25, keyFrameInterval = 50, flags;
        long currentTimeMs, frameDuration = 1000 *  Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND / fps;
        byte[][] framesData = new byte[][]{
                new byte[ProducerTestBase.MAX_FRAME_SIZE_BYTES_1024]
        };

        KinesisVideoFrame frame;
        String testStreamName;

        producerTestBase.createProducer();

        for(int i = 0; i < 2; i++) {
            testStreamName = "JavaProducerApiTestStream_createProduceStartStopStreamAllCached" + i;
            kinesisVideoProducerStreams[i] = producerTestBase.createTestStream(testStreamName,
                    StreamInfo.StreamingType.STREAMING_TYPE_REALTIME, ProducerTestBase.TEST_LATENCY, ProducerTestBase.TEST_BUFFER_DURATION);
            producerTestBase.cacheStreamingEndpoint(true, testStreamName);
            for (int index = 0; index < 100; index++) {

                currentTimeMs = System.currentTimeMillis() * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;

                flags = index % keyFrameInterval == 0 ? ProducerTestBase.FRAME_FLAG_KEY_FRAME : ProducerTestBase.FRAME_FLAG_NONE;
                frame = new KinesisVideoFrame(index++, flags, currentTimeMs, currentTimeMs,
                        frameDuration, ByteBuffer.wrap(framesData[index % framesData.length]));
                try {
                    kinesisVideoProducerStreams[i].putFrame(frame);
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
                kinesisVideoProducerStreams[i].stopStreamSync();
            } catch(ProducerException e) {
                e.printStackTrace();
                fail();
            }
            producerTestBase.freeTestStream(kinesisVideoProducerStreams[i]);
            kinesisVideoProducerStreams[i] = null;
        }
    }
}
