package com.amazonaws.kinesisvideo.util;

import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;

import javax.annotation.Nonnull;

public final class VersionUtil {

    public static final String AWS_SDK_KVS_PRODUCER_VERSION_STRING = "1.9.5";

    private static final String DEFAULT_USER_AGENT_NAME = "AWS-SDK-KVS";

    private static final String SYSTEM_INFORMATION_STRING = new StringBuilder().append("JAVA/")
            .append(System.getProperty("java.version")).append(' ')
            .append(System.getProperty("os.name").replace(' ', '_')).append('/')
            .append(System.getProperty("os.version"))
            .append(' ').append(System.getProperty("os.arch")).toString();

    private VersionUtil() {
        throw new UnsupportedOperationException();
    }

    public static String getUserAgent(@Nonnull final String userAgentName) {
        Preconditions.checkNotNull(userAgentName);
        return new StringBuilder().append(userAgentName).append('/').append(AWS_SDK_KVS_PRODUCER_VERSION_STRING)
                .append(' ').append(SYSTEM_INFORMATION_STRING).toString();

    }

    public static String getUserAgent() {
        return getUserAgent(DEFAULT_USER_AGENT_NAME);
    }
}

