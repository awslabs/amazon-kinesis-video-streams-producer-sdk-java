package com.amazonaws.kinesisvideo.producer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.Charset;

/**
 * Device Auth Info object.
 * <p>
 * NOTE: This object will be used by the native code and resembles AuthInfo structure in the native codebase.
 * <p>
 * NOTE: Suppressing Findbug error as this code will be accessed from native codebase.
 */
@SuppressFBWarnings("EI_EXPOSE_REP")
public class AuthInfo {

    /**
     * Current version of the object as defined in the native code client/Include.h
     */
    private static final int AUTH_INFO_CURRENT_VERSION = 0;

    private final byte[] mData;
    private final long mExpiration;
    private final int mVersion;
    private final AuthInfoType mAuthType;

    /**
     * Public constructor
     * @param authType Authentication type
     * @param data String representation or NULL
     * @param expiration Expiration in absolute time in 100ns
     */
    public AuthInfo(@Nonnull AuthInfoType authType, @Nullable final String data, long expiration) {
        this(authType, data == null ? null : data.getBytes(Charset.defaultCharset()), expiration);
    }

    /**
     * Public constructor
     * @param authType Authentication type
     * @param data Acual bits of the auth or NULL
     * @param expiration Expiration in absolute time in 100ns
     */
    public AuthInfo(@Nonnull AuthInfoType authType, @Nullable final byte[] data, long expiration) {
        mAuthType = authType;
        mData = data;
        mExpiration = expiration;
        mVersion = AUTH_INFO_CURRENT_VERSION;
    }

    @Nonnull
    public AuthInfoType getAuthType() {
        return mAuthType;
    }

    public int getIntAuthType() {
        return mAuthType.getIntType();
    }

    @Nullable
    public byte[] getData() {
        return mData;
    }

    public long getExpiration() {
        return mExpiration;
    }

    public int getVersion() {
        return mVersion;
    }
}
