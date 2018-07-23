package com.amazonaws.kinesisvideo.client.stream;


import org.apache.http.client.methods.CloseableHttpResponse;
import com.amazonaws.kinesisvideo.client.StreamingReadClient;
import com.amazonaws.kinesisvideo.config.ClientConfiguration;
import com.amazonaws.kinesisvideo.signing.KinesisVideoSigner;
import com.google.inject.Inject;
import com.google.inject.name.Named;

public class StreamingReadManager {
    private final KinesisVideoSigner mSigner;

    @Inject
    public StreamingReadManager(@Named("AWSKinesisVideoV4SignerForNonStreamingPayload")  final KinesisVideoSigner signer) {
        if (signer == null) {
            throw new NullPointerException("signer");
        }
        this.mSigner = signer;
    }

    public CloseableHttpResponse receiveStreamData(final ClientConfiguration config, final String inputInJson) {
        return StreamingReadClient.builder().uri(config.getStreamUri()).signer(mSigner).inputInJson(inputInJson).connectionTimeoutInMillis(config.getConnectionTimeoutInMillis()).readTimeoutInMillis(config.getReadTimeoutInMillis()).build().execute();
    }
}
