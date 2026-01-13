package com.amazonaws.kinesisvideo.client;

import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentialsProvider;
import com.amazonaws.kinesisvideo.java.auth.JavaCredentialsProviderImpl;
import com.amazonaws.kinesisvideo.producer.StorageCallbacks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Configuration for {@link com.amazonaws.kinesisvideo.internal.producer.client.KinesisVideoServiceClient}.
 */
public final class KinesisVideoClientConfiguration {

    private static final Logger log = LogManager.getLogger(KinesisVideoClientConfiguration.class);

    private final String region;
    private final KinesisVideoCredentialsProvider credentialsProvider;
    private final StorageCallbacks storageCallbacks;
    private final String endpoint;
    private final IPVersionFilter ipVersionFilter;
    private final boolean isInstrumentedAllocatorsEnabled;

    private KinesisVideoClientConfiguration(final Builder builder) {
        this.region = builder.region;
        this.credentialsProvider = builder.credentialsProvider;
        this.storageCallbacks = builder.storageCallbacks;
        this.endpoint = builder.endpoint;
        this.ipVersionFilter = builder.ipVersionFilter;
        this.isInstrumentedAllocatorsEnabled = builder.useInstrumentedAllocators;
    }

    public static Builder builder() {
        return new Builder();
    }

    private static void sanitizeBuilder(@Nonnull final Builder builder) {
        final boolean isLegacyEndpoint = builder.isLegacyEndpoint.orElse(KinesisVideoClientConfigurationDefaults.USE_LEGACY_ENDPOINT);

        if (builder.region == null && builder.endpoint == null) {
            builder.withRegion(KinesisVideoClientConfigurationDefaults.US_WEST_2);

            if (isLegacyEndpoint) {
                builder.withEndpoint(KinesisVideoClientConfigurationDefaults
                        .getControlPlaneEndpoint(builder.region));
            } else {
                builder.withEndpoint(KinesisVideoClientConfigurationDefaults
                        .getDualStackControlPlaneEndpoint(builder.region));
            }

            log.info("Using default region: {}", builder.region);
        }

        if (builder.region == null) {
            // TODO: determine from endpoint?
            builder.withRegion(KinesisVideoClientConfigurationDefaults.US_WEST_2);
            log.info("Using default region: {}", builder.region);
        }

        if (builder.endpoint == null) {
            if (isLegacyEndpoint) {
                builder.withEndpoint(KinesisVideoClientConfigurationDefaults
                        .getControlPlaneEndpoint(builder.region));
            } else {
                builder.withEndpoint(KinesisVideoClientConfigurationDefaults
                        .getDualStackControlPlaneEndpoint(builder.region));
            }
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

    public IPVersionFilter getIpVersionFilter() {
        return this.ipVersionFilter;
    }

    public boolean isInstrumentedAllocatorsEnabled() { return this.isInstrumentedAllocatorsEnabled;}

    @Override
    public String toString() {
        return "KinesisVideoClientConfiguration{" +
                "region='" + region + '\'' +
                ", credentialsProvider=" + (credentialsProvider != null ? credentialsProvider.getClass().getSimpleName() : "null") +
                ", storageCallbacks=" + (storageCallbacks != null ? storageCallbacks.getClass().getSimpleName() : "null") +
                ", endpoint='" + endpoint + '\'' +
                ", ipVersionFilter=" + ipVersionFilter +
                ", isInstrumentedAllocatorsEnabled=" + isInstrumentedAllocatorsEnabled +
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
        private boolean useInstrumentedAllocators = false;

        public Builder withRegion(final String region) {
            this.region = region;
            return this;
        }

        /**
         * Credentials Provider to fetch credentials from, and how often to rotate the credentials.
         *
         * @param credentialsProvider The KVS Producer client will refresh the credentials
         *                            based on the provider's configured rotation interval. During client initialization,
         *                            the Producer client fetches the latest credentials once, and then every
         *                            {@code rotationPeriod} interval afterward.
         *                            <p>
         *                            The client will pass these cached credentials to the {@link com.amazonaws.kinesisvideo.internal.producer.ServiceCallbacks}
         *                            for ControlPlane operations. It is ServiceCallbacks responsibility to construct
         *                            the request with the provided serialized credentials.
         *                            <p>
         *                            For PutMedia (DataPlane) operation, the client will fetch the credentials
         *                            via the stream's GetStreamingToken state, then pass those serialized credentials
         *                            to the PutStream operation.
         *                            <p>
         *                            It is important to configure the rotationPeriod to be less than the AWS-configured
         *                            expiration time (e.g. role duration seconds) to prevent the client from using
         *                            stale credentials during API operations.
         *                            <p>
         *                            Use {@link com.amazonaws.kinesisvideo.java.auth.JavaCredentialsFactory} for constructor convenience.
         * @return This Builder instance to allow method chaining
         * @see JavaCredentialsProviderImpl#updateCredentials()
         */
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

        public Builder withIsLegacyEndpoint(final Boolean isLegacyEndpoint) {
            this.isLegacyEndpoint = Optional.ofNullable(isLegacyEndpoint);
            return this;
        }

        public Builder withIPVersionFilter(final IPVersionFilter ipVersionFilter) {
            this.ipVersionFilter = ipVersionFilter;
            return this;
        }

        public Builder useInstrumentedAllocators() {
            this.useInstrumentedAllocators = true;
            return this;
        }

        public KinesisVideoClientConfiguration build() {
            sanitizeBuilder(this);
            return new KinesisVideoClientConfiguration(this);
        }
    }
}
