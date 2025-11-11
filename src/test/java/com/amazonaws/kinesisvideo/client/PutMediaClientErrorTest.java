package com.amazonaws.kinesisvideo.client;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.kinesisvideo.client.signing.KinesisVideoAWS4Signer;
import com.amazonaws.kinesisvideo.common.function.Consumer;
import com.amazonaws.kinesisvideo.util.KinesisVideoStreamResource;
import com.amazonaws.kinesisvideo.util.ThreadWatcher;
import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClientBuilder;
import com.amazonaws.services.kinesisvideo.model.APIName;
import com.amazonaws.services.kinesisvideo.model.GetDataEndpointRequest;
import com.amazonaws.services.kinesisvideo.model.GetDataEndpointResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Integration test suite for PutMediaClient error handling and resource management.
 * <p>
 * This test class validates the client's behavior under adverse conditions, specifically:
 * - Resource cleanup when dealing with infinite/garbage data streams (in a real
 * application, this would be a data stream that has more data yet to be sent)
 * - Thread lifecycle management to prevent resource leaks
 * - Proper handling of completion callbacks under error conditions
 * - HTTP response parsing and ACK processing robustness
 * <p>
 * The test uses a MockInputStream that generates continuous garbage data to simulate
 * scenarios where the data source never terminates naturally, forcing the client
 * to handle cleanup through external mechanisms (close() calls).
 * <p>
 * Critical aspects tested:
 * - Thread leak detection (before/after thread count comparison)
 * - Resource cleanup verification (InputStream.close() call counting)
 * - HTTP protocol compliance (status codes, headers, chunked encoding)
 * - <strong>Completion callback reliability (exactly-once semantics)</strong>
 * - <strong>InputStream management (exactly-once semantics)</strong>
 */
public class PutMediaClientErrorTest {

    private static final Logger log = LogManager.getLogger(PutMediaClientErrorTest.class);

    private static final int TIMEOUT_SECONDS = 20;
    private static final String DELIMITER = "\r\n\r\n";
    private static final String END_OF_STREAM_MSG = "0" + DELIMITER;
    private static final String PUT_MEDIA_POSTFIX = "/putMedia";
    private static final String SERVICE_NAME = "kinesisvideo";

    private URI putMediaUri; // Put media data endpoint + "/putMedia"
    private String region;
    private KinesisVideoAWS4Signer putMediaAWS4Signer;
    private KinesisVideoStreamResource.KinesisVideoStreamConfiguration streamConfiguration;
    private KinesisVideoStreamResource kinesisVideoStreamResource;

    private ThreadWatcher threadWatcher;
    private final List<String> THREADS_TO_IGNORE = Collections.singletonList("java-sdk-http-connection-reaper");
    private final Duration THREADS_TIMEOUT = Duration.ofSeconds(5);
    private final Duration THREADS_POLLING_INTERVAL = Duration.ofMillis(100);

    @Before
    public void setUp() throws Exception {
        this.streamConfiguration = new KinesisVideoStreamResource.KinesisVideoStreamConfiguration();
        this.kinesisVideoStreamResource = new KinesisVideoStreamResource(this.streamConfiguration);

        // Endpoint discovery
        final AmazonKinesisVideo kinesisVideoClient = AmazonKinesisVideoClientBuilder.defaultClient();

        final GetDataEndpointResult dataEndpointResult = kinesisVideoClient.getDataEndpoint(new GetDataEndpointRequest()
                .withStreamName(this.streamConfiguration.streamName)
                .withAPIName(APIName.PUT_MEDIA));

        kinesisVideoClient.shutdown();

        this.putMediaUri = URI.create(dataEndpointResult.getDataEndpoint() + PUT_MEDIA_POSTFIX);

        this.region = new DefaultAwsRegionProviderChain().getRegion();

        final com.amazonaws.kinesisvideo.config.ClientConfiguration clientConfiguration =
                com.amazonaws.kinesisvideo.config.ClientConfiguration
                        .builder()
                        .serviceName(SERVICE_NAME)
                        .region(this.region)
                        .build();
        this.putMediaAWS4Signer = new KinesisVideoAWS4Signer(DefaultAWSCredentialsProviderChain.getInstance(),
                clientConfiguration);
    }

    @After
    public void tearDown() {
        this.kinesisVideoStreamResource.close();
    }

    /**
     * Validates PutMediaClient behavior with an infinite garbage data stream.
     * <p>
     * This test is designed to catch resource leaks and threading issues that can occur
     * when the client processes data that never naturally terminates. The MockInputStream
     * continuously generates random bytes, simulating a pathological data source.
     * <p>
     * Key validations:
     * 1. Thread cleanup - Ensures no background threads are leaked after client shutdown
     * 2. Resource management - Verifies InputStream.close() is called exactly once
     * 3. Completion semantics - Confirms completion callback is invoked exactly once
     * 4. HTTP protocol handling - Validates proper response parsing under stress
     * <p>
     * The test uses thread snapshots before/after to detect leaks, which is critical
     * for long-running applications that create/destroy many PutMediaClient instances.
     */
    @Test
    public void testPutMediaClientWithGarbageData() throws Exception {
        final CountDownLatch completionLatch = new CountDownLatch(1);
        final List<String> acksReceived = Collections.synchronizedList(new ArrayList<>());
        final List<Exception> completionsReceived = Collections.synchronizedList(new ArrayList<>());

        // Capture baseline thread state before client creation
        // This is essential for detecting thread leaks in concurrent environments
        this.threadWatcher = new ThreadWatcher(this.THREADS_TIMEOUT, this.THREADS_POLLING_INTERVAL, this.THREADS_TO_IGNORE);

        // MockInputStream generates infinite garbage data to stress-test resource cleanup
        final MockInputStream garbageStream = new MockInputStream(72105328310511897L);

        final PutMediaClient client = PutMediaClient.builder()
                .putMediaDestinationUri(this.putMediaUri)
                .signWith(this.putMediaAWS4Signer)
                .streamName(this.streamConfiguration.streamName)
                .mkvStream(garbageStream)
                .timestamp(System.currentTimeMillis())
                .fragmentTimecodeType("RELATIVE")
                .receiveAcks(new Consumer<InputStream>() {
                    /**
                     * ACK processing callback - handles HTTP response stream from KVS service.
                     * <p>
                     * This callback processes the chunked HTTP response containing acknowledgments
                     * from the Kinesis Video Streams service. The response format follows HTTP/1.1
                     * with chunked transfer encoding for streaming ACKs.
                     * <p>
                     * Expected response structure:
                     * 1. HTTP status line (HTTP/1.1 200 OK)
                     * 2. HTTP headers (including request ID, date, transfer encoding)
                     * 3. Chunked body containing JSON ACK payloads
                     * 4. End-of-stream marker (0\r\n\r\n)
                     */
                    @Override
                    public void accept(final InputStream acks) {
                        try (final BufferedReader br = new BufferedReader(new InputStreamReader(acks))) {
                            final String statusLine = br.readLine();
                            log.info(statusLine);
                            assertEquals("HTTP/1.1 200 OK", statusLine);

                            // Parse and validate HTTP headers
                            final List<String> headerLines = new ArrayList<>();
                            String headerLine;
                            while ((headerLine = br.readLine()) != null && !headerLine.isEmpty()) {
                                log.info(headerLine);
                                headerLines.add(headerLine);
                            }

                            // Validate essential HTTP headers are present
                            assertFalse("Did not receive any HTTP headers", headerLines.isEmpty());
                            assertTrue("Did not receive a request ID", headerLines.stream().anyMatch(line -> line.startsWith("x-amzn-RequestId: ")));
                            assertTrue("Did not receive a date", headerLines.stream().anyMatch(line -> line.startsWith("Date: ")));
                            assertTrue("It should be chunked transfer encoding", headerLines.stream().anyMatch(line -> line.startsWith("Transfer-Encoding: chunked")));
                            assertTrue("It should be keep alive", headerLines.stream().anyMatch(line -> line.startsWith("connection: keep-alive")));

                            log.info("Start receiving acks");

                            // Process chunked HTTP response body containing ACK messages
                            // Each chunk contains length-prefixed JSON ACK payloads from KVS
                            final byte[] buffer = new byte[1024];
                            int bytesRead;
                            while ((bytesRead = acks.read(buffer)) > 0) {
                                final String response = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);

                                if (END_OF_STREAM_MSG.equals(response)) {
                                    // HTTP chunked encoding end marker - normal termination
                                    log.info("End of stream!");
                                    break;
                                }

                                if (!response.isEmpty()) {
                                    // There could more than one ack inside of this message
                                    Arrays.stream(response.split(DELIMITER))
                                            .peek(ackStr -> log.info("Received ack: {}", response))
                                            .forEach(acksReceived::add);
                                }
                            }
                            // bytesRead == -1 indicates connection closed by service
                            log.info("Done receiving acks");
                        } catch (final IOException e) {
                            log.error("Error reading acks", e);
                            fail("Ran into an IOException: " + e);
                        }
                    }
                })
                .receiveCompletion(new Consumer<Exception>() {
                    /**
                     * Completion callback - invoked when PutMedia operation terminates.
                     * <p>
                     * This callback provides the final status of the streaming operation.
                     * A null exception indicates successful completion, while non-null
                     * indicates an error condition that terminated the stream.
                     * <p>
                     * We are expecting the INVALID_MKV_DATA error ack to trigger the PIC layer to spawn a new PutMedia connection
                     * starting from the next fragment. If the completion callback triggers with an error as well, the PIC would
                     * receive duplicate notifications. PIC should already handle this via the UploadHandle #, but not all
                     * clients may consider this behavior.
                     */
                    @Override
                    public void accept(final Exception exception) {
                        log.info("Received exception: " + exception);

                        completionsReceived.add(exception);

                        if (exception != null) {
                            log.error("PutMedia errored out!", exception);
                        }
                        completionLatch.countDown();
                    }
                })
                .build();
        try {
            client.putMediaInBackground();

            // Wait for completion with generous timeout for garbage data processing
            assertTrue("Upload did not complete within timeout",
                    completionLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

            log.info("Acks received: " + acksReceived);
            log.info("Completions received: " + completionsReceived);

            // Sometimes PutMedia closes the connection without sending the ERROR ack
            // - If PutMedia closed with an ERROR ack, the completionCallback should have a null (success)
            //   since parseFragmentAck() in the receiver thread will notify PIC.
            // - If PutMedia closed without any ERROR ack, we need the completionCallback with an exception
            //   to notify PIC.
            // PutMedia's receiveCompletion will throw an exception if there was still stuff to send in the buffer
            // but the service already closed the connection.
            // The logic to handle this is in the DefaultServiceCallbacksImpl's CompletionCallback, where it synchronizes
            // the ack receiver's seenErrorAck().
            if (!acksReceived.isEmpty()) {
                // Note: In this case, we are not expecting any BUFFERING acks
                assertEquals("Expected 1 INVALID_MKV_DATA ack: " + acksReceived, 1, acksReceived.size());
                assertTrue("The ACK should be INVALID_MKV_DATA: " + acksReceived, acksReceived.get(0).contains("INVALID_MKV_DATA"));
            }

            // Validate completion callback exactly-once semantics
            // Expecting an exception to be propagated since there is still more data to be sent
            assertEquals("Completion callback was not called exactly once! Completions received: " + completionsReceived, 1, completionsReceived.size());
            assertTrue("Received an unexpected exception in the completion callback! Expected RuntimeException, but received: " + completionsReceived, completionsReceived.get(0) instanceof RuntimeException);

            // Expecting the cause to be java.net.SocketException: "Connection or outbound has been closed"
            assertNotNull("Expected a cause in the exception " + completionsReceived.get(0), completionsReceived.get(0).getCause());
            assertTrue("Expected the cause to be a socket exception: " + completionsReceived.get(0).getCause(), completionsReceived.get(0).getCause() instanceof SocketException);
        } finally {
            client.close();
        }

        // Thread leak detection - compares thread state before/after
        // Any leaked threads indicate resource management bugs that can cause
        // memory leaks and eventual OOM in long-running applications
        this.threadWatcher.close();
        this.threadWatcher = null;

        // Verify resource cleanup - InputStream.close() must be called exactly once
        assertEquals("close() was not called exactly once!", 1, garbageStream.closedCalls.get());
    }

    /**
     * Mock InputStream implementation for testing resource cleanup under adverse conditions.
     * <p>
     * This implementation simulates a pathological data source that:
     * - Never naturally terminates (never returns -1 from read())
     * - Generates continuous garbage data to stress-test the client
     * - Tracks close() calls to verify proper resource management
     * <p>
     * The key behavior is that read() operations always return data, forcing
     * the client to handle termination through external mechanisms (close() calls)
     * rather than 'natural' stream exhaustion.
     * <p>
     * This pattern is critical for testing scenarios where network conditions prevent
     * normal stream termination, or when the KVS service rejects a fragment (it will terminate
     * the PutMedia connection).
     */
    private static class MockInputStream extends InputStream {

        private final Random random;
        private final AtomicInteger closedCalls = new AtomicInteger(0);

        private MockInputStream(final long seed) {
            super();
            this.random = new Random(seed);
        }

        @Override
        public int read() throws IOException {
            // Force callers to use buffered read methods
            throw new IOException("Can't call byte-by-byte");
        }

        @Override
        public int read(@Nonnull final byte[] b,
                        final int off,
                        final int len)
                throws IOException {

            // Generate continuous garbage data - never return -1 (end of stream)
            // This simulates a data source that never naturally terminates,
            // forcing the client to handle cleanup through close() calls
            this.random.nextBytes(b);

            return len;
        }

        @Override
        public int read(@Nonnull final byte[] b)
                throws IOException {
            return read(b, 0, b.length);
        }

        @Override
        public void close() throws IOException {
            // Track close() calls to verify exactly-once cleanup semantics
            this.closedCalls.incrementAndGet();
        }
    }
}
