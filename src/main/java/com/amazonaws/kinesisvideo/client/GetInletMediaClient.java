package com.amazonaws.kinesisvideo.client;


import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.ContentType;
import com.amazonaws.kinesisvideo.http.KinesisVideoApacheHttpClient;
import com.amazonaws.kinesisvideo.http.HttpMethodName;
import com.amazonaws.kinesisvideo.signing.KinesisVideoSigner;
import java.net.URI;

public final class GetInletMediaClient {
    private static final String CONTENT_TYPE_HEADER_KEY = "Content-Type";
    private static final String X_AMZN_REQUEST_ID = "x-amzn-RequestId";
    private URI mUri;
    private KinesisVideoSigner mSigner;
    private String mGetInletMediaInputInJson;
    private Integer mConnectionTimeoutInMillis;
    private Integer mReadTimeoutInMillis;

    public CloseableHttpResponse execute( final String requestId) {
        if (requestId == null) {
            throw new NullPointerException("requestId");
        }
        final KinesisVideoApacheHttpClient client = getHttpClient(requestId);
        return client.executeRequest();
    }

    private KinesisVideoApacheHttpClient getHttpClient( final String requestId) {
        if (requestId == null) {
            throw new NullPointerException("requestId");
        }
        KinesisVideoApacheHttpClient.Builder clientBuilder = KinesisVideoApacheHttpClient.builder().withUri(mUri).withContentType(ContentType.APPLICATION_JSON).withMethod(HttpMethodName.POST).withContentInJson(mGetInletMediaInputInJson).withHeader(CONTENT_TYPE_HEADER_KEY, ContentType.APPLICATION_JSON.getMimeType()).withHeader(X_AMZN_REQUEST_ID, requestId);
        if (mConnectionTimeoutInMillis != null) {
            clientBuilder = clientBuilder.withConnectionTimeoutInMillis(mConnectionTimeoutInMillis.intValue());
        }
        if (mReadTimeoutInMillis != null) {
            clientBuilder = clientBuilder.withSocketTimeoutInMillis(mReadTimeoutInMillis.intValue());
        }
        final KinesisVideoApacheHttpClient client = clientBuilder.build();
        mSigner.sign(client);
        return client;
    }

    GetInletMediaClient(final URI uri, final KinesisVideoSigner signer, final String getInletMediaInputInJson, final Integer connectionTimeoutInMillis, final Integer readTimeoutInMillis) {
        this.mUri = uri;
        this.mSigner = signer;
        this.mGetInletMediaInputInJson = getInletMediaInputInJson;
        this.mConnectionTimeoutInMillis = connectionTimeoutInMillis;
        this.mReadTimeoutInMillis = readTimeoutInMillis;
    }


    public static class GetInletMediaClientBuilder {
        private URI uri;
        private KinesisVideoSigner signer;
        private String getInletMediaInputInJson;
        private Integer connectionTimeoutInMillis;
        private Integer readTimeoutInMillis;

        GetInletMediaClientBuilder() {
        }

        public GetInletMediaClientBuilder uri(final URI uri) {
            this.uri = uri;
            return this;
        }

        public GetInletMediaClientBuilder signer(final KinesisVideoSigner signer) {
            this.signer = signer;
            return this;
        }

        public GetInletMediaClientBuilder getInletMediaInputInJson(final String getInletMediaInputInJson) {
            this.getInletMediaInputInJson = getInletMediaInputInJson;
            return this;
        }

        public GetInletMediaClientBuilder connectionTimeoutInMillis(final Integer connectionTimeoutInMillis) {
            this.connectionTimeoutInMillis = connectionTimeoutInMillis;
            return this;
        }

        public GetInletMediaClientBuilder readTimeoutInMillis(final Integer readTimeoutInMillis) {
            this.readTimeoutInMillis = readTimeoutInMillis;
            return this;
        }

        public GetInletMediaClient build() {
            return new GetInletMediaClient(uri, signer, getInletMediaInputInJson, connectionTimeoutInMillis, readTimeoutInMillis);
        }

        @Override
        public String toString() {
            return "GetInletMediaClient.GetInletMediaClientBuilder(uri=" + this.uri + ", signer=" + this.signer + ", getInletMediaInputInJson=" + this.getInletMediaInputInJson + ", connectionTimeoutInMillis=" + this.connectionTimeoutInMillis + ", readTimeoutInMillis=" + this.readTimeoutInMillis + ")";
        }
    }

    public static GetInletMediaClientBuilder builder() {
        return new GetInletMediaClientBuilder();
    }
}
