package com.amazonaws.kinesisvideo.java.mediasource.file;


import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSourceConfiguration;
import com.amazonaws.kinesisvideo.producer.StreamCallbacks;
import com.amazonaws.kinesisvideo.producer.Tag;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Arrays;

import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.VIDEO_CONTENT_TYPE;

public class ImageFileMediaSourceConfiguration implements MediaSourceConfiguration {

    private final int fps;
    private final String dir;
    private final String filenameFormat;
    private final int startFileIndex;
    private final int endFileIndex;
    private final String contentType;
    private final boolean allowStreamCreation;
    private final long startTimeMs;
    private final StreamCallbacks streamCallbacks;
    private final Tag[] tags;

    public ImageFileMediaSourceConfiguration(final Builder builder) {
        this.fps = builder.fps;
        this.dir = builder.dir;
        this.filenameFormat = builder.filenameFormat;
        this.startFileIndex = builder.startFileIndex;
        this.endFileIndex = builder.endFileIndex;
        this.contentType = builder.contentType;
        this.allowStreamCreation = builder.allowStreamCreation;
        this.startTimeMs = builder.startTimeMs;
        this.streamCallbacks = builder.streamCallbacks;
        this.tags = builder.tags;
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

    public long getStartTimeMs() { return startTimeMs; }

    @Override
    public String getMediaSourceType() {
        return null;
    }

    @Override
    public String getMediaSourceDescription() {
        return null;
    }

    @Nullable
    public Tag[] getTags() {
        return tags;
    }

    public boolean isAllowStreamCreation() {
        return allowStreamCreation;
    }

    public StreamCallbacks getStreamCallbacks() {
        return streamCallbacks;
    }

    public static class Builder implements MediaSourceConfiguration.Builder<ImageFileMediaSourceConfiguration> {

        private int fps;
        private String dir;
        private String filenameFormat;
        private int startFileIndex;
        private int endFileIndex;
        private String contentType = VIDEO_CONTENT_TYPE;
        private boolean allowStreamCreation;
        private long startTimeMs = System.currentTimeMillis();
        private StreamCallbacks streamCallbacks = null;
        private Tag[] tags = new Tag[] {
                new Tag("device", "Test Device"),
                new Tag("stream", "Test Stream") };

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

        public Builder allowStreamCreation(final Boolean allowStreamCreation) {
            this.allowStreamCreation = allowStreamCreation;
            return this;
        }

        public Builder startTimeMs(final long startTimeMs) {
            this.startTimeMs = startTimeMs;
            return this;
        }

        public Builder streamCallbacks(final StreamCallbacks streamCallbacks) {
            this.streamCallbacks = streamCallbacks;
            return this;
        }

        public Builder tags(@Nullable final Tag... tags) {
            if (tags != null) {
                this.tags = Arrays.copyOf(tags, tags.length);
            } else {
                this.tags = null;
            }
            return this;
        }

        @Override
        public ImageFileMediaSourceConfiguration build() {
            return new ImageFileMediaSourceConfiguration(this);
        }
    }

}
