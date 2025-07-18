package com.amazonaws.kinesisvideo.producer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.amazonaws.kinesisvideo.producer.FrameFlags.FRAME_FLAG_KEY_FRAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class KinesisVideoFrameTest {

    private static final Logger log = LogManager.getLogger(KinesisVideoFrameTest.class);

    private static final int FRAME_INDEX = 42;
    private static final int FRAME_FLAGS = FRAME_FLAG_KEY_FRAME;
    private static final long DECODING_TS = 1000L;
    private static final long PRESENTATION_TS = 1005L;
    private static final long DURATION = 100L;
    private static final long TRACK_ID = 1L;
    private static final int BUFFER_SIZE = 1024;

    private KinesisVideoFrame createTestFrameFromBuffer(final ByteBuffer data) {
        return new KinesisVideoFrame(
                FRAME_INDEX,
                FRAME_FLAGS,
                DECODING_TS,
                PRESENTATION_TS,
                DURATION,
                data,
                TRACK_ID
        );
    }

    @Test
    public void whenFrameIsCreated_thenGettersReturnCorrectValues() {
        final byte[] data = new byte[BUFFER_SIZE];
        final ByteBuffer testData = ByteBuffer.wrap(data);

        final KinesisVideoFrame frame = createTestFrameFromBuffer(testData);

        assertEquals(FRAME_INDEX, frame.getIndex());
        assertEquals(FRAME_FLAGS, frame.getFlags());
        assertEquals(DECODING_TS, frame.getDecodingTs());
        assertEquals(PRESENTATION_TS, frame.getPresentationTs());
        assertEquals(DURATION, frame.getDuration());
        assertEquals(TRACK_ID, frame.getTrackId());
        assertEquals(BUFFER_SIZE, frame.getSize());
        assertEquals(KinesisVideoFrame.FRAME_CURRENT_VERSION, frame.getVersion());

        final ByteBuffer receivedData = frame.getData();
        for (int i = 0; i < BUFFER_SIZE; i++) {
            assertEquals(data[i], receivedData.get(i));
        }
    }

    @Test
    // Verify immutability
    public void whenOriginalDataIsModified_thenFrameDataRemainsUnchanged() {
        // Create a test buffer with backing array
        final byte[] originalArray = new byte[BUFFER_SIZE];
        for (int i = 0; i < BUFFER_SIZE; i++) {
            originalArray[i] = (byte) i;
        }
        final ByteBuffer testData = ByteBuffer.wrap(originalArray);

        final KinesisVideoFrame frame = createTestFrameFromBuffer(testData);

        // Get the data and verify it matches the original
        final ByteBuffer frameData = frame.getData();
        assertEquals(BUFFER_SIZE, frameData.remaining());
        for (int i = 0; i < BUFFER_SIZE; i++) {
            assertEquals((byte) i, frameData.get());
        }

        // Modify the original array
        for (int i = 0; i < BUFFER_SIZE; i++) {
            originalArray[i] = (byte) (i + 100);
        }

        // Get the data again and verify it's unchanged
        final ByteBuffer frameData2 = frame.getData();
        assertEquals(BUFFER_SIZE, frameData2.remaining());
        for (int i = 0; i < BUFFER_SIZE; i++) {
            assertEquals((byte) i, frameData2.get());
        }
    }

    @Test(expected = ReadOnlyBufferException.class)
    public void whenModifyingReturnedBuffer_thenReadOnlyExceptionIsThrown() {
        final ByteBuffer testData = ByteBuffer.wrap(new byte[]{1, 2, 3, 4,});

        final KinesisVideoFrame frame = createTestFrameFromBuffer(testData);

        final ByteBuffer frameData = frame.getData();
        assertTrue(frameData.isReadOnly());

        // Attempt to modify the buffer should throw ReadOnlyBufferException
        frameData.put(0, (byte) 99);
        fail("Expected ReadOnlyBufferException was not thrown");
    }

    @Test
    public void whenMultipleThreadsAccessData_thenNoDataCorruptionOccurs() throws InterruptedException {
        final ByteBuffer testData = ByteBuffer.allocate(BUFFER_SIZE);
        for (int i = 0; i < BUFFER_SIZE; i++) {
            testData.put((byte) i);
        }
        testData.flip();

        final KinesisVideoFrame frame = createTestFrameFromBuffer(testData);

        // Thead parameters
        final int threadCount = 50;
        final int iterationsPerThread = 1000;

        // Using a latch to ensure all threads start at roughly the same time
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch completionLatch = new CountDownLatch(threadCount);
        final AtomicBoolean testFailed = new AtomicBoolean(false);

        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    // Wait for the signal to start
                    startLatch.await();

                    // Each thread gets the data and verifies it multiple times
                    for (int i = 0; i < iterationsPerThread; i++) {
                        final ByteBuffer data = frame.getData();

                        // Verify the data
                        assertEquals(BUFFER_SIZE, data.remaining());

                        // Read and verify some bytes
                        for (int j = 0; j < BUFFER_SIZE; j++) {
                            if (data.get() != (byte) j) {
                                testFailed.set(true);
                                break;
                            }
                        }

                        // Modify the position and limit to ensure thread isolation
                        data.position(0);
                        data.limit(data.capacity() / 2);
                    }
                } catch (final Throwable thr) {
                    log.error("Encountered an issue in a thread!", thr);
                    testFailed.set(true);
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Start all threads
        startLatch.countDown();

        // Wait for all threads to complete
        assertTrue("Threads did not complete in time", completionLatch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        // Check if any thread reported a failure
        assertFalse("Thread safety test failed, check the logs for more information", testFailed.get());
    }

    @Test
    public void whenPositionChangedInOneBuffer_thenOtherBuffersAreUnaffected() {
        final ByteBuffer testData = ByteBuffer.allocateDirect(BUFFER_SIZE);

        final KinesisVideoFrame frame = createTestFrameFromBuffer(testData);

        // Get two buffers from the same frame
        final ByteBuffer buffer1 = frame.getData();
        final ByteBuffer buffer2 = frame.getData();

        // Modify position of first buffer
        buffer1.position(2);

        // Verify second buffer is unaffected
        assertEquals(0, buffer2.position());
        assertEquals(BUFFER_SIZE, buffer2.remaining());
    }

    @Test(expected = NullPointerException.class)
    @SuppressWarnings("ConstantConditions") // Deliberately passing null to test exception
    public void whenNullDataProvided_thenNullPointerExceptionIsThrown() {
        // Attempt to create a frame with null data
        createTestFrameFromBuffer(null);
    }

    @Test
    public void whenEmptyDataProvided_thenNoExceptionIsThrown() {
        final ByteBuffer emptyBuffer = ByteBuffer.allocate(0);

        // Attempt to create a frame with empty data
        // Main use case is for submitting end of fragment
        createTestFrameFromBuffer(emptyBuffer);
    }
}
