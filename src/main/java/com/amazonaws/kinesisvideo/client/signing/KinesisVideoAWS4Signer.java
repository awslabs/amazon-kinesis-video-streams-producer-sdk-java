package com.amazonaws.kinesisvideo.client.signing;

import java.net.URI;

import com.amazonaws.DefaultRequest;
import com.amazonaws.SignableRequest;
import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.http.HttpMethodName;
import com.amazonaws.kinesisvideo.config.ClientConfiguration;
import com.amazonaws.kinesisvideo.http.HttpClient;
import com.amazonaws.kinesisvideo.signing.KinesisVideoSigner;

public class KinesisVideoAWS4Signer extends AWS4Signer implements KinesisVideoSigner {

    private static final String CONTENT_HASH_HEADER = "x-amz-content-sha256";
    private static final String CONTENT_UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD";
    private static final String AUTH_HEADER = "Authorization";
    private static final String DATE_HEADER = "X-Amz-Date";
    private static final String SECURITY_TOKEN_HEADER = "X-Amz-Security-Token";

    private final AWSCredentialsProvider mAWSCredentialsProvider;
    private final ClientConfiguration mConfiguration;

    private static class SimpleSignableRequest extends DefaultRequest {

        public SimpleSignableRequest(final HttpClient httpClient) {
            super("kinesisvideo");
            try {
                setHttpMethod(HttpMethodName.fromValue(httpClient.getMethod().name()));
                setEndpoint(new URI(httpClient.getUri().getScheme() + "://" + httpClient.getUri().getHost()));
                setResourcePath(httpClient.getUri().getPath());
                setHeaders(httpClient.getHeaders());
                setContent(httpClient.getContent());
            } catch (final Throwable e) {
                throw new RuntimeException("Exception while creating signable request ! ", e);
            }
        }
    }

    public KinesisVideoAWS4Signer(
            final AWSCredentialsProvider credentialsProvider,
            final ClientConfiguration config) {
        mAWSCredentialsProvider = credentialsProvider;
        mConfiguration = config;
    }

    @Override
    protected String calculateContentHash(final SignableRequest<?> request) {
        if (shouldAddContentUnsignedPayloadInHeader(request.getHttpMethod().name())) {
            return CONTENT_UNSIGNED_PAYLOAD;
        }
        return  super.calculateContentHash(request);
    }


    @Override
    public void sign(final HttpClient httpClient) {
        setServiceName(mConfiguration.getServiceName());
        setRegionName(mConfiguration.getRegion());

        final SignableRequest signableRequest = toSignableRequest(httpClient);
        final AWSCredentials credentials = mAWSCredentialsProvider.getCredentials();

        sign(signableRequest, credentials);
        // TODO: Implement logging
        httpClient.getHeaders().put(AUTH_HEADER, (String) signableRequest.getHeaders().get(AUTH_HEADER));
        httpClient.getHeaders().put(DATE_HEADER, (String) signableRequest.getHeaders().get(DATE_HEADER));
        addSecurityToken(httpClient, signableRequest);
        addContentHeader(httpClient);

    }

    public SignableRequest<?> toSignableRequest(final HttpClient httpClient) {
        return new SimpleSignableRequest(httpClient);
    }

    private void addSecurityToken(final HttpClient httpClient, final SignableRequest signableRequest) {
        final Object securityToken = signableRequest.getHeaders().get(SECURITY_TOKEN_HEADER);
        if (securityToken != null) {
            httpClient.getHeaders().put(SECURITY_TOKEN_HEADER, (String) securityToken);
        }
    }

    private void addContentHeader(final HttpClient httpClient) {
        if (shouldAddContentUnsignedPayloadInHeader(httpClient.getMethod().name())) {
            httpClient.getHeaders().put(CONTENT_HASH_HEADER, CONTENT_UNSIGNED_PAYLOAD);
        }
    }

    protected boolean shouldAddContentUnsignedPayloadInHeader(final String httpMethodName) {
        return HttpMethodName.POST.name().equals(httpMethodName);
    }
}
