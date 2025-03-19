package com.amazonaws.kinesisvideo.client;

import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentialsProvider;
import com.amazonaws.kinesisvideo.producer.StorageCallbacks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.lang.invoke.MethodHandles;
import java.util.Optional;

/**
 * Configuration for KinesisVideoClient.
 */
public final class KinesisVideoClientConfiguration {

    private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    private final String region;
    private final KinesisVideoCredentialsProvider credentialsProvider;
    private final StorageCallbacks storageCallbacks;
    private final String endpoint;
    private final IPVersionFilter ipVersionFilter;
    private KinesisVideoClientConfiguration(final Builder builder) {
        this.region = builder.region;
        this.credentialsProvider = builder.credentialsProvider;
        this.storageCallbacks = builder.storageCallbacks;
        this.endpoint = builder.endpoint;
        this.ipVersionFilter = builder.ipVersionFilter;
    }

    public static Builder builder() {
        return new Builder();
    }

    private static void sanitizeBuilder(@Nonnull final Builder builder) {
        final boolean isLegacyEndpoint = builder.isLegacyEndpoint.orElse(KinesisVideoClientConfigurationDefaults.USE_LEGACY_ENDPOINT);

        if (builder.region == null && builder.endpoint == null) {
            builder.withRegion(KinesisVideoClientConfigurationDefaults.US_WEST_2);
            builder.withEndpoint(KinesisVideoClientConfigurationDefaults.getControlPlaneEndpoint(builder.region, isLegacyEndpoint));

            log.info("Using default region: {}", builder.region);
        }

        if (builder.region == null) {
            // TODO: determine from endpoint?
            builder.withRegion(KinesisVideoClientConfigurationDefaults.US_WEST_2);
            log.info("Using default region: {}", builder.region);
        }

        if (builder.endpoint == null) {
            builder.withEndpoint(KinesisVideoClientConfigurationDefaults.getControlPlaneEndpoint(builder.region, isLegacyEndpoint));
        }

        if (builder.ipVersionFilter == null) {
            builder.withIPVersionFilter(KinesisVideoClientConfigurationDefaults.BOTH_IPV4_AND_IPV6);
        }

        builder.withIsLegacyEndpoint(isLegacyEndpoint);
    }

    public String getServiceName() {
        return "kinesisvideo";
    }

    public String getRegion() {
        return this.region;
    }

    public KinesisVideoCredentialsProvider getCredentialsProvider() {
        return this.credentialsProvider;
    }

    public StorageCallbacks getStorageCallbacks() {
        return this.storageCallbacks;
    }

    public String getEndpoint() {
        return this.endpoint;
    }

    public IPVersionFilter getIpVersionFilter() { return this.ipVersionFilter; }

    @Override
    public String toString() {
        return "KinesisVideoClientConfiguration{" +
                "region='" + region + '\'' +
                ", credentialsProvider=" + (credentialsProvider != null ? credentialsProvider.getClass().getSimpleName() : "null") +
                ", storageCallbacks=" + (storageCallbacks != null ? storageCallbacks.getClass().getSimpleName() : "null") +
                ", endpoint='" + endpoint + '\'' +
                ", ipVersionFilter=" + ipVersionFilter +
                '}';
    }

    public static class Builder {
        private String region;
        private KinesisVideoCredentialsProvider credentialsProvider;
        private StorageCallbacks storageCallbacks =
                KinesisVideoClientConfigurationDefaults.NO_OP_STORAGE_CALLBACKS;
        private String endpoint;
        private Optional<Boolean> isLegacyEndpoint = Optional.empty();
        private IPVersionFilter ipVersionFilter;

        public Builder withRegion(final String region) {
            this.region = region;
            return this;
        }

        public Builder withCredentialsProvider(final KinesisVideoCredentialsProvider credentialsProvider) {
            this.credentialsProvider = credentialsProvider;
            return this;
        }

        public Builder withStorageCallbacks(final StorageCallbacks storageCallbacks) {
            this.storageCallbacks = storageCallbacks;
            return this;
        }

        public Builder withEndpoint(final String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder withIsLegacyEndpoint(final boolean isLegacyEndpoint) {
            this.isLegacyEndpoint = Optional.of(isLegacyEndpoint);
            return this;
        }

        public Builder withIPVersionFilter(final IPVersionFilter ipVersionFilter) {
            this.ipVersionFilter = ipVersionFilter;
            return this;
        }

        public KinesisVideoClientConfiguration build() {
            sanitizeBuilder(this);
            return new KinesisVideoClientConfiguration(this);
        }
    }
}
