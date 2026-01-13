package com.amazonaws.kinesisvideo.java.auth;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentials;
import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentialsProvider;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.time.Duration;

/**
 * Turns an AWS credentials provider (which vends AWS credentials) into {@link KinesisVideoCredentialsProvider} (which vends {@link KinesisVideoCredentials}).
 * There are two types of KinesisVideoCredentialsProvider:
 * <ul>
 *     <li>One that provides temporary ephemeral credentials (session token)</li>
 *     <li>And static that just provides access key + secret key (also known as long-lived credentials)</li>
 * </ul>
 *
 * @see JavaCredentialsProviderImpl
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
     *                                           But <strong>should NOT be greater than the configured AWS expiration</strong> (e.g. role duration seconds).
     *                                           <p>
     *                                           If the AWSCredentialsProvider returns non-expiring credentials
     *                                           (<strong>not recommended for production use</strong>),
     *                                           use {@link KinesisVideoCredentials#getCredentialsNeverExpire()}.
     * @return the correct (temporary/non-temporary) Kinesis Video Credentials Provider based on the AWS Credentials Provider
     * @see <a href="https://docs.aws.amazon.com/sdkref/latest/guide/access-users.html">Using AWS access keys to authenticate AWS SDKs and tools</a>
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
