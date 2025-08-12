package com.amazonaws.kinesisvideo.internal.mediasource;

import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import org.junit.Test;

import java.nio.ByteBuffer;

public class OnStreamDataAvailableTest {

    @Test
    public void givenDefaultImplementation_whenCallingMethods_thenDoNotThrowExceptions() throws KinesisVideoException {
        final OnStreamDataAvailable implementation = new OnStreamDataAvailable() {
        };

        final ByteBuffer buffer = ByteBuffer.allocate(10);
        implementation.onFrameDataAvailable(buffer);

        implementation.onFragmentMetadataAvailable("test", "value", true);
    }

}
