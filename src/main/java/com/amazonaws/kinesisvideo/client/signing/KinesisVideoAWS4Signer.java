package com.amazonaws.kinesisvideo.client.signing;

import java.net.URI;
import java.net.URISyntaxException;
import com.amazonaws.kinesisvideo.config.ClientConfiguration;
import com.amazonaws.kinesisvideo.http.HttpClient;
import com.amazonaws.kinesisvideo.signing.KinesisVideoSigner;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.SignedRequest;
import software.amazon.awssdk.identity.spi.AwsCredentialsIdentity;
import software.amazon.awssdk.identity.spi.AwsSessionCredentialsIdentity;

public class KinesisVideoAWS4Signer implements KinesisVideoSigner {

    private static final String CONTENT_HASH_HEADER = "x-amz-content-sha256";
    private static final String CONTENT_UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD";
    private static final String AUTH_HEADER = "Authorization";
    private static final String DATE_HEADER = "X-Amz-Date";
    private static final String SECURITY_TOKEN_HEADER = "X-Amz-Security-Token";

    private final AwsCredentialsProvider mAWSCredentialsProvider;
    private final ClientConfiguration mConfiguration;
    private final AwsV4HttpSigner mSigner = AwsV4HttpSigner.create();

    public KinesisVideoAWS4Signer(
            final AwsCredentialsProvider credentialsProvider,
            final ClientConfiguration config) {
        mAWSCredentialsProvider = credentialsProvider;
        mConfiguration = config;
    }

    public void sign(final HttpClient httpClient) {

        AwsCredentialsIdentity identity = AwsSessionCredentialsIdentity.create(mAWSCredentialsProvider.resolveCredentials().accessKeyId(),
                mAWSCredentialsProvider.resolveCredentials().secretAccessKey(), null);

        SdkHttpRequest.Builder requestBuilder = SdkHttpRequest.builder();
        try {
            requestBuilder.uri(new URI(httpClient.getUri().getScheme() + "://" + httpClient.getUri().getHost()
                            + httpClient.getUri().getPath()))
                    .method(SdkHttpMethod.valueOf(httpClient.getMethod().name()));

            httpClient.getHeaders().forEach(requestBuilder::putHeader);

            SdkHttpRequest signableRequest = requestBuilder.build();

            ContentStreamProvider requestPayload = ContentStreamProvider.fromInputStream(httpClient.getContent());

            SignedRequest signedRequest = mSigner.sign(r -> r.identity(identity)
                    .request(signableRequest)
                    .payload(requestPayload)
                    .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, "kinesis-video")
                    .putProperty(AwsV4HttpSigner.REGION_NAME, mConfiguration.getRegion().toString()).build());

            httpClient.getHeaders().put(AUTH_HEADER, signedRequest.request().headers().get(AUTH_HEADER).get(0));
            httpClient.getHeaders().put(DATE_HEADER, signedRequest.request().headers().get(DATE_HEADER).get(0));
            addSecurityToken(httpClient, signableRequest);
            addContentHeader(httpClient);

        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private void addSecurityToken(final HttpClient httpClient, final SdkHttpRequest signableRequest) {
        final Object securityToken = signableRequest.headers().get(SECURITY_TOKEN_HEADER);
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
        return SdkHttpMethod.POST.name().equals(httpMethodName);
    }
}
