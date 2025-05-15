package com.amazonaws.kinesisvideo.signing;

import com.amazonaws.kinesisvideo.http.HttpClient;

public interface KinesisVideoSigner { 
    void sign(final HttpClient httpClient);
}
