package com.amazonaws.kinesisvideo.auth;

import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import java.io.Serializable;
import java.util.Date;

/**
 * Credentials object containing AWS credentials. They might be temporary (using a session token).
 * Consumer is responsible for checking the {@link #expiration} before using the credentials.
 * <p>
 * If the credentials are non-temporary, the session token will be {@code null} and the expiration
 * will be {@link #getCredentialsNeverExpire()}.
 * </p>
 */
@Immutable
@ThreadSafe
public class KinesisVideoCredentials implements Serializable {
    /**
     * Internal immutable sentinel value indicating the credentials never expire.
     */
    private static final Date CREDENTIALS_NEVER_EXPIRE_INTERNAL = new Date(Long.MAX_VALUE);
    
    /**
     * Sentinel value indicating the credentials never expire.
     * @deprecated This exposes a mutable Date object. Use {@link #getCredentialsNeverExpire()} instead.
     */
    @Deprecated
    public static final Date CREDENTIALS_NEVER_EXPIRE = new Date(Long.MAX_VALUE);
    
    /**
     * Returns a defensive copy of the sentinel value indicating credentials never expire.
     * This method should be used instead of the deprecated {@link #CREDENTIALS_NEVER_EXPIRE} constant
     * to maintain immutability.
     * 
     * @return A new Date instance representing credentials that never expire
     */
    @Nonnull
    public static Date getCredentialsNeverExpire() {
        return new Date(CREDENTIALS_NEVER_EXPIRE_INTERNAL.getTime());
    }

    /**
     * AWS Access Key ID, non-empty.
     */
    @Nonnull
    private final String accessKey;

    /**
     * AWS Secret Key, non-empty.
     */
    @Nonnull
    private final String secretKey;

    /**
     * AWS Session Token. Will be {@code null} if the credentials are non-temporary. Non-empty.
     */
    @Nullable
    private final String sessionToken;

    /**
     * Credentials are no longer valid after this date.
     */
    @Nonnull
    private final Date expiration;

    /**
     * Constructor for non-temporary credentials.
     *
     * @param accessKey AWS Access Key ID, must not be null or empty
     * @param secretKey AWS Secret Key, must not be null or empty
     * @throws IllegalArgumentException if accessKey or secretKey is null or empty
     */
    public KinesisVideoCredentials(@Nonnull final String accessKey,
                                   @Nonnull final String secretKey) {
        this(accessKey, secretKey, null, CREDENTIALS_NEVER_EXPIRE_INTERNAL);
    }

    /**
     * Constructor for temporary credentials.
     *
     * @param accessKey    AWS Access Key ID, must not be null or empty
     * @param secretKey    AWS Secret Key, must not be null or empty
     * @param sessionToken AWS Session Token, must not be empty if provided
     * @param expiration   When this set of credentials expire, must not be null
     * @throws IllegalArgumentException if accessKey, secretKey, or sessionToken is empty, or
     *                                  if any required parameter is null, or
     *                                  if a session token is provided for non-temporary credentials
     */
    @SuppressWarnings({"ConstantConditions"}) // @Nonnull is a compile-time warning, it can still be null at runtime
    public KinesisVideoCredentials(@Nonnull final String accessKey,
                                   @Nonnull final String secretKey,
                                   @Nullable final String sessionToken,
                                   @Nonnull final Date expiration) {
        Preconditions.checkArgument(accessKey != null && !accessKey.isEmpty(), "Access key cannot be null or empty");
        Preconditions.checkArgument(secretKey != null && !secretKey.isEmpty(), "Secret key cannot be null or empty");
        Preconditions.checkArgument(expiration != null, "Expiration cannot be null");

        if (sessionToken != null) {
            Preconditions.checkArgument(!sessionToken.isEmpty(), "Session token cannot be empty!");
        }

        Preconditions.checkArgument((sessionToken == null && CREDENTIALS_NEVER_EXPIRE_INTERNAL.equals(expiration)) ||
                        (sessionToken != null && !CREDENTIALS_NEVER_EXPIRE_INTERNAL.equals(expiration)),
                "Temporary credentials should have a session token and non-temporary should not!");

        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.sessionToken = sessionToken;
        this.expiration = new Date(expiration.getTime());
    }

    /**
     * @return AWS Access Key ID, non-empty
     */
    @Nonnull
    public String getAccessKey() {
        return this.accessKey;
    }

    /**
     * @return AWS Secret Key, non-empty
     */
    @Nonnull
    public String getSecretKey() {
        return this.secretKey;
    }

    /**
     * @return AWS Session Token. If the credentials are non-temporary, this will be {@code null}. Otherwise, non-empty.
     */
    @Nullable
    public String getSessionToken() {
        return this.sessionToken;
    }

    /**
     * @return When the credentials will no longer be valid. If the credentials are non-temporary,
     * this will be {@link #getCredentialsNeverExpire()}.
     */
    @Nonnull
    public Date getExpiration() {
        return new Date(this.expiration.getTime());
    }

    /**
     * Checks if the credentials are temporary or not.
     *
     * @return true if the credentials are temporary (have an expiration).
     */
    public boolean isTemporary() {
        return !CREDENTIALS_NEVER_EXPIRE_INTERNAL.equals(this.expiration);
    }
}
