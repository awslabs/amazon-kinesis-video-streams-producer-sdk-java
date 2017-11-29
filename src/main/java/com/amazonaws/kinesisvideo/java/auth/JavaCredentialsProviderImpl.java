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

    public JavaCredentialsProviderImpl(@Nonnull final AWSCredentialsProvider awsCredentialsProvider) {
        this.credentialsProvider = Preconditions.checkNotNull(awsCredentialsProvider);
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

        Date expiration = KinesisVideoCredentials.CREDENTIALS_NEVER_EXPIRE;

        return new KinesisVideoCredentials(awsCredentials.getAWSAccessKeyId(),
                awsCredentials.getAWSSecretKey(),
                sessionToken,
                expiration);
    }
}
