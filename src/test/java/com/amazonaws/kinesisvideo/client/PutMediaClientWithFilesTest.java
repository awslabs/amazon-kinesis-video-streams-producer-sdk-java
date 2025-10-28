package com.amazonaws.kinesisvideo.client;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.kinesisvideo.client.signing.KinesisVideoAWS4Signer;
import com.amazonaws.kinesisvideo.common.function.Consumer;
import com.amazonaws.kinesisvideo.util.KinesisVideoStreamResource;
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
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Comprehensive integration test suite for PutMediaClient using real MKV file samples.
 * <p>
 * This parameterized test validates PutMediaClient behavior across various MKV file scenarios,
 * including both valid content and edge cases that should trigger specific error responses
 * from the Amazon Kinesis Video Streams service.
 * <p>
 * Test Coverage:
 * - Valid MKV files with proper track structure and timing
 * - Invalid MKV files with corrupted data or malformed structure
 * - Edge cases like track number mismatches and timestamp violations
 * - Service ACK processing for both success and error scenarios
 * - Thread lifecycle management and resource cleanup
 * <p>
 * The test uses real AWS KVS streams (created/destroyed per test) to ensure
 * end-to-end validation of the client-service interaction. Each test case
 * specifies expected ACK patterns that should be received from the service.
 * <p>
 * ACK Validation Strategy:
 * - Success cases expect "FragmentNumber" ACKs (RECEIVED, BUFFERING, PERSISTED)
 * - Error cases expect specific error type strings in ACK responses
 * - ACK order validation accounts for potential BUFFERING ACK variations
 * <p>
 * Resource Management:
 * - Automatic KVS stream creation/deletion via KinesisVideoStreamResource
 * - Thread leak detection through before/after thread enumeration
 * - Proper cleanup verification for all test scenarios
 */
@RunWith(Parameterized.class)
public class PutMediaClientWithFilesTest {

    private static final Logger log = LogManager.getLogger(PutMediaClientWithFilesTest.class);

    private static final String END_OF_STREAM_MSG = "0\r\n\r\n";
    private static final String PUT_MEDIA_POSTFIX = "/putMedia";
    private static final String SERVICE_NAME = "kinesisvideo";
    private static final int BUFFER_SIZE = 1024;
    private static final int TIMEOUT_SECONDS = 100;
    private static final int EXPECTED_COMPLETION_CALLBACKS = 1;

    private URI putMediaUri; // PutMedia data endpoint response + "/putMedia"
    private String region;
    private KinesisVideoAWS4Signer putMediaAWS4Signer;
    private KinesisVideoStreamResource.KinesisVideoStreamConfiguration kinesisVideoStreamConfiguration;
    private KinesisVideoStreamResource kinesisVideoStreamResource;
    private final InputStream testMkvFile;
    private final List<String> expectedAcks;

    @Before
    public void setUp() throws Exception {
        // Endpoint discovery pattern
        final AmazonKinesisVideo kinesisVideoClient = AmazonKinesisVideoClientBuilder.defaultClient();

        this.kinesisVideoStreamConfiguration =
                new KinesisVideoStreamResource.KinesisVideoStreamConfiguration();

        this.kinesisVideoStreamResource = new KinesisVideoStreamResource(this.kinesisVideoStreamConfiguration);

        final GetDataEndpointResult dataEndpointResult = kinesisVideoClient.getDataEndpoint(new GetDataEndpointRequest()
                .withStreamName(this.kinesisVideoStreamConfiguration.streamName)
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
    public void cleanUp() {
        if (this.kinesisVideoStreamResource != null) {
            this.kinesisVideoStreamResource.close();
            this.kinesisVideoStreamResource = null;
        }
    }

    /**
     * Test data provider defining MKV file scenarios and expected service responses.
     * <p>
     * Each test case consists of:
     * 1. Resource file path - MKV file in src/test/resources/samples/
     * 2. Expected ACK patterns - List of strings that should appear in service ACKs
     */
    @Parameterized.Parameters(name = "{index}: {0}, {1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(
                // Edge case: Track metadata mismatch
                // Contains 20 clusters, with:
                // - odd clusters having frames in track 1 and 2
                // - even clusters having frames in track 2 only
                // - the trackInfo only declares track 1
                new Object[]{"samples/extra-tracks.mkv", Arrays.asList("TRACK_NUMBER_MISMATCH")},

                // Error case: Corrupted MKV structure
                // Contains one cluster with malformed MKV data that fails parsing
                new Object[]{"samples/invalid-mkv-data.mkv", Arrays.asList("INVALID_MKV_DATA")},

                // Error case: Timestamp constraint violation
                // Contains one cluster with timestamps that violate KVS requirements
                new Object[]{"samples/invalid-producer-timestamp.mkv", Arrays.asList("INVALID_PRODUCER_TIMESTAMP")},

                // Success case: Minimal valid content
                // Contains 2 clusters (expecting received, buffering, persisted for both)
                new Object[]{"samples/valid-single-track.mkv", Collections.nCopies(6, "FragmentNumber")},

                // Success case: Multi-cluster content
                // Contains 8 clusters (expecting received, buffering, persisted for all)
                new Object[]{"samples/clusters.mkv", Collections.nCopies(24, "FragmentNumber")}
        );
    }

    /**
     * Constructor for parameterized test execution.
     *
     * @param resourceFilePath Path to MKV file in test resources
     * @param expectedAcks     List of strings expected to appear in service ACK responses
     */
    public PutMediaClientWithFilesTest(@Nonnull final String resourceFilePath, @Nonnull final List<String> expectedAcks) {
        // Load test MKV file from classpath resources
        this.testMkvFile = PutMediaClientWithFilesTest.class.getClassLoader().getResourceAsStream(resourceFilePath);
        assertNotNull("Could not load: " + resourceFilePath, this.testMkvFile);

        this.expectedAcks = expectedAcks;
    }

    /**
     * End-to-end integration test for PutMediaClient with real MKV content.
     * <p>
     * This test validates the complete client-service interaction flow:
     * 1. Stream MKV content to KVS service via PutMediaClient
     * 2. Process HTTP response and ACK messages from service
     * 3. Validate expected ACK patterns based on content type
     * 4. Verify proper resource cleanup and thread management
     * <p>
     * The test creates a real KVS stream, streams the MKV content, and validates
     * that the service responds with expected ACK patterns. Success cases expect
     * FragmentNumber ACKs, while error cases expect specific error type strings.
     * <p>
     * Thread leak detection ensures no background threads are left running after
     * the client is closed, which is critical for applications that create many
     * short-lived PutMediaClient instances.
     *
     * @throws Exception if any assertion fails or unexpected errors occur
     */
    @Test
    public void testPutMediaClientWithMkvFile() throws Exception {
        final CountDownLatch completionLatch = new CountDownLatch(1);
        final boolean[] success = {false};
        final List<String> acksReceived = new ArrayList<>();
        final List<Exception> completionsReceived = new ArrayList<>();

        // Capture baseline thread state for leak detection
        // Essential for validating proper cleanup in production environments
        final List<String> threadsBefore = Thread.getAllStackTraces().keySet()
                .stream()
                .map(Thread::getName)
                .collect(Collectors.toList());

        final PutMediaClient client = PutMediaClient.builder()
                .putMediaDestinationUri(this.putMediaUri)
                .signWith(this.putMediaAWS4Signer)
                .streamName(this.kinesisVideoStreamConfiguration.streamName)
                .mkvStream(this.testMkvFile)
                .timestamp(System.currentTimeMillis())
                .fragmentTimecodeType("RELATIVE")
                .receiveAcks(new Consumer<InputStream>() {
                    @Override
                    public void accept(final InputStream acks) {
                        try (final BufferedReader br = new BufferedReader(new InputStreamReader(acks))) {
                            final String statusLine = br.readLine();
                            log.info(statusLine);
                            assertEquals("HTTP/1.1 200 OK", statusLine);

                            final List<String> headerLines = new ArrayList<>();
                            String headerLine;
                            while ((headerLine = br.readLine()) != null && !headerLine.isEmpty()) {
                                log.info(headerLine);
                                headerLines.add(headerLine);
                            }

                            assertFalse("Did not receive any HTTP headers", headerLines.isEmpty());
                            assertTrue("Did not receive a request ID", headerLines.stream().anyMatch(line -> line.startsWith("x-amzn-RequestId: ")));
                            assertTrue("Did not receive a date", headerLines.stream().anyMatch(line -> line.startsWith("Date: ")));
                            assertTrue("It should be chunked transfer encoding", headerLines.stream().anyMatch(line -> line.startsWith("Transfer-Encoding: chunked")));
                            assertTrue("It should be keep alive", headerLines.stream().anyMatch(line -> line.startsWith("connection: keep-alive")));

                            log.info("Start receiving acks");

                            // Read raw bytes for PutMedia ACK responses
                            final byte[] buffer = new byte[BUFFER_SIZE];
                            int bytesRead;
                            while ((bytesRead = acks.read(buffer)) > 0) {
                                final String response = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);

                                if (END_OF_STREAM_MSG.equals(response)) {
                                    // end of stream
                                    log.info("End of stream!");
                                    break;
                                }

                                if (!response.isEmpty()) {
                                    log.info("Received ack: " + response);
                                    acksReceived.add(response);
                                }
                            }
                            // bytesRead == -1 = connection closed by service
                            log.info("Done receiving acks");
                        } catch (final IOException e) {
                            log.error("Error reading acks", e);
                            fail("Ran into an IOException: " + e);
                        }
                    }
                })
                .receiveCompletion(new Consumer<Exception>() {
                    @Override
                    public void accept(final Exception exception) {
                        log.info("Received exception: " + exception);

                        completionsReceived.add(exception);

                        success[0] = (exception == null);
                        if (exception != null) {
                            log.error("PutMedia errored out!", exception);
                        }
                        completionLatch.countDown();
                    }
                })
                .build();
        try {
            client.putMediaInBackground();

            // Block the main thread up to 100 seconds for completion callback
            assertTrue("Upload did not complete within timeout",
                    completionLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

            log.info("Acks received: " + acksReceived);
            log.info("Completions received: " + completionsReceived);
            assertEquals("Completion callback was not called " + EXPECTED_COMPLETION_CALLBACKS + " times", EXPECTED_COMPLETION_CALLBACKS, completionsReceived.size());
            assertNull("Received an unexpected exception in the completion callback!", completionsReceived.get(0));

            // ACK validation with tolerance for service-side buffering variations
            // The KVS service may send BUFFERING ACKs inconsistently
            // (i.e., BUFFERING, then ERROR; or ERROR immediately), so we validate
            // from the end of the ACK list to ensure we catch the expected patterns
            // regardless of whether buffering ACKs are present
            assertTrue("Not enough acks received. Expected: " + this.expectedAcks.size() + ", received: " + acksReceived.size(), acksReceived.size() >= this.expectedAcks.size());
            for (int i = 0; i < this.expectedAcks.size(); i++) {
                final String currentAck = acksReceived.get(acksReceived.size() - 1 - i);
                // ACK format: chunk-length + JSON payload containing the actual ACK data
                assertTrue("\"" + currentAck + "\" does not have: \"" + this.expectedAcks.get(i) + "\" in it.",
                        currentAck.contains(this.expectedAcks.get(i)));
            }


        } finally {
            client.close();
        }

        // Thread leak detection
        // Compares thread state before/after to ensure no background threads
        // are leaked by the PutMediaClient implementation
        final List<String> threadsAfter = Thread.getAllStackTraces().keySet()
                .stream()
                .map(Thread::getName)
                .collect(Collectors.toList());

        threadsBefore.sort(String.CASE_INSENSITIVE_ORDER);
        threadsAfter.sort(String.CASE_INSENSITIVE_ORDER);
        assertEquals("There was a thread that wasn't cleaned up properly!", threadsBefore, threadsAfter);
    }
}
