package com.amazonaws.kinesisvideo.producer.jni;

import org.junit.Test;

public class NativeLibraryLoaderTest extends NativeKinesisVideoProducerJniTestBase {
    public NativeLibraryLoaderTest() throws Exception {
    }

    @Test(expected = NullPointerException.class)
    public void preconditionsNullCheckTest() throws Exception {
        final NativeLibraryLoader loader = new NativeLibraryLoader(null);
    }

    @Test(expected = IllegalStateException.class)
    public void preconditionsStateCheckNullNullTest() throws Exception {
        final NativeLibraryLoader loader = new NativeLibraryLoader(mLog);

        loader.loadNativeLibrary(null, null);
    }

    @Test(expected = IllegalStateException.class)
    public void preconditionsStateCheckEmptyNullTest() throws Exception {
        final NativeLibraryLoader loader = new NativeLibraryLoader(mLog);

        loader.loadNativeLibrary("", null);
    }

    @Test(expected = IllegalStateException.class)
    public void preconditionsStateCheckNullEmptyTest() throws Exception {
        final NativeLibraryLoader loader = new NativeLibraryLoader(mLog);

        loader.loadNativeLibrary(null, "");
    }

    @Test(expected = IllegalStateException.class)
    public void preconditionsStateCheckEmptyEmptyTest() throws Exception {
        final NativeLibraryLoader loader = new NativeLibraryLoader(mLog);

        loader.loadNativeLibrary("", "");
    }
}
