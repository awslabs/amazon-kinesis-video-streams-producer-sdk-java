package com.amazonaws.kinesisvideo.config;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.net.URI;


@Getter
@Builder
@ToString
public final class ClientConfiguration {
    private String region;
    private String serviceName;
    private String apiName;
    private String materialSet;
    private String streamName;
    private URI streamUri;
    private Integer connectionTimeoutInMillis;
    private Integer readTimeoutInMillis;
}
