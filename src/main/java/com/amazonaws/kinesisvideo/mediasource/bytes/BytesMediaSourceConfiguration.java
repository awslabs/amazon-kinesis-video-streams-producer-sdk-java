package com.amazonaws.kinesisvideo.mediasource.bytes;

import com.amazonaws.kinesisvideo.client.mediasource.MediaSourceConfiguration;

public class BytesMediaSourceConfiguration implements MediaSourceConfiguration {

    private static final String MEDIA_SOURCE_TYPE = "BytesMediaSource";
    private static final String MEDIA_SOURCE_DESCRIPTION = "Generates bytes in specific "
            + "configuration. Useful for debugging";

    public static class Builder
            implements MediaSourceConfiguration.Builder<BytesMediaSourceConfiguration> {

        private int fps;

        public Builder withFps(final int fps) {
            this.fps = fps;
            return this;
        }

        @Override
        public BytesMediaSourceConfiguration build() {
            return new BytesMediaSourceConfiguration(this);
        }
    }

    private final Builder mBuilder;

    public BytesMediaSourceConfiguration(final Builder builder) {
        mBuilder = builder;
    }

    public int getFps() {
        return mBuilder.fps;
    }

    @Override
    public String getMediaSourceType() {
        return null;
    }

    @Override
    public String getMediaSourceDescription() {
        return null;
    }
}
