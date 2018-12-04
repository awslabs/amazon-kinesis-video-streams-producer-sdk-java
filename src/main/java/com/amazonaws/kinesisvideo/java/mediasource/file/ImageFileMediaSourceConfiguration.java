package com.amazonaws.kinesisvideo.java.mediasource.file;


import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSourceConfiguration;

public class ImageFileMediaSourceConfiguration implements MediaSourceConfiguration {

    private final int fps;
    private final String dir;
    private final String filenameFormat;
    private final int startFileIndex;
    private final int endFileIndex;

    public ImageFileMediaSourceConfiguration(final Builder builder) {
        this.fps = builder.fps;
        this.dir = builder.dir;
        this.filenameFormat = builder.filenameFormat;
        this.startFileIndex = builder.startFileIndex;
        this.endFileIndex = builder.endFileIndex;
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

    @Override
    public String getMediaSourceType() {
        return null;
    }

    @Override
    public String getMediaSourceDescription() {
        return null;
    }

    public static class Builder implements MediaSourceConfiguration.Builder<ImageFileMediaSourceConfiguration> {
        private int fps;
        private String dir;
        private String filenameFormat;
        private int startFileIndex;
        private int endFileIndex;

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

        @Override
        public ImageFileMediaSourceConfiguration build() {
            return new ImageFileMediaSourceConfiguration(this);
        }
    }

}
