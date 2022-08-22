package com.amazonaws.kinesisvideo.java.mediasource.file;

import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSourceConfiguration;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsync;

import java.time.Instant;

import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.VIDEO_CONTENT_TYPE;

public class CanaryImageFileMediaSourceConfiguration implements MediaSourceConfiguration {

    private final int fps;
    private final String dir;
    private final String filenameFormat;
    private final int startFileIndex;
    private final int endFileIndex;
    private final String contentType;

    public final Instant startTimestamp;

    public final AmazonCloudWatchAsync cloudWatchClient;

    public final long streamingDurationInSeconds;

    public CanaryImageFileMediaSourceConfiguration(final Builder builder) {
        this.fps = builder.fps;
        this.dir = builder.dir;
        this.filenameFormat = builder.filenameFormat;
        this.startFileIndex = builder.startFileIndex;
        this.endFileIndex = builder.endFileIndex;
        this.contentType = builder.contentType;
        this.startTimestamp = builder.startTimestamp;
        this.cloudWatchClient = builder.cloudWatchClient;
        this.streamingDurationInSeconds = builder.streamingDurationInSeconds;
    }

    public int getFps() {
        return fps;
    }

    public String getDir() {
        return dir;
    }

    public String getFilenameFormat() {
        return filenameFormat;
    }

    public int getStartFileIndex() {
        return startFileIndex;
    }

    public int getEndFileIndex() {
        return endFileIndex;
    }

    public String getContentType() {
        return contentType;
    }

    @Override
    public String getMediaSourceType() {
        return null;
    }

    @Override
    public String getMediaSourceDescription() {
        return null;
    }

    public static class Builder implements MediaSourceConfiguration.Builder<CanaryImageFileMediaSourceConfiguration> {
        private int fps;
        private String dir;
        private String filenameFormat;
        private int startFileIndex;
        private int endFileIndex;
        private String contentType = VIDEO_CONTENT_TYPE;
        private Instant startTimestamp;
        private AmazonCloudWatchAsync cloudWatchClient;

        private long streamingDurationInSeconds;


        public Builder fps(final int fps) {
            this.fps = fps;
            if (fps <= 0) {
                throw new IllegalArgumentException("Fps should not be negative or zero.");
            }
            return this;
        }

        public Builder dir(final String dir) {
            this.dir = dir;
            return this;
        }

        public Builder filenameFormat(final String filenameFormat) {
            this.filenameFormat = filenameFormat;
            return this;
        }

        public Builder startFileIndex(final int index) {
            this.startFileIndex = index;
            return this;
        }

        public Builder endFileIndex(final int index) {
            this.endFileIndex = index;
            return this;
        }

        public Builder contentType(final String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder startTimestamp(final Instant startTimestamp) {
            this.startTimestamp = startTimestamp;
            return this;
        }

        public Builder cloudWatchClient(final AmazonCloudWatchAsync cloudWatchClient) {
            this.cloudWatchClient = cloudWatchClient;
            return this;
        }

        public Builder streamDurationInSeconds(final long streamingDurationInSeconds) {
            this.streamingDurationInSeconds = streamingDurationInSeconds;
            return this;
        }

        @Override
        public CanaryImageFileMediaSourceConfiguration build() {
            return new CanaryImageFileMediaSourceConfiguration(this);
        }
    }

}

