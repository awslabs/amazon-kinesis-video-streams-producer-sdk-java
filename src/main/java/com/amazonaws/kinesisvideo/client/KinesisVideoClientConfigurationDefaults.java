package com.amazonaws.kinesisvideo.client;

import com.amazonaws.kinesisvideo.producer.StorageCallbacks;
import com.amazonaws.kinesisvideo.storage.DefaultStorageCallbacks;

import javax.annotation.Nonnull;

public final class KinesisVideoClientConfigurationDefaults {
    static final String US_WEST_2 = "us-west-2";
    static final String PROD_CONTROL_PLANE_ENDPOINT_FORMAT = "kinesisvideo.%s.amazonaws.com";

    static final int DEVICE_VERSION = 0;
    static final int TEN_STREAMS = 10;
    static final int SPILL_RATIO_90_PERCENT = 90;
    static final int STORAGE_SIZE_256_MEGS = 256 * 1024 * 1024;

    public static final int DEFAULT_SERVICE_CALL_TIMEOUT_IN_MILLIS = 5000;

    static final StorageCallbacks NO_OP_STORAGE_CALLBACKS = new DefaultStorageCallbacks();

    public static String getControlPlaneEndpoint(final @Nonnull String region) {
        return String.format(PROD_CONTROL_PLANE_ENDPOINT_FORMAT, region);
    }

    private KinesisVideoClientConfigurationDefaults() {
        // no-op
    }
}
