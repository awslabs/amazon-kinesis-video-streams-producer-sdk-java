package com.amazonaws.kinesisvideo.client;

import com.amazonaws.kinesisvideo.producer.StorageCallbacks;
import com.amazonaws.kinesisvideo.storage.DefaultStorageCallbacks;

import javax.annotation.Nonnull;

public final class KinesisVideoClientConfigurationDefaults {
    static final String US_WEST_2 = "us-west-2";
    static final String PROD_CONTROL_PLANE_ENDPOINT_FORMAT = "kinesisvideo.%s.amazonaws.com";
    static final String PROD_CONTROL_PLANE_ENDPOINT_FORMAT_CN = "kinesisvideo.%s.amazonaws.com.cn";
    static final String PROD_CONTROL_PLANE_ENDPOINT_FORMAT_DUAL_STACK = "kinesisvideo.%s.api.aws";
    static final String PROD_CONTROL_PLANE_ENDPOINT_FORMAT_DUAL_STACK_CN = "kinesisvideo.%s.api.amazonwebservices.com.cn";
    static final boolean USE_LEGACY_ENDPOINT = true;

    public static final IPVersionFilter BOTH_IPV4_AND_IPV6 = IPVersionFilter.IPV4_AND_IPV6; // Basically no filter

    static final int DEVICE_VERSION = 0;
    static final int TEN_STREAMS = 10;
    static final int SPILL_RATIO_90_PERCENT = 90;
    static final int STORAGE_SIZE_256_MEGS = 256 * 1024 * 1024;

    public static final int DEFAULT_SERVICE_CALL_TIMEOUT_IN_MILLIS = 5000;

    static final StorageCallbacks NO_OP_STORAGE_CALLBACKS = new DefaultStorageCallbacks();

    /**
     * Construct KVS control plane legacy endpoint (excluding {@code https://}).
     *
     * @param region region
     * @return legacy endpoint host name
     */
    public static String getControlPlaneEndpoint(final @Nonnull String region) {
        if (region.startsWith("cn-")) {
            return String.format(PROD_CONTROL_PLANE_ENDPOINT_FORMAT_CN, region);
        }
        return String.format(PROD_CONTROL_PLANE_ENDPOINT_FORMAT, region);
    }

    /**
     * Construct either the KVS control plane legacy or dual-stack endpoint (excluding {@code https://}).
     *
     * @param region region
     * @param isLegacyEndpoint which endpoint to construct (false = dual-stack)
     * @return endpoint host name
     */
    public static String getControlPlaneEndpoint(final @Nonnull String region, final boolean isLegacyEndpoint) {
        if (isLegacyEndpoint) {
            return getControlPlaneEndpoint(region);
        }

        if (region.startsWith("cn-")) {
            return String.format(PROD_CONTROL_PLANE_ENDPOINT_FORMAT_DUAL_STACK_CN, region);
        }
        return String.format(PROD_CONTROL_PLANE_ENDPOINT_FORMAT_DUAL_STACK, region);
    }

    private KinesisVideoClientConfigurationDefaults() {
        // no-op
    }
}
