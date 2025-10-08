package com.amazonaws.kinesisvideo.java.auth;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.kinesisvideo.auth.AbstractKinesisVideoCredentialsProvider;
import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentials;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.Date;

/**
 * Implementation of the AWS Credentials Provider wrapper for Java
 */
public class JavaCredentialsProviderImpl extends AbstractKinesisVideoCredentialsProvider {

    private static final Logger log = LogManager.getLogger(JavaCredentialsProviderImpl.class);

    private final AWSCredentialsProvider credentialsProvider;
    private Date tokenExpiration;
    private final long rotationPeriodInMillis;

    /**
     * Constructor for non-temporary credential provider. Access Key ID + Secret Key (no session token)
     *
     * @param awsCredentialsProvider credential provider
     */
    public JavaCredentialsProviderImpl(@Nonnull final AWSCredentialsProvider awsCredentialsProvider) {
        this.credentialsProvider = Preconditions.checkNotNull(awsCredentialsProvider);
        tokenExpiration = KinesisVideoCredentials.getCredentialsNeverExpire();
        rotationPeriodInMillis = 0;
    }

    /**
     * Constructor for temporary credential provider with token rotation period (has a session token)
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
    @Nonnull
    protected KinesisVideoCredentials updateCredentials() {
        final long startTime = System.currentTimeMillis();
        log.debug("UpdateCredentials was called. Starting refresh");

        // Refresh the token first
        credentialsProvider.refresh();

        final long refreshTime = System.currentTimeMillis() - startTime;
        log.debug("Refresh took: {}ms, retrieving credentials", refreshTime);

        // Get the AWS credentials and create Kinesis Video Credentials
        final AWSCredentials awsCredentials = credentialsProvider.getCredentials();
        log.debug("GetCredentials took: {}ms", System.currentTimeMillis() - refreshTime);

        String sessionToken = null;
        if (awsCredentials instanceof AWSSessionCredentials) {
            final AWSSessionCredentials sessionCredentials = (AWSSessionCredentials) awsCredentials;
            sessionToken = sessionCredentials.getSessionToken();
        }

        if (!tokenExpiration.equals(KinesisVideoCredentials.getCredentialsNeverExpire())) {
            tokenExpiration = new Date(System.currentTimeMillis() + rotationPeriodInMillis);
        }

        log.debug("Refreshed credentials with expiration: {}", tokenExpiration);
        return new KinesisVideoCredentials(awsCredentials.getAWSAccessKeyId(),
                awsCredentials.getAWSSecretKey(),
                sessionToken,
                tokenExpiration);
    }
}
