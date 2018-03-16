package com.amazonaws.kinesisvideo.model;

import java.io.InputStream;
import java.util.Map;

/**
 * Response object which is parsed from the http response to status line, headers and the body.
 *
 * @author bdhandap
 */
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

    Response(final ResponseStatus responseStatus, final Map<String, String> responseHeaders, final String responseBody, final InputStream responsePayload) {
        this.responseStatus = responseStatus;
        this.responseHeaders = responseHeaders;
        this.responseBody = responseBody;
        this.responsePayload = responsePayload;
    }


    public static class ResponseBuilder {
        private ResponseStatus responseStatus;
        private Map<String, String> responseHeaders;
        private String responseBody;
        private InputStream responsePayload;

        ResponseBuilder() {
        }

        public ResponseBuilder responseStatus(final ResponseStatus responseStatus) {
            this.responseStatus = responseStatus;
            return this;
        }

        public ResponseBuilder responseHeaders(final Map<String, String> responseHeaders) {
            this.responseHeaders = responseHeaders;
            return this;
        }

        public ResponseBuilder responseBody(final String responseBody) {
            this.responseBody = responseBody;
            return this;
        }

        public ResponseBuilder responsePayload(final InputStream responsePayload) {
            this.responsePayload = responsePayload;
            return this;
        }

        public Response build() {
            return new Response(responseStatus, responseHeaders, responseBody, responsePayload);
        }

        @Override
        public String toString() {
            return "Response.ResponseBuilder(responseStatus=" + this.responseStatus + ", responseHeaders=" + this.responseHeaders + ", responseBody=" + this.responseBody + ", responsePayload=" + this.responsePayload + ")";
        }
    }

    public static ResponseBuilder builder() {
        return new ResponseBuilder();
    }

    /**
     * Status line with status code and reason. Eg :400, 404, etc
     */
    public ResponseStatus getResponseStatus() {
        return this.responseStatus;
    }

    /**
     * Key value pair of all the headers in the response.
     */
    public Map<String, String> getResponseHeaders() {
        return this.responseHeaders;
    }

    /**
     * Response body as string if the response contains text response.
     */
    public String getResponseBody() {
        return this.responseBody;
    }

    /**
     * Response as the raw input stream.
     */
    public InputStream getResponsePayload() {
        return this.responsePayload;
    }

    @Override
    public String toString() {
        return "Response(responseStatus=" + this.getResponseStatus() + ", responseHeaders=" + this.getResponseHeaders() + ")";
    }
}
