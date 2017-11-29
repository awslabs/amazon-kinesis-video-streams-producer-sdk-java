package com.amazonaws.kinesisvideo.producer.jni;

import com.amazonaws.kinesisvideo.common.logging.Log;
import com.amazonaws.kinesisvideo.producer.AuthCallbacks;
import com.amazonaws.kinesisvideo.producer.AuthInfo;
import com.amazonaws.kinesisvideo.producer.AuthInfoType;
import com.amazonaws.kinesisvideo.producer.Time;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TestAuthCallbacks implements AuthCallbacks {
    public static final long TEST_AUTH_EXPIRATION = Time.getCurrentTime() + 60 * Time.HUNDREDS_OF_NANOS_IN_A_MINUTE; // a minute expiration

    private final Log mLog;

    public TestAuthCallbacks(final @Nonnull Log log) {
        mLog = log;
    }

    @Nullable
    @Override
    public AuthInfo getDeviceCertificate() {
        mLog.verbose("Called getDeviceCertificate");
        return null;
    }

    @Nullable
    @Override
    public AuthInfo getSecurityToken() {
        mLog.verbose("Called getSecurityToken");
        return new AuthInfo(AuthInfoType.SECURITY_TOKEN, NativeKinesisVideoProducerJniTestBase.TEST_AUTH_BITS_STS, TEST_AUTH_EXPIRATION);
    }

    @Nullable
    @Override
    public String getDeviceFingerprint() {
        mLog.verbose("Called getDeviceFingerprint");
        return null;
    }

}