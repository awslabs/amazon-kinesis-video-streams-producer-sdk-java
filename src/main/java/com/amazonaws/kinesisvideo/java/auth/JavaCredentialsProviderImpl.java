package com.amazonaws.kinesisvideo.java.auth;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.kinesisvideo.auth.AbstractKinesisVideoCredentialsProvider;
import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentials;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;

import javax.annotation.Nonnull;
import java.util.Date;

/**
 * Implementation of the AWS Credentials Provider wrapper for Java
 */
public class JavaCredentialsProviderImpl extends AbstractKinesisVideoCredentialsProvider {

    private final AWSCredentialsProvider credentialsProvider;
    private Date tokenExpiration;
    private final long rotationPeriodInMillis;

    /**
     * Constructor for non-temporary credential provider. Access Key ID + Secret Key (no session token).
     * Also known as long-lived credentials. Not recommended for production use.
     *
     * @param awsCredentialsProvider credential provider
     * @see <a href="https://docs.aws.amazon.com/sdkref/latest/guide/access-users.html">Using AWS access keys to authenticate AWS SDKs and tools</a>
     */
    public JavaCredentialsProviderImpl(@Nonnull final AWSCredentialsProvider awsCredentialsProvider) {
        this.credentialsProvider = Preconditions.checkNotNull(awsCredentialsProvider);
        tokenExpiration = KinesisVideoCredentials.getCredentialsNeverExpire();
        rotationPeriodInMillis = 0;
    }

    /**
     * Constructor for temporary credential provider with token rotation period (has a session token)
     * (i.e. token expires for every 5 minutes).
     * The KVS Producer client will refresh the credentials based on the provider's configured rotation interval.
     * During client initialization, the Producer client fetches the latest credentials once, and then every
     * {@code rotationPeriod} interval afterward.
     *
     * @param awsCredentialsProvider AWS credentials provider to use
     * @param rotationPeriodInMillis Token expires periodically for every rotationPeriodInMillis milliseconds.
     *                               It is important to configure the rotationPeriod to be less than the AWS-configured
     *                               expiration time (e.g. role duration seconds) to prevent the client from using
     *                               stale credentials during API operations.
     * @see #updateCredentials()
     */
    public JavaCredentialsProviderImpl(@Nonnull final AWSCredentialsProvider awsCredentialsProvider,
                                       final long rotationPeriodInMillis) {
        this.credentialsProvider = Preconditions.checkNotNull(awsCredentialsProvider);
        this.rotationPeriodInMillis = rotationPeriodInMillis;
        tokenExpiration = new Date(System.currentTimeMillis() + rotationPeriodInMillis);
    }

    @Override
    @Nonnull
    protected KinesisVideoCredentials updateCredentials() {
        // Refresh the token first
        credentialsProvider.refresh();

        // Get the AWS credentials and create Kinesis Video Credentials
        final AWSCredentials awsCredentials = credentialsProvider.getCredentials();

        String sessionToken = null;
        if (awsCredentials instanceof AWSSessionCredentials) {
            final AWSSessionCredentials sessionCredentials = (AWSSessionCredentials) awsCredentials;
            sessionToken = sessionCredentials.getSessionToken();
        }

        if (!tokenExpiration.equals(KinesisVideoCredentials.getCredentialsNeverExpire())) {
            tokenExpiration = new Date(System.currentTimeMillis() + rotationPeriodInMillis);
        }

        return new KinesisVideoCredentials(awsCredentials.getAWSAccessKeyId(),
                awsCredentials.getAWSSecretKey(),
                sessionToken,
                tokenExpiration);
    }
}
