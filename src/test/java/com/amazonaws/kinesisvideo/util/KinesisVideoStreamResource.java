package com.amazonaws.kinesisvideo.util;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClientBuilder;
import com.amazonaws.services.kinesisvideo.model.CreateStreamRequest;
import com.amazonaws.services.kinesisvideo.model.CreateStreamResult;
import com.amazonaws.services.kinesisvideo.model.DeleteStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamResult;
import com.amazonaws.services.kinesisvideo.model.UpdateDataRetentionOperation;
import com.amazonaws.services.kinesisvideo.model.UpdateDataRetentionRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.io.Closeable;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNotNull;

/**
 * Represents a Kinesis Video Stream resource in AWS cloud.
 * A new stream will be created if the stream doesn't exist already,
 * and its retention will be updated to match the {@link KinesisVideoStreamConfiguration}.
 * The stream will be deleted when this object's {@link #close()} method
 * is called.
 */
public class KinesisVideoStreamResource implements Closeable {

    private static final Logger log = LogManager.getLogger(KinesisVideoStreamResource.class);

    /**
     * The default configuration uses the streamName format: 'prefix-nanoTime', where prefix
     * is the "TEST_STREAMS_PREFIX" environment variable.
     */
    public static class KinesisVideoStreamConfiguration {
        public int dataRetentionInHours = 2;
        public String streamName = String.join("-",
                Optional.ofNullable(System.getenv("TEST_STREAMS_PREFIX"))
                        .filter(String::isEmpty)
                        .orElse("test-stream"),
                Long.toString(System.nanoTime()));
    }

    private final KinesisVideoStreamConfiguration kinesisVideoStreamConfiguration;

    public KinesisVideoStreamResource(@Nonnull final KinesisVideoStreamConfiguration kinesisVideoStreamConfiguration) {
        this.kinesisVideoStreamConfiguration = kinesisVideoStreamConfiguration;

        final String streamName = kinesisVideoStreamConfiguration.streamName;
        final AWSCredentialsProvider credentialsProvider = DefaultAWSCredentialsProviderChain.getInstance();
        final String region = new DefaultAwsRegionProviderChain().getRegion();
        assumeNotNull("Unable to locate credentials!", credentialsProvider.getCredentials());
        assumeNotNull("Unable to locate region!", region);
        assertNotNull("The stream configuration cannot be null", kinesisVideoStreamConfiguration);
        assertNotNull("The stream name cannot be null", kinesisVideoStreamConfiguration.streamName);
        assertFalse("The stream name cannot be empty", kinesisVideoStreamConfiguration.streamName.isEmpty());
        assertTrue("The stream retention cannot be negative", kinesisVideoStreamConfiguration.dataRetentionInHours >= 0);

        final AmazonKinesisVideo kvs = AmazonKinesisVideoClientBuilder.standard()
                .withRegion(region)
                .withCredentials(credentialsProvider)
                .build();

        boolean created = false;
        try {
            final DescribeStreamRequest describeStreamRequest = new DescribeStreamRequest();
            describeStreamRequest.setStreamName(kinesisVideoStreamConfiguration.streamName);

            final DescribeStreamResult describeStreamResult = kvs.describeStream(describeStreamRequest);
            log.debug("Stream exists! {}", describeStreamResult.getStreamInfo().getStreamARN());


            if (describeStreamResult.getStreamInfo().getDataRetentionInHours() == 0) {
                log.info("Stream {} does not have any retention. Updating...", streamName);

                final UpdateDataRetentionRequest updateDataRetentionRequest = new UpdateDataRetentionRequest();
                updateDataRetentionRequest.setStreamName(streamName);
                updateDataRetentionRequest.setCurrentVersion(describeStreamResult.getStreamInfo().getVersion());
                updateDataRetentionRequest.setOperation(UpdateDataRetentionOperation.INCREASE_DATA_RETENTION.toString());
                updateDataRetentionRequest.setDataRetentionChangeInHours(2);
                kvs.updateDataRetention(updateDataRetentionRequest);
            }

        } catch (final Exception e) {
            final CreateStreamRequest createStreamRequest = new CreateStreamRequest();
            createStreamRequest.setStreamName(streamName);
            createStreamRequest.setDataRetentionInHours(2);
            final CreateStreamResult createStreamResult = kvs.createStream(createStreamRequest);
            log.debug("Stream created! {}", createStreamResult.getStreamARN());
            created = true;
        }

        // In case the stream hasn't finished being created yet
        if (created) {
            for (int i = 0; i < 5; i++) {
                try {
                    final DescribeStreamRequest describeStreamRequest = new DescribeStreamRequest();
                    describeStreamRequest.setStreamName(streamName);

                    final DescribeStreamResult describeStreamResult = kvs.describeStream(describeStreamRequest);
                    log.debug("Stream exists now. ARN: {}", describeStreamResult.getStreamInfo().getStreamARN());
                } catch (final Exception e) {
                    log.info("Stream is still creating... {}/{}", i, 3, e);
                    try {
                        Thread.sleep(1000L * (1 << i));
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        kvs.shutdown();
    }

    @Override
    public void close() {
        final AmazonKinesisVideo kvs = AmazonKinesisVideoClientBuilder.standard()
                .withRegion(new DefaultAwsRegionProviderChain().getRegion())
                .withCredentials(DefaultAWSCredentialsProviderChain.getInstance())
                .build();
        try {
            final DescribeStreamRequest describeStreamRequest = new DescribeStreamRequest()
                    .withStreamName(kinesisVideoStreamConfiguration.streamName);
            final DescribeStreamResult describeStreamResult = kvs.describeStream(describeStreamRequest);

            final DeleteStreamRequest deleteStreamRequest = new DeleteStreamRequest()
                    .withStreamARN(describeStreamResult.getStreamInfo().getStreamARN())
                    .withCurrentVersion(describeStreamResult.getStreamInfo().getVersion());
            kvs.deleteStream(deleteStreamRequest);
        } catch (final Exception e) {
            log.error("Error deleting stream {}", kinesisVideoStreamConfiguration.streamName, e);
            fail("Error deleting stream " + kinesisVideoStreamConfiguration.streamName);
        } finally {
            kvs.shutdown();
        }
    }
}
