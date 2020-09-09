package com.amazonaws.kinesisvideo.http;

import static com.amazonaws.kinesisvideo.common.preconditions.Preconditions.checkNotNull;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.nio.client.methods.HttpAsyncMethods;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * Http Async Client which uses Apache HttpAsyncClient internally to make
 * the http request and invoke callbacks when there is data ready to consume.
 */
public final class KinesisVideoApacheHttpAsyncClient extends HttpClientBase {
    
    private final CloseableHttpAsyncClient mHttpClient;

    private KinesisVideoApacheHttpAsyncClient(final BuilderBase<Builder> builder) {
        super(builder);
        this.mHttpClient = buildHttpAsyncClient();
        this.mHttpClient.start();
    }

    public static Builder builder() {
        return new Builder();
    }

    public void executeRequest() {
        final HttpPost request = new HttpPost(mBuilder.mUri);
        for (Map.Entry<String, String> entry : mBuilder.mHeaders.entrySet()) {
            request.addHeader(entry.getKey(), entry.getValue());
        }
        final HttpEntity entity = new StringEntity(mBuilder.mContentInJson, mBuilder.mContentType);
        request.setEntity(entity);
        final HttpAsyncRequestProducer requestProducer = HttpAsyncMethods.create(request);
        this.mHttpClient.execute(requestProducer, ((Builder) mBuilder).mHttpAsyncResponseConsumer,
                ((Builder) mBuilder).mFutureCallback);
    }

    private CloseableHttpAsyncClient buildHttpAsyncClient() {
        try {
            final SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, new X509ExtendedTrustManager[] {
                    new HostnameVerifyingX509ExtendedTrustManager(true)}, new SecureRandom());

            final SSLIOSessionStrategy sslSessionStrategy = new SSLIOSessionStrategy(sslContext);

            return HttpAsyncClientBuilder.create()
                    .setSSLStrategy(sslSessionStrategy)
                    .setDefaultRequestConfig(RequestConfig.custom()
                            .setConnectTimeout(mBuilder.mConnectionTimeoutInMillis)
                            .setSocketTimeout(mBuilder.mSocketTimeoutInMillis)
                            .build())
                    .build();
        } catch (final KeyManagementException e) {
            throw new RuntimeException("Exception while building Apache http client", e);
        } catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException("Exception while building Apache http client", e);
        }
    }
    
    public static final class Builder extends BuilderBase<Builder> {
        
        private HttpAsyncResponseConsumer<HttpResponse> mHttpAsyncResponseConsumer;
        private FutureCallback<HttpResponse> mFutureCallback;
        
        public Builder withHttpAsyncResponseConsumer(final HttpAsyncResponseConsumer<HttpResponse> 
                          httpAsyncResponseConsumer) {
            mHttpAsyncResponseConsumer = httpAsyncResponseConsumer;
            return this;
        }
        
        public Builder withFutureCallback(final FutureCallback<HttpResponse> futureCallback) {
            mFutureCallback = futureCallback;
            return this;
        }

        public KinesisVideoApacheHttpAsyncClient build() {
            checkNotNull(mUri);
            return new KinesisVideoApacheHttpAsyncClient(this);
        }

        @Override
        public Builder builderType() {
            return this;
        }
    }

    @Override
    public void closeClient() throws IOException {
        this.mHttpClient.close();
    }
}
