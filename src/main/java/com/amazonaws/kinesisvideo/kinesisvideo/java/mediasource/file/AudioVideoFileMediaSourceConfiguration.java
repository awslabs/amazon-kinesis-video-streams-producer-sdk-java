package com.amazonaws.kinesisvideo.java.mediasource.file;


import com.amazonaws.kinesisvideo.internal.mediasource.multitrack.MultiTrackMediaSourceConfiguration;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.AUDIO_VIDEO_CONTENT_TYPE;

public class AudioVideoFileMediaSourceConfiguration extends MultiTrackMediaSourceConfiguration {


    private final Builder builder;

    protected AudioVideoFileMediaSourceConfiguration(final Builder builder) {
        super(builder);
        this.builder = builder;
    }

    public String getDir() {
        return builder.dir;
    }

    public static class AudioVideoBuilder extends Builder<AudioVideoBuilder> {
        public AudioVideoBuilder() {
            super(AudioVideoBuilder.class);
        }
    }

    protected static class Builder<T extends Builder<T>>
            extends MultiTrackMediaSourceConfiguration.Builder<T> {
        private String dir;

        public Builder(final Class<?> builder) {
            super();
        }

        public T withDir(final String dir) {
            this.dir = dir;
            return (T) this;
        }

        @Override
        public AudioVideoFileMediaSourceConfiguration build() {
            if (contentType == null) {
                withContentType(AUDIO_VIDEO_CONTENT_TYPE);
            }

            return new AudioVideoFileMediaSourceConfiguration(this);
        }
    }

}
