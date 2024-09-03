package com.amazonaws.kinesisvideo.demoapp.auth;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.SystemPropertyCredentialsProvider;

public final class AuthHelper {
    public static AwsCredentialsProvider getSystemPropertiesCredentialsProvider() {
        return SystemPropertyCredentialsProvider.create();
    }

    private AuthHelper() {
        throw new UnsupportedOperationException();
    }
}
