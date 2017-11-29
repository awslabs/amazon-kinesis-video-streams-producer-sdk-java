package com.amazonaws.kinesisvideo.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class ResponseStatus {
    private final String protocol;
    private final int statusCode;
    private final String reason;
}
