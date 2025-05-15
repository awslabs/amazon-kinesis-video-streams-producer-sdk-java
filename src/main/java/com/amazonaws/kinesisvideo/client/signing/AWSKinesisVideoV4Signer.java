package com.amazonaws.kinesisvideo.client.signing;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.kinesisvideo.config.ClientConfiguration;

/**
 * This is an extended class of {@link KinesisVideoAWS4Signer} which decides whether to
 * send a content header based on whether the payload is of streaming type or not
 * Different class is created so as to not break the existing PutStream and GetStream
 * clients using the {@link KinesisVideoAWS4Signer}. This will be used by GetMedia client now and
 * should also be used for PutMedia client when migration happens from PutStream to PutMedia.
 * At the end when put/get Stream is no longer used {@link KinesisVideoAWS4Signer} will/should not
 * be used by the clients.
 */
public class AWSKinesisVideoV4Signer extends KinesisVideoAWS4Signer {

    private final boolean mIsStreamingPayload;

    public AWSKinesisVideoV4Signer(
            final AWSCredentialsProvider credentialsProvider,
            final ClientConfiguration config,
            final boolean isStreamingPayload) {
        super(credentialsProvider, config);
        mIsStreamingPayload = isStreamingPayload;
    }

    @Override
    protected boolean shouldAddContentUnsignedPayloadInHeader(final String httpMethodName) {
        return mIsStreamingPayload;
    }
}

