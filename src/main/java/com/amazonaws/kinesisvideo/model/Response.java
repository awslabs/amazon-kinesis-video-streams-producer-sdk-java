package com.amazonaws.kinesisvideo.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.io.InputStream;
import java.util.Map;

/**
 * Response object which is parsed from the http response to status line, headers and the body.
 *
 * @author bdhandap
 */
@Getter
@Builder
@ToString (exclude = {"responseBody", "responsePayload"})
public class Response {

    /**
     * Status line with status code and reason. Eg :400, 404, etc
     */
    private final ResponseStatus responseStatus;

    /**
     * Key value pair of all the headers in the response.
     */
    private final Map<String, String> responseHeaders;

    /**
     * Response body as string if the response contains text response.
     */
    private final String responseBody;

    /**
     * Response as the raw input stream.
     */
    private final InputStream responsePayload;
}
