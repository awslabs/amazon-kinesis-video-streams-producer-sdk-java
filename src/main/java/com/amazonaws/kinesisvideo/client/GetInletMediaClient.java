package com.amazonaws.kinesisvideo.client;

import lombok.Builder;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.ContentType;

import com.amazonaws.kinesisvideo.http.KinesisVideoApacheHttpClient;
import com.amazonaws.kinesisvideo.http.HttpMethodName;
import com.amazonaws.kinesisvideo.signing.KinesisVideoSigner;

import java.net.URI;

@Builder
@Accessors(prefix = "m")
public final class GetInletMediaClient {

    private static final String CONTENT_TYPE_HEADER_KEY = "Content-Type";
    private static final String X_AMZN_REQUEST_ID = "x-amzn-RequestId";

    private URI mUri;
    private KinesisVideoSigner mSigner;
    private String mGetInletMediaInputInJson;
    private Integer mConnectionTimeoutInMillis;
    private Integer mReadTimeoutInMillis;

    public CloseableHttpResponse execute(@NonNull final String requestId) {
        final KinesisVideoApacheHttpClient client = getHttpClient(requestId);
        return client.executeRequest();
    }

    private KinesisVideoApacheHttpClient getHttpClient(@NonNull final String requestId) {
        KinesisVideoApacheHttpClient.Builder clientBuilder =
                KinesisVideoApacheHttpClient.builder()
                        .withUri(mUri)
                        .withContentType(ContentType.APPLICATION_JSON)
                        .withMethod(HttpMethodName.POST)
                        .withContentInJson(mGetInletMediaInputInJson)
                        .withHeader(CONTENT_TYPE_HEADER_KEY, ContentType.APPLICATION_JSON.getMimeType())
                        .withHeader(X_AMZN_REQUEST_ID, requestId);
        
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
}
