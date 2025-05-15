package com.amazonaws.kinesisvideo.auth;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;

import java.io.Serializable;
import java.util.Date;

@SuppressFBWarnings("EI_EXPOSE_REP")
public class KinesisVideoCredentials implements Serializable{
    /**
     * Sentinel value indicating the credentials never expire
     */
    public static final Date CREDENTIALS_NEVER_EXPIRE = new Date(Long.MAX_VALUE);

    public static final KinesisVideoCredentials EMPTY_KINESIS_VIDEO_CREDENTIALS = new KinesisVideoCredentials("","");

    private final String accessKey;
    private final String secretKey;
    private final String sessionToken;
    private final Date expiration;

    public KinesisVideoCredentials(@Nonnull final String accessKey,
                             @Nonnull final String secretKey) {
        this(accessKey, secretKey, null, CREDENTIALS_NEVER_EXPIRE);
    }

    public KinesisVideoCredentials(@Nonnull final String accessKey,
                             @Nonnull final String secretKey,
                             @Nullable final String sessionToken,
                             @Nonnull final Date expiration) {
        this.accessKey = Preconditions.checkNotNull(accessKey);
        this.secretKey = Preconditions.checkNotNull(secretKey);
        this.sessionToken = sessionToken;
        this.expiration = expiration;
    }

    @Nonnull
    public String getAccessKey() {
        return accessKey;
    }

    @Nonnull
    public String getSecretKey() {
        return secretKey;
    }

    @Nullable
    public String getSessionToken() {
        return sessionToken;
    }

    @Nonnull
    public Date getExpiration() {
        return expiration;
    }
}
