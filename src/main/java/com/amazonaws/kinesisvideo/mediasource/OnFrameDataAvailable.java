package com.amazonaws.kinesisvideo.mediasource;

import java.nio.ByteBuffer;

public interface OnFrameDataAvailable {
    void onFrameDataAvailable(final ByteBuffer data);
}
