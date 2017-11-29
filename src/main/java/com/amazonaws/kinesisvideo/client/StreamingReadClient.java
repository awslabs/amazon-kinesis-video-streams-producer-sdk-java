package com.amazonaws.kinesisvideo.client;

import java.net.URI;

import lombok.Builder;
import lombok.experimental.Accessors;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.ContentType;

import com.amazonaws.kinesisvideo.http.KinesisVideoApacheHttpClient;
import com.amazonaws.kinesisvideo.http.HttpMethodName;
import com.amazonaws.kinesisvideo.signing.KinesisVideoSigner;

@Builder
@Accessors(prefix = "m")
public class StreamingReadClient {

    private static final String CONTENT_TYPE_HEADER_KEY = "Content-Type";
    
    private URI mUri;
    private KinesisVideoSigner mSigner;
    private String mInputInJson;
    private Integer mConnectionTimeoutInMillis;
    private Integer mReadTimeoutInMillis;

    public CloseableHttpResponse execute() {
        final KinesisVideoApacheHttpClient client = getHttpClient();
        return client.executeRequest();
    }

    private KinesisVideoApacheHttpClient getHttpClient() {
        KinesisVideoApacheHttpClient.Builder clientBuilder =
                KinesisVideoApacheHttpClient.builder()
                        .withUri(mUri)
                        .withContentType(ContentType.APPLICATION_JSON)
                        .withMethod(HttpMethodName.POST)
                        .withContentInJson(mInputInJson)
                        .withHeader(CONTENT_TYPE_HEADER_KEY, ContentType.APPLICATION_JSON.getMimeType());

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
