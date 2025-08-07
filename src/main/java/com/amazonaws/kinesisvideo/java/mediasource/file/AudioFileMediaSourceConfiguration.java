package com.amazonaws.kinesisvideo.java.mediasource.file;

import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSourceConfiguration;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.VIDEO_CONTENT_TYPE;
/**
 * Configuration class for audio file media source that implements MediaSourceConfiguration.
 * This class manages the configuration parameters needed for processing audio files
 * in a media stream.
 * <p>
 * The configuration includes:
 * <ul>
 *     <li>Frame rate (FPS) for audio processing</li>
 *     <li>Directory path for audio files</li>
 *     <li>File naming format</li>
 *     <li>File index range (start and end)</li>
 *     <li>Content type specification</li>
 *     <li>Stream creation control</li>
 * </ul>
 *
 * @see MediaSourceConfiguration
 */
public class AudioFileMediaSourceConfiguration implements MediaSourceConfiguration {

    /**
     * The frames per second rate for audio processing
     */
    private final int fps;

    /**
     * The directory path where audio files are located
     */
    private final String dir;

    /**
     * The format pattern for audio filenames
     */
    private final String filenameFormat;

    /**
     * The starting index for audio file processing
     */
    private final int startFileIndex;

    /**
     * The ending index for audio file processing
     */
    private final int endFileIndex;

    /**
     * The content type of the audio files
     */
    private final String contentType;

    /**
     * Flag indicating whether new stream creation is allowed
     */
    private final boolean allowStreamCreation;

    /**
     * Constructs a new AudioFileMediaSourceConfiguration using the Builder pattern.
     *
     * @param builder The Builder object containing configuration parameters
     */
    public AudioFileMediaSourceConfiguration(final Builder builder) {
        this.fps = builder.fps;
        this.dir = builder.dir;
        this.filenameFormat = builder.filenameFormat;
        this.startFileIndex = builder.startFileIndex;
        this.endFileIndex = builder.endFileIndex;
        this.contentType = builder.contentType;
        this.allowStreamCreation = builder.allowStreamCreation;
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

    public boolean isAllowStreamCreation() {
        return allowStreamCreation;
    }

    public static class Builder implements MediaSourceConfiguration.Builder<AudioFileMediaSourceConfiguration> {

        private int fps;
        private String dir;
        private String filenameFormat;
        private int startFileIndex;
        private int endFileIndex;
        private String contentType = VIDEO_CONTENT_TYPE; //TODO: add audio only content type
        private boolean allowStreamCreation;


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

        @Override
        public AudioFileMediaSourceConfiguration build() {
            return new AudioFileMediaSourceConfiguration(this);
        }
    }

}