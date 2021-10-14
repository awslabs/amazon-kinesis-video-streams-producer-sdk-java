package com.amazonaws.kinesisvideo.common;

import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_BUFFER_DURATION;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.MAX_LATENCY_ZERO;
import static org.junit.Assert.*;

import com.amazonaws.kinesisvideo.producer.StreamInfo;
import org.junit.Test;

import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.producer.ProducerException;

import java.nio.ByteBuffer;


public class ProducerApiTest {

    private static final int TEST_STREAM_COUNT = 10;
    private static final int TEST_START_STOP_ITERATION_COUNT = 200;

    private static final int MAX_FRAME_SIZE_BYTES_1024 = 1024;
    private static final long HUNDREDS_OF_NANOS_IN_A_MILLISECOND = 10000;
    private static final int FRAME_FLAG_KEY_FRAME = 1;
    private static final int FRAME_FLAG_NONE = 0;

    private KinesisVideoProducerStream [] kinesisVideoProducerStreams = new KinesisVideoProducerStream[TEST_STREAM_COUNT];

    private ProducerTestBase producerTestBase = new ProducerTestBase();

    @Test
    public void createFreeStream() {
        producerTestBase.createProducer(false);
        String testStreamName;
        for(int i = 0 ; i < TEST_STREAM_COUNT ; i++) {
            testStreamName = "JavaProducerApiTestStream_createFreeStream" + i;
            kinesisVideoProducerStreams[i] = producerTestBase.createTestStream(testStreamName,
                    StreamInfo.StreamingType.STREAMING_TYPE_REALTIME, MAX_LATENCY_ZERO, DEFAULT_BUFFER_DURATION);
        }
        for(int i = 0 ; i < TEST_STREAM_COUNT ; i++) {
            testStreamName = "JavaProducerApiTestStream_createFreeStream" + i;
            producerTestBase.freeTestStream(kinesisVideoProducerStreams[i]);
            kinesisVideoProducerStreams[i] = null;
            kinesisVideoProducerStreams[i] = producerTestBase.createTestStream(testStreamName,
                    StreamInfo.StreamingType.STREAMING_TYPE_REALTIME, MAX_LATENCY_ZERO, DEFAULT_BUFFER_DURATION);
        }
        try {
            producerTestBase.freeStreams();
        } catch(Exception e) {
            assertTrue(false);
        }
        for(int i = 0 ; i < TEST_STREAM_COUNT ; i++) {
            testStreamName = "JavaProducerApiTestStream_createFreeStream" + i;
            kinesisVideoProducerStreams[i] = producerTestBase.createTestStream(testStreamName,
                    StreamInfo.StreamingType.STREAMING_TYPE_REALTIME, MAX_LATENCY_ZERO, DEFAULT_BUFFER_DURATION);
        }
    }

    @Test
    public void createProduceStartStopStream() {
        int fps = 25, keyFrameInterval = 50, flags;
        long currentTimeMs, decodingTs, presentationTs, frameDuration = 1000 * HUNDREDS_OF_NANOS_IN_A_MILLISECOND / fps;
        byte[][] framesData = new byte[][]{
                new byte[MAX_FRAME_SIZE_BYTES_1024],
                new byte[MAX_FRAME_SIZE_BYTES_1024],
                new byte[MAX_FRAME_SIZE_BYTES_1024],
                new byte[MAX_FRAME_SIZE_BYTES_1024],
                new byte[MAX_FRAME_SIZE_BYTES_1024],
                new byte[MAX_FRAME_SIZE_BYTES_1024]
        };

        KinesisVideoFrame frame;
        String testStreamName;

        producerTestBase.createProducer(false);

        for(int i = 0; i < 3; i++) {
            testStreamName = "JavaProducerApiTestStream_createProduceStartStopStream" + i;
            kinesisVideoProducerStreams[i] = producerTestBase.createTestStream(testStreamName,
                    StreamInfo.StreamingType.STREAMING_TYPE_REALTIME, MAX_LATENCY_ZERO, DEFAULT_BUFFER_DURATION);
            for (int index = 0; index < 10; index++) {

                currentTimeMs = System.currentTimeMillis();
                decodingTs = currentTimeMs * HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
                presentationTs = currentTimeMs * HUNDREDS_OF_NANOS_IN_A_MILLISECOND;

                flags = index % keyFrameInterval == 0 ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE;
                frame = new KinesisVideoFrame(index++, flags, decodingTs, presentationTs,
                        frameDuration, ByteBuffer.wrap(framesData[index % framesData.length]));
                try {
                    kinesisVideoProducerStreams[i].putFrame(frame);
                } catch(ProducerException e) {
                    assertTrue(false);
                }

                try {
                    //Thread.sleep(frameDuration);
                    Thread.sleep(1000);
                } catch(InterruptedException e) {
                    e.printStackTrace();
                }
            }

            try {
                kinesisVideoProducerStreams[i].stopStreamSync();
            } catch(ProducerException e) {
                assertTrue(false);
            }
            producerTestBase.freeTestStream(kinesisVideoProducerStreams[i]);
            kinesisVideoProducerStreams[i] = null;
        }
    }

    @Test
    public void createProduceStartStopStreamEndpointCached() {
        int fps = 25, keyFrameInterval = 50, flags;
        long currentTimeMs, decodingTs, presentationTs, frameDuration = 1000 * HUNDREDS_OF_NANOS_IN_A_MILLISECOND / fps;
        byte[][] framesData = new byte[][]{
                new byte[MAX_FRAME_SIZE_BYTES_1024],
                new byte[MAX_FRAME_SIZE_BYTES_1024],
                new byte[MAX_FRAME_SIZE_BYTES_1024],
                new byte[MAX_FRAME_SIZE_BYTES_1024],
                new byte[MAX_FRAME_SIZE_BYTES_1024],
                new byte[MAX_FRAME_SIZE_BYTES_1024]
        };

        KinesisVideoFrame frame;
        String testStreamName;

        producerTestBase.createProducer(true);

        for(int i = 0; i < 2; i++) {
            testStreamName = "JavaProducerApiTestStream_createProduceStartStopStreamEndpointCached" + i;
            producerTestBase.cacheStreamingEndpoint(testStreamName);
            kinesisVideoProducerStreams[i] = producerTestBase.createTestStream(testStreamName,
                    StreamInfo.StreamingType.STREAMING_TYPE_REALTIME, MAX_LATENCY_ZERO, DEFAULT_BUFFER_DURATION);
            producerTestBase.cacheStreamingEndpoint(testStreamName);
            for (int index = 0; index < 100; index++) {

                currentTimeMs = System.currentTimeMillis();
                decodingTs = currentTimeMs * HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
                presentationTs = currentTimeMs * HUNDREDS_OF_NANOS_IN_A_MILLISECOND;

                flags = index % keyFrameInterval == 0 ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE;
                frame = new KinesisVideoFrame(index++, flags, decodingTs, presentationTs,
                        frameDuration, ByteBuffer.wrap(framesData[index % framesData.length]));
                try {
                    kinesisVideoProducerStreams[i].putFrame(frame);
                } catch(ProducerException e) {
                    assertTrue(false);
                }

                try {
                    //Thread.sleep(frameDuration);
                    Thread.sleep(1000);
                } catch(InterruptedException e) {
                    e.printStackTrace();
                }
            }

            try {
                kinesisVideoProducerStreams[i].stopStreamSync();
            } catch(ProducerException e) {
                assertTrue(false);
            }
            producerTestBase.freeTestStream(kinesisVideoProducerStreams[i]);
            kinesisVideoProducerStreams[i] = null;
        }
    }
}
