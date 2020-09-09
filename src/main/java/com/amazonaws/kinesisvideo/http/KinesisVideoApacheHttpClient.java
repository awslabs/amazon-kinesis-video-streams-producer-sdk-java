package com.amazonaws.kinesisvideo.http;

import static com.amazonaws.kinesisvideo.common.preconditions.Preconditions.checkNotNull;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * Http Client which uses Apache HttpClient internally to make
 * the http request and get the response.
 */
public final class KinesisVideoApacheHttpClient extends HttpClientBase {

    private final CloseableHttpClient mHttpClient;

    private KinesisVideoApacheHttpClient(final BuilderBase<Builder> builder) {
        super(builder);
        this.mHttpClient = buildHttpClient();
    }

    public static Builder builder() {
        return new KinesisVideoApacheHttpClient.Builder();
    }

    public CloseableHttpResponse executeRequest() {

        final HttpPost request = new HttpPost(mBuilder.mUri);
        for (Map.Entry<String, String> entry : mBuilder.mHeaders.entrySet()) {
            request.addHeader(entry.getKey(), entry.getValue());
        }
        final HttpEntity entity = new StringEntity(mBuilder.mContentInJson, mBuilder.mContentType);
        request.setEntity(entity);
        try {
            return this.mHttpClient.execute(request);
        } catch (final IOException e) {
            throw new RuntimeException("Exception while doing a POST on the URI " + mBuilder.mUri, e);
        }
    }

    private CloseableHttpClient buildHttpClient() {
        try {
            final SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, new X509ExtendedTrustManager[] {
                    new HostnameVerifyingX509ExtendedTrustManager(true)}, new SecureRandom());

            final SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext);

            return HttpClients.custom()
                    .setSSLSocketFactory(sslSocketFactory)
                    .setDefaultRequestConfig(RequestConfig.custom()
                            .setConnectTimeout(mBuilder.mConnectionTimeoutInMillis)
                            .build())
                    .setDefaultSocketConfig(SocketConfig.custom()
                            .setSoTimeout(mBuilder.mSocketTimeoutInMillis)
                            .build())
                    .build();
        } catch (final KeyManagementException e) {
            throw new RuntimeException("Exception while building Apache http client", e);
        } catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException("Exception while building Apache http client", e);
        }
    }

    @Override
    public void closeClient() throws IOException {
        this.mHttpClient.close();
    }
    
    public static final class Builder extends BuilderBase<Builder> {
        
        public KinesisVideoApacheHttpClient build() {
            checkNotNull(mUri);
            return new KinesisVideoApacheHttpClient(this);
        }

        @Override
        public Builder builderType() {
            return this;
        }
    }
}
