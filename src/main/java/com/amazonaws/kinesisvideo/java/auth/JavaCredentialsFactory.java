package com.amazonaws.kinesisvideo.java.auth;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentialsProvider;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.time.Duration;

/**
 * Turns an AWS credentials provider into KinesisVideoCredentialsProvider.
 * There are two types of KinesisVideoCredentialsProvider - one that provides temporary credentials (session token),
 * and non-temporary that just provides access key + secret key.
 */
@ThreadSafe
public final class JavaCredentialsFactory {
    private JavaCredentialsFactory() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the correct Kinesis Video Credentials Provider based on if the AWS Credentials provider returns a session token or not.
     * Use this if you know your session token lasts for 1 hour.
     */
    public static KinesisVideoCredentialsProvider createKinesisVideoCredentialsProvider(@Nonnull final AWSCredentialsProvider awsCredentialsProvider) {
        return createKinesisVideoCredentialsProvider(awsCredentialsProvider, Duration.ofHours(1));
    }

    /**
     * @param awsCredentialsProvider             AWSCredentialsProvider
     * @param credentialsRefreshIntervalFallback If the AWSCredentialsProvider returns temporary credentials, how often
     *                                           those credentials should be refreshed. Can be less than its actual expiration.
     * @return the correct (temporary/non-temporary) Kinesis Video Credentials Provider based on the AWS Credentials Provider
     */
    @SuppressWarnings({"ConstantConditions"}) // @Nonnull is a compile-time check, it can still be null at runtime
    public static KinesisVideoCredentialsProvider createKinesisVideoCredentialsProvider(@Nonnull final AWSCredentialsProvider awsCredentialsProvider,
                                                                                        @Nonnull final Duration credentialsRefreshIntervalFallback) {

        Preconditions.checkArgument(awsCredentialsProvider != null, "awsCredentialsProvider must not be null");
        Preconditions.checkArgument(credentialsRefreshIntervalFallback != null, "credentialsRefreshIntervalFallback must not be null");

        if (awsCredentialsProvider.getCredentials() instanceof AWSSessionCredentials) {
            return new JavaCredentialsProviderImpl(awsCredentialsProvider, credentialsRefreshIntervalFallback.toMillis());
        } else {
            return new JavaCredentialsProviderImpl(awsCredentialsProvider);
        }
    }
}
