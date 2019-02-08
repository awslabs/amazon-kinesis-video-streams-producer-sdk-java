package com.amazonaws.kinesisvideo.java.auth;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.kinesisvideo.auth.AbstractKinesisVideoCredentialsProvider;
import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentials;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
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
     * Constructor for non-temporary credential provider
     *
     * @param awsCredentialsProvider credential provider
     */
    public JavaCredentialsProviderImpl(@Nonnull final AWSCredentialsProvider awsCredentialsProvider) {
        this.credentialsProvider = Preconditions.checkNotNull(awsCredentialsProvider);
        tokenExpiration = KinesisVideoCredentials.CREDENTIALS_NEVER_EXPIRE;
        rotationPeriodInMillis = 0;
    }

    /**
     * Constructor for temporary credential provider with token rotation period
     * (i.e. token expires for every 5 minutes)
     *
     * @param awsCredentialsProvider credential provider
     * @param rotationPeriodInMillis token expire periodically for every rotationPeriodInMillis milliseconds
     */
    public JavaCredentialsProviderImpl(@Nonnull final AWSCredentialsProvider awsCredentialsProvider,
                                       final long rotationPeriodInMillis) {
        this.credentialsProvider = Preconditions.checkNotNull(awsCredentialsProvider);
        this.rotationPeriodInMillis = rotationPeriodInMillis;
        tokenExpiration = new Date(System.currentTimeMillis() + rotationPeriodInMillis);
    }

    @Override
    protected KinesisVideoCredentials updateCredentials() throws KinesisVideoException {
        // Refresh the token first
        credentialsProvider.refresh();

        // Get the AWS credentials and create Kinesis Video Credentials
        final AWSCredentials awsCredentials = credentialsProvider.getCredentials();

        String sessionToken = null;
        if (awsCredentials instanceof AWSSessionCredentials) {
            final AWSSessionCredentials sessionCredentials = (AWSSessionCredentials) awsCredentials;
            sessionToken = sessionCredentials.getSessionToken();
        }

        if (tokenExpiration != KinesisVideoCredentials.CREDENTIALS_NEVER_EXPIRE) {
            tokenExpiration = new Date(System.currentTimeMillis() + rotationPeriodInMillis);
        }

        return new KinesisVideoCredentials(awsCredentials.getAWSAccessKeyId(),
                awsCredentials.getAWSSecretKey(),
                sessionToken,
                tokenExpiration);
    }
}
