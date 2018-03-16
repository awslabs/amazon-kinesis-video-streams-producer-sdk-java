package com.amazonaws.kinesisvideo.model;

public class ResponseStatus {
    private final String protocol;
    private final int statusCode;
    private final String reason;

    ResponseStatus(final String protocol, final int statusCode, final String reason) {
        this.protocol = protocol;
        this.statusCode = statusCode;
        this.reason = reason;
    }


    public static class ResponseStatusBuilder {
        private String protocol;
        private int statusCode;
        private String reason;

        ResponseStatusBuilder() {
        }

        public ResponseStatusBuilder protocol(final String protocol) {
            this.protocol = protocol;
            return this;
        }

        public ResponseStatusBuilder statusCode(final int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public ResponseStatusBuilder reason(final String reason) {
            this.reason = reason;
            return this;
        }

        public ResponseStatus build() {
            return new ResponseStatus(protocol, statusCode, reason);
        }

        @Override
        public String toString() {
            return "ResponseStatus.ResponseStatusBuilder(protocol=" + this.protocol + ", statusCode=" + this.statusCode + ", reason=" + this.reason + ")";
        }
    }

    public static ResponseStatusBuilder builder() {
        return new ResponseStatusBuilder();
    }

    public String getProtocol() {
        return this.protocol;
    }

    public int getStatusCode() {
        return this.statusCode;
    }

    public String getReason() {
        return this.reason;
    }

    @Override
    public String toString() {
        return "ResponseStatus(protocol=" + this.getProtocol() + ", statusCode=" + this.getStatusCode() + ", reason=" + this.getReason() + ")";
    }
}
