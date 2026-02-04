package com.amazonaws.kinesisvideo.config;

import com.amazonaws.kinesisvideo.client.IPVersionFilter;
import com.amazonaws.kinesisvideo.client.KinesisVideoClientConfigurationDefaults;

import java.net.URI;

public final class ClientConfiguration {
    private String region;
    private String serviceName;
    private String apiName;
    private String materialSet;
    private String streamName;
    private URI streamUri;
    private Integer connectionTimeoutInMillis;
    private Integer readTimeoutInMillis;
    private IPVersionFilter ipVersionFilter;

    ClientConfiguration(final String region, final String serviceName, final String apiName, final String materialSet, final String streamName, final URI streamUri, final Integer connectionTimeoutInMillis, final Integer readTimeoutInMillis, final IPVersionFilter ipVersionFilter) {
        this.region = region;
        this.serviceName = serviceName;
        this.apiName = apiName;
        this.materialSet = materialSet;
        this.streamName = streamName;
        this.streamUri = streamUri;
        this.connectionTimeoutInMillis = connectionTimeoutInMillis;
        this.readTimeoutInMillis = readTimeoutInMillis;
    }


    public static class ClientConfigurationBuilder {
        private String region;
        private String serviceName;
        private String apiName;
        private String materialSet;
        private String streamName;
        private URI streamUri;
        private Integer connectionTimeoutInMillis;
        private Integer readTimeoutInMillis;
        private IPVersionFilter ipVersionFilter;

        ClientConfigurationBuilder() {
        }

        public ClientConfigurationBuilder region(final String region) {
            this.region = region;
            return this;
        }

        public ClientConfigurationBuilder serviceName(final String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public ClientConfigurationBuilder apiName(final String apiName) {
            this.apiName = apiName;
            return this;
        }

        public ClientConfigurationBuilder materialSet(final String materialSet) {
            this.materialSet = materialSet;
            return this;
        }

        public ClientConfigurationBuilder streamName(final String streamName) {
            this.streamName = streamName;
            return this;
        }

        public ClientConfigurationBuilder streamUri(final URI streamUri) {
            this.streamUri = streamUri;
            return this;
        }

        public ClientConfigurationBuilder connectionTimeoutInMillis(final Integer connectionTimeoutInMillis) {
            this.connectionTimeoutInMillis = connectionTimeoutInMillis;
            return this;
        }

        public ClientConfigurationBuilder readTimeoutInMillis(final Integer readTimeoutInMillis) {
            this.readTimeoutInMillis = readTimeoutInMillis;
            return this;
        }

        public ClientConfigurationBuilder ipVersionFilter(final IPVersionFilter ipVersionFilter) {
            this.ipVersionFilter = ipVersionFilter;
            return this;
        }

        public ClientConfiguration build() {
            if (ipVersionFilter == null) {
                this.ipVersionFilter = KinesisVideoClientConfigurationDefaults.BOTH_IPV4_AND_IPV6;
            }
            return new ClientConfiguration(region, serviceName, apiName, materialSet, streamName, streamUri, connectionTimeoutInMillis, readTimeoutInMillis, ipVersionFilter);
        }

        @Override
        public String toString() {
            return "ClientConfiguration.ClientConfigurationBuilder(region=" + this.region + ", serviceName=" + this.serviceName + ", apiName=" + this.apiName + ", materialSet=" + this.materialSet + ", streamName=" + this.streamName + ", streamUri=" + this.streamUri + ", connectionTimeoutInMillis=" + this.connectionTimeoutInMillis + ", readTimeoutInMillis=" + this.readTimeoutInMillis + ", ipVersionFilter=" + this.ipVersionFilter + ")";
        }
    }

    public static ClientConfigurationBuilder builder() {
        return new ClientConfigurationBuilder();
    }

    public String getRegion() {
        return this.region;
    }

    public String getServiceName() {
        return this.serviceName;
    }

    public String getApiName() {
        return this.apiName;
    }

    public String getMaterialSet() {
        return this.materialSet;
    }

    public String getStreamName() {
        return this.streamName;
    }

    public URI getStreamUri() {
        return this.streamUri;
    }

    public Integer getConnectionTimeoutInMillis() {
        return this.connectionTimeoutInMillis;
    }

    public Integer getReadTimeoutInMillis() {
        return this.readTimeoutInMillis;
    }
    public IPVersionFilter getIpVersionFilter() {
        return this.ipVersionFilter;
    }

    @Override
    public String toString() {
        return "ClientConfiguration(region=" + this.getRegion() + ", serviceName=" + this.getServiceName() + ", apiName=" + this.getApiName() + ", materialSet=" + this.getMaterialSet() + ", streamName=" + this.getStreamName() + ", streamUri=" + this.getStreamUri() + ", connectionTimeoutInMillis=" + this.getConnectionTimeoutInMillis() + ", readTimeoutInMillis=" + this.getReadTimeoutInMillis() + ")";
    }
}
