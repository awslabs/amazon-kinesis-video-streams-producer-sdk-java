package com.amazonaws.kinesisvideo.java.service;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentials;
import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentialsProvider;
import com.amazonaws.kinesisvideo.auth.StaticCredentialsProvider;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.producer.StreamDescription;
import com.amazonaws.kinesisvideo.producer.StreamStatus;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamResult;
import com.amazonaws.services.kinesisvideo.model.StreamInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class JavaKinesisVideoServiceClientTest {

    private static final Logger log = LogManager.getLogger(JavaKinesisVideoServiceClientTest.class);

    private final Instant now = Instant.now();
    private final Date expiration = Date.from(now.plus(Duration.ofHours(1)));

    private final KinesisVideoCredentials basicCreds = new KinesisVideoCredentials("ACCESS_KEY", "SECRET_KEY");
    private final KinesisVideoCredentials sessionCreds = new KinesisVideoCredentials("ACCESS_KEY", "SECRET_KEY", "SESSION_TOKEN", expiration);

    @Test
    public void testCreateAwsCredentials_withBasicCredentials() throws KinesisVideoException {
        final KinesisVideoCredentialsProvider provider = new StaticCredentialsProvider(basicCreds);

        final AWSCredentials credentials = JavaKinesisVideoServiceClient.createAwsCredentials(provider);

        assertNotNull(credentials);

        assertEquals("ACCESS_KEY", credentials.getAWSAccessKeyId());
        assertEquals("SECRET_KEY", credentials.getAWSSecretKey());
    }

    @Test
    public void testCreateAwsCredentials_withAWSSessionCredentials() throws KinesisVideoException {
        final KinesisVideoCredentialsProvider provider = new StaticCredentialsProvider(sessionCreds);

        final AWSCredentials credentials = JavaKinesisVideoServiceClient.createAwsCredentials(provider);

        assertNotNull(credentials);
        assertTrue(credentials instanceof AWSSessionCredentials);

        final AWSSessionCredentials sessionCredentials = (AWSSessionCredentials) credentials;

        assertEquals("ACCESS_KEY", sessionCredentials.getAWSAccessKeyId());
        assertEquals("SECRET_KEY", sessionCredentials.getAWSSecretKey());
        assertEquals("SESSION_TOKEN", sessionCredentials.getSessionToken());
    }

    @Test
    public void whenCredentialsProviderIsNull_thenCredentialsProviderIsNull() throws KinesisVideoException {
        final AWSCredentialsProvider credentialsProvider = JavaKinesisVideoServiceClient.createAwsCredentialsProvider(null, log);
        assertNull(credentialsProvider);
    }

    @Test
    public void whenCredentialsProviderIsNull_thenCredentialsIsNull() throws KinesisVideoException {
        final AWSCredentials credentials = JavaKinesisVideoServiceClient.createAwsCredentials(null);
        assertNull(credentials);
    }

    @Test
    public void whenCredentialsProviderReturnsNull_thenCredentialsReturnsNull() throws KinesisVideoException {
        final KinesisVideoCredentialsProvider nullReturningCredentialsProvider = new KinesisVideoCredentialsProvider() {

            @Nullable
            @Override
            public KinesisVideoCredentials getCredentials() throws KinesisVideoException {
                return null;
            }

            @Nullable
            @Override
            public KinesisVideoCredentials getUpdatedCredentials() throws KinesisVideoException {
                return null;
            }
        };

        final AWSCredentialsProvider credentialsProvider = JavaKinesisVideoServiceClient.createAwsCredentialsProvider(nullReturningCredentialsProvider, log);

        // No exceptions should be thrown
        credentialsProvider.refresh();
        assertNull(credentialsProvider.getCredentials());
    }

    @Test
    public void whenCredentialsProviderThrowsException_thenCredentialsReturnsNull() throws KinesisVideoException {
        final KinesisVideoCredentialsProvider nullReturningCredentialsProvider = new KinesisVideoCredentialsProvider() {

            @Nullable
            @Override
            public KinesisVideoCredentials getCredentials() throws KinesisVideoException {
                throw new KinesisVideoException();
            }

            @Nullable
            @Override
            public KinesisVideoCredentials getUpdatedCredentials() throws KinesisVideoException {
                throw new KinesisVideoException();
            }
        };

        final AWSCredentialsProvider credentialsProvider = JavaKinesisVideoServiceClient.createAwsCredentialsProvider(nullReturningCredentialsProvider, log);

        // No exceptions should be thrown
        credentialsProvider.refresh();
        assertNull(credentialsProvider.getCredentials());
    }

    @Test(expected = NullPointerException.class)
    public void testToStreamDescription_NullResult() {
        JavaKinesisVideoServiceClient.toStreamDescription(null);
    }

    @Test
    public void testToStreamDescription_BasicConversion() {
        final String streamName = "my-stream";
        final String streamArn = "arn:aws:kinesisvideo:us-west-2:123456789012:stream/my-stream/1234567890";
        final String version = "1";
        final Date creationTime = new Date();
        final Integer dataRetentionInHours = 0;
        final String status = "ACTIVE";
        final String kmsKeyId = "arn:aws:kms:us-west-2:123456789012:key/abcde123-4567-890a-bcde-1234567890ab";

        final DescribeStreamResult result = new DescribeStreamResult()
                .withStreamInfo(new StreamInfo()
                        .withStreamName(streamName)
                        .withStreamARN(streamArn)
                        .withVersion(version)
                        .withCreationTime(creationTime)
                        .withStatus(status)
                        .withDataRetentionInHours(dataRetentionInHours)
                        .withKmsKeyId(kmsKeyId));

        final StreamDescription streamDescription = JavaKinesisVideoServiceClient.toStreamDescription(result);

        assertNotNull(streamDescription);
        assertEquals(streamName, streamDescription.getStreamName());
        assertEquals(streamArn, streamDescription.getStreamArn());
        assertEquals(version, streamDescription.getUpdateVersion());
        assertEquals(creationTime.getTime(), streamDescription.getCreationTime());
        assertEquals(StreamStatus.ACTIVE.intValue(), streamDescription.getStreamStatus());
        assertEquals((long) dataRetentionInHours, streamDescription.getRetention());
        assertEquals(kmsKeyId, streamDescription.getKmsKeyId());
    }
}
