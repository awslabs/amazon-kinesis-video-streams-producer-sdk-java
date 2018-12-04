package com.amazonaws.kinesisvideo.internal.mediasource.bytes;

import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSourceConfiguration;

public class BytesMediaSourceConfiguration implements MediaSourceConfiguration {

    private static final String MEDIA_SOURCE_TYPE = "BytesMediaSource";
    private static final String MEDIA_SOURCE_DESCRIPTION = "Generates bytes in specific "
            + "configuration. Useful for debugging";

    public static class Builder
            implements MediaSourceConfiguration.Builder<BytesMediaSourceConfiguration> {

        private int fps;
        private long retentionPeriodInHours;

        public Builder withFps(final int fps) {
            this.fps = fps;
            return this;
        }

        public Builder withRetentionPeriodInHours(final long retentionPeriodInHours) {
            this.retentionPeriodInHours = retentionPeriodInHours;
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

    public long getRetentionPeriodInHours() {
        return mBuilder.retentionPeriodInHours;
    }

    @Override
    public String getMediaSourceType() {
        return MEDIA_SOURCE_TYPE;
    }

    @Override
    public String getMediaSourceDescription() {
        return MEDIA_SOURCE_DESCRIPTION;
    }
}
