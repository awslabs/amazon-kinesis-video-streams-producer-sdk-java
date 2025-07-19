package com.amazonaws.kinesisvideo.java.auth;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentialsProvider;

import java.time.Duration;

/**
 * Turns an AWS credentials provider into KinesisVideoCredentialsProvider.
 * There are two types of KinesisVideoCredentialsProvider - one that provides temporary credentials (session token),
 * and non-temporary that just provides access key + secret key.
 */
public class JavaCredentialsFactory {
    private JavaCredentialsFactory() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the correct Kinesis Video Credentials Provider based on if the AWS Credentials provider returns a session token or not.
     * Use this if you know your session token lasts for 1 hour.
     */
    public static KinesisVideoCredentialsProvider getKinesisVideoCredentialsProvider(final AWSCredentialsProvider awsCredentialsProvider) {
        return getKinesisVideoCredentialsProvider(awsCredentialsProvider, Duration.ofHours(1));
    }

    /**
     * @param awsCredentialsProvider             AWSCredentialsProvider
     * @param credentialsRefreshIntervalFallback If the AWSCredentialsProvider returns temporary credentials, how often
     *                                           those credentials should be refreshed. Can be less than its actual expiration.
     * @return the correct (temporary/non-temporary) Kinesis Video Credentials Provider based on the AWS Credentials Provider
     */
    public static KinesisVideoCredentialsProvider getKinesisVideoCredentialsProvider(final AWSCredentialsProvider awsCredentialsProvider, final Duration credentialsRefreshIntervalFallback) {
        if (awsCredentialsProvider.getCredentials() instanceof AWSSessionCredentials) {
            return new JavaCredentialsProviderImpl(awsCredentialsProvider, credentialsRefreshIntervalFallback.toMillis());
        } else {
            return new JavaCredentialsProviderImpl(awsCredentialsProvider);
        }
    }
}
