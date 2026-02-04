package com.amazonaws.kinesisvideo.internal.service;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.kinesisvideo.util.StreamInfoConstants;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClientBuilder;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link DefaultServiceCallbacksImpl} class.
 * Tests focus on the getStatusCodeFromException method behavior.
 */
public class DefaultServiceCallbacksImplTest {

    private static final Logger log = LogManager.getLogger(DefaultServiceCallbacksImplTest.class);

    private AmazonKinesisVideo awsSdkKinesisVideoClient;

    @Before
    public void setUp() {
        this.awsSdkKinesisVideoClient = AmazonKinesisVideoClientBuilder.defaultClient();
    }

    @After
    public void tearDown() {
        this.awsSdkKinesisVideoClient.shutdown();
    }

    @Test
    public void whenExceptionIsNull_thenReturnsHttpOk() {
        final int statusCode = DefaultServiceCallbacksImpl.getStatusCodeFromException(null);
        assertEquals(StreamInfoConstants.HTTP_OK, statusCode);
    }

    /**
     * Tests that when a stream does not exist:
     * - Returns HTTP_NOT_FOUND (404)
     */
    @Test
    public void whenExceptionIsResourceNotFoundException_thenReturnsHttpNotFound() {
        final String streamName = "TestStream-DoesNotExist-Nope";
        final DescribeStreamRequest describeStreamRequest = new DescribeStreamRequest()
                .withStreamName(streamName);

        try {
            this.awsSdkKinesisVideoClient.describeStream(describeStreamRequest);
            fail("An exception should have been thrown");
        } catch (final Exception e) {
            log.info("Received exception (expected): {}", e.getClass().getName(), e);
            final int statusCode = DefaultServiceCallbacksImpl.getStatusCodeFromException(e);
            assertEquals("ResourceNotFound should have returned " + StreamInfoConstants.HTTP_NOT_FOUND,
                    StreamInfoConstants.HTTP_NOT_FOUND, statusCode);
        }
    }

    /**
     * Tests that it will translate a bad request correctly:
     * - Pass both streamARN and streamName to describeStream API
     * - 400 InvalidArgumentException expected to be returned
     * - Returns HTTP_BAD_REQUEST (400)
     */
    @Test
    public void whenExceptionIsBadRequest_thenReturnsBadRequest() {
        final String streamName = "TestStream";
        final String streamARN = "arn:aws:kinesisvideo:us-west-2:123456789012:stream/TestStream/1691560751966";

        final DescribeStreamRequest describeStreamRequest = new DescribeStreamRequest()
                .withStreamName(streamName)
                .withStreamARN(streamARN);

        try {
            this.awsSdkKinesisVideoClient.describeStream(describeStreamRequest);
            fail("An exception should have been thrown");
        } catch (final Exception e) {
            log.info("Received exception (expected): {}", e.getClass().getName(), e);
            final int statusCode = DefaultServiceCallbacksImpl.getStatusCodeFromException(e);
            assertEquals("InvalidArgumentException should have returned " + StreamInfoConstants.HTTP_BAD_REQUEST,
                    StreamInfoConstants.HTTP_BAD_REQUEST, statusCode);
        }
    }

    /**
     * Tests that when exception class name ends with "AccessDeniedException":
     * - Uses bad credentials
     * - Expects UnrecognizedClientException (403) to be returned
     * - Returns HTTP_ACCESS_DENIED (403)
     */
    @Test
    public void whenExceptionIsAccessDeniedException_thenReturnsHttpAccessDenied() {
        final String streamName = "demo-stream";

        final DescribeStreamRequest describeStreamRequest = new DescribeStreamRequest()
                .withStreamName(streamName)
                .withRequestCredentialsProvider(new AWSCredentialsProvider() {
                    @Override
                    public AWSCredentials getCredentials() {
                        return new BasicAWSCredentials("accessKey", "secretKey");
                    }

                    @Override
                    public void refresh() {
                        // No-op
                    }
                });

        try {
            this.awsSdkKinesisVideoClient.describeStream(describeStreamRequest);
            fail("An exception should have been thrown");
        } catch (final Exception e) {
            log.info("Received exception (expected): {}", e.getClass().getName(), e);
            final int statusCode = DefaultServiceCallbacksImpl.getStatusCodeFromException(e);
            assertEquals("UnrecognizedClientException should have returned " + StreamInfoConstants.HTTP_ACCESS_DENIED,
                    StreamInfoConstants.HTTP_ACCESS_DENIED, statusCode);
        }
    }

    /**
     * Tests that when exception doesn't match any specific patterns:
     * - Returns HTTP_BAD_REQUEST (400) as default
     */
    @Test
    public void whenExceptionIsGeneric_thenReturnsHttpBadRequest() {
        final Exception exception = new RuntimeException("Generic error");
        final int statusCode = DefaultServiceCallbacksImpl.getStatusCodeFromException(exception);
        assertEquals(StreamInfoConstants.HTTP_BAD_REQUEST, statusCode);
    }

}
