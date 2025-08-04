package com.amazonaws.kinesisvideo.common;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.kinesisvideo.client.KinesisVideoClient;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSource;
import com.amazonaws.kinesisvideo.java.client.KinesisVideoJavaClientFactory;
import com.amazonaws.kinesisvideo.java.mediasource.file.ImageFileMediaSource;
import com.amazonaws.kinesisvideo.java.mediasource.file.ImageFileMediaSourceConfiguration;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClientBuilder;
import com.amazonaws.services.kinesisvideo.model.CreateStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DeleteStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

public class DuplicateStreamNameIntegTest {

    private static final Logger log = LogManager.getLogger(DuplicateStreamNameIntegTest.class);
    private static final int STATUS_DUPLICATE_STREAM_NAME = 0x5200004B;

    private String streamName;

    @Before
    public void setup() {
        assumeTrue(DefaultAWSCredentialsProviderChain.getInstance().getCredentials() != null);

        final String prefix = Optional.ofNullable(System.getenv("TEST_STREAMS_PREFIX")).orElse("");
        final AmazonKinesisVideo awsSdkKinesisVideoClient = AmazonKinesisVideoClientBuilder.standard().build();
        boolean success = true;

        this.streamName = prefix + "-" + UUID.randomUUID();
        try {
            log.info("Creating stream {}", this.streamName);
            final CreateStreamRequest createStreamRequest = new CreateStreamRequest()
                    .withStreamName(this.streamName)
                    .withDataRetentionInHours(2);
            awsSdkKinesisVideoClient.createStream(createStreamRequest);
        } catch (final Throwable t) {
            log.error("Encountered an error creating stream: {}!", this.streamName, t);
            success = false;
        }

        assertTrue("Encountered an error in the setup, check the logs above", success);
    }

    @After
    public void tearDown() {
        final AmazonKinesisVideo awsSdkKinesisVideoClient = AmazonKinesisVideoClientBuilder.standard().build();
        boolean success = true;

        try {
            log.info("Deleting stream {}", this.streamName);
            final DescribeStreamRequest describeStreamRequest = new DescribeStreamRequest().withStreamName(this.streamName);
            final DescribeStreamResult describeStreamResult = awsSdkKinesisVideoClient.describeStream(describeStreamRequest);

            final DeleteStreamRequest deleteStreamRequest = new DeleteStreamRequest()
                    .withStreamARN(describeStreamResult.getStreamInfo().getStreamARN())
                    .withCurrentVersion(describeStreamResult.getStreamInfo().getVersion());
            awsSdkKinesisVideoClient.deleteStream(deleteStreamRequest);
        } catch (final Throwable t) {
            log.error("Encountered an error deleting healthy stream: {}!", this.streamName, t);
            success = false;
        }

        assertTrue("Encountered an error in the teardown, check the logs above", success);
    }

    /**
     * Validates that the user is notified of the exception.
     */
    @Test
    public void when_registerDuplicateStreamName_then_ThrowsException() throws KinesisVideoException {
        final MediaSource mediaSource = new ImageFileMediaSource(this.streamName);
        mediaSource.configure(new ImageFileMediaSourceConfiguration.Builder().build());
        final MediaSource duplicateMediaSource = new ImageFileMediaSource(this.streamName);
        duplicateMediaSource.configure(new ImageFileMediaSourceConfiguration.Builder().build());

        final KinesisVideoClient client = KinesisVideoJavaClientFactory
                .createKinesisVideoClient(Regions.US_WEST_2, DefaultAWSCredentialsProviderChain.getInstance());


        client.registerMediaSource(mediaSource);

        try {
            client.registerMediaSource(duplicateMediaSource);
            fail("Registering a media source with the same name should have thrown an error!");
        } catch (final ProducerException e) {
            assertEquals("Passing in a duplicate stream name should return 0x" + Long.toHexString(STATUS_DUPLICATE_STREAM_NAME),
                    STATUS_DUPLICATE_STREAM_NAME, e.getStatusCode());
        } finally {
            client.unregisterMediaSource(mediaSource);
            client.free();
        }
    }
}
