package com.amazonaws.kinesisvideo.producer;

import javax.annotation.Nullable;

/**
 *
 * Interface to the Kinesis Video Streams Producer Authentication Callbacks functionality.
 *
 * These will be used to integrate with the Auth
 *
 *
 */
public interface AuthCallbacks
{
    /**
     * Returns the device certificate.
     * Null if not integrated through certificates.
     * @return Device certificate bits
     */
    @Nullable
    AuthInfo getDeviceCertificate();

    /**
     * Returns the device security token.
     * Null if not integrated through security tokens.
     * @return Device security token
     */
    @Nullable
    AuthInfo getSecurityToken();

    /**
     * Returns the device fingerprint uniquely identifying the device.
     * Null if no provisioning is allowed for the device in which case
     * the device should be integrated with either the Certificate
     * model or with the Security token model.
     * @return Device unique fingerprint.
     */
    @Nullable
    String getDeviceFingerprint();

}
