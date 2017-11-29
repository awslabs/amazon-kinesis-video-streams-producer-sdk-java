package com.amazonaws.kinesisvideo.client.stream;

import com.amazonaws.kinesisvideo.signing.KinesisVideoSigner;
import com.google.inject.Inject;
import com.google.inject.name.Named;

@Deprecated
public class GetMediaManager extends StreamingReadManager {

    @Inject
    public GetMediaManager(@Named("AWSKinesisVideoV4SignerForNonStreamingPayload") final KinesisVideoSigner signer) {
        super(signer);
    }
}
