package com.amazonaws.kinesisvideo.java.mediasource.file;

import static com.amazonaws.kinesisvideo.producer.StreamInfo.NalAdaptationFlags.NAL_ADAPTATION_FLAG_NONE;
import static com.amazonaws.kinesisvideo.producer.StreamInfo.codecIdFromContentType;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.internal.mediasource.DefaultOnStreamDataAvailable;
import com.amazonaws.kinesisvideo.producer.StreamCallbacks;

import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSource;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSourceConfiguration;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSourceSink;
import com.amazonaws.kinesisvideo.client.mediasource.MediaSourceState;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.kinesisvideo.producer.Tag;

import java.util.concurrent.CompletableFuture;

/**
 * MediaSource implementation for streaming audio from local files to Kinesis Video Streams.
 * 
 * <p>This class provides a complete implementation of the MediaSource interface for audio streaming.
 * It reads audio files (AAC/PCM format) from the local filesystem and streams them to Kinesis Video Streams
 * at a configurable frame rate.</p>
 * 
 * <p>Key features:</p>
 * <ul>
 *   <li>Supports AAC and PCM audio formats</li>
 *   <li>Configurable frame rate and file range</li>
 *   <li>Automatic stream creation (optional)</li>
 *   <li>Real-time streaming with proper timing</li>
 *   <li>Thread-safe lifecycle management</li>
 * </ul>
 * 
 * <p>Usage example:</p>
 * <pre>{@code
 * // Create configuration
 * AudioFileMediaSourceConfiguration config = new AudioFileMediaSourceConfiguration.Builder()
 *     .fps(25)
 *     .dir("audio/")
 *     .filenameFormat("audio-%03d.aac")
 *     .startFileIndex(1)
 *     .endFileIndex(100)
 *     .contentType("audio/aac")
 *     .build();
 * 
 * // Create and configure media source
 * AudioFileMediaSource mediaSource = new AudioFileMediaSource("my-audio-stream");
 * mediaSource.configure(config);
 * 
 * // Initialize with sink and start streaming
 * mediaSource.initialize(sink);
 * mediaSource.start();
 * }</pre>
 * 
 * @see MediaSource
 * @see AudioFileMediaSourceConfiguration
 * @see AudioFrameSource
 */
public class AudioFileMediaSource implements MediaSource {

    private final String streamName;
    private final CompletableFuture<Boolean> completionFuture;

    private AudioFileMediaSourceConfiguration configuration;
    private MediaSourceState state;
    private MediaSourceSink sink;
    private AudioFrameSource frameSource;

    /**
     * Constructs an AudioFileMediaSource with the specified stream name.
     * 
     * <p>Creates a new audio media source that will stream to the specified Kinesis Video stream.
     * The source must be configured and initialized before it can start streaming.</p>
     *
     * @param streamName the name of the Kinesis Video stream to stream to
     * @throws IllegalArgumentException if streamName is null
     */
    public AudioFileMediaSource(@Nonnull final String streamName) {
        this(streamName, new CompletableFuture<>());
    }

    /**
     * Constructs an AudioFileMediaSource with the specified stream name and completion future.
     * 
     * <p>This constructor allows external code to be notified when the media source stops
     * by providing a CompletableFuture that will be completed when streaming ends.</p>
     *
     * @param streamName the name of the Kinesis Video Stream to send stream to
     * @param completionFuture a future that will be completed when the source stops
     * @throws IllegalArgumentException if streamName or completionFuture is null
     */
    public AudioFileMediaSource(@Nonnull final String streamName, final CompletableFuture<Boolean> completionFuture) {
        this.streamName = Preconditions.checkNotNull(streamName);
        this.completionFuture = Preconditions.checkNotNull(completionFuture);
    }

    @Override
    public MediaSourceState getMediaSourceState() {
        return state;
    }

    @Override
    public MediaSourceConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Returns the StreamInfo configuration for this audio media source.
     * 
     * <p>The StreamInfo contains all the necessary parameters for Kinesis Video Streams
     * to properly handle the audio stream, including codec information, frame rate,
     * and streaming characteristics.</p>
     * 
     * @return the StreamInfo configuration for this audio stream
     * @throws KinesisVideoException if the media source is not configured
     * @throws IllegalStateException if called before configuration
     */
    @Override
    public StreamInfo getStreamInfo() throws KinesisVideoException {
        Preconditions.checkNotNull(configuration, "MediaSource must be configured before getting StreamInfo");

        return new StreamInfo(VERSION_TWO,
                streamName,
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME,
                configuration.getContentType(),
                NO_KMS_KEY_ID,
                RETENTION_ONE_HOUR,
                NOT_ADAPTIVE,
                MAX_LATENCY_ZERO, // Default value 120 seconds
                DEFAULT_GOP_DURATION,
                false, //    KEYFRAME_FRAGMENTATION_FALSE
                USE_FRAME_TIMECODES,
                RELATIVE_TIMECODES,
                REQUEST_FRAGMENT_ACKS,
                RECOVER_ON_FAILURE,
                codecIdFromContentType(configuration.getContentType()),
                "audio-track",
                DEFAULT_BITRATE,
                configuration.getFps(),
                DEFAULT_BUFFER_DURATION,
                DEFAULT_REPLAY_DURATION,
                DEFAULT_STALENESS_DURATION,
                DEFAULT_TIMESCALE,
                RECALCULATE_METRICS,
                null,
                new Tag[] {
                        new Tag("device", "AudioFileDevice"),
                        new Tag("stream", "AudioFileStream") },
                NAL_ADAPTATION_FLAG_NONE,
                configuration.isAllowStreamCreation());
    }

    /**
     * Initializes the media source with the specified sink.
     * 
     * <p>The sink is used to deliver audio frames to the Kinesis Video Streams producer.
     * This method must be called before starting the media source.</p>
     * 
     * @param mediaSourceSink the sink to receive audio frames
     * @throws KinesisVideoException if initialization fails
     * @throws IllegalArgumentException if mediaSourceSink is null
     */
    @Override
    public void initialize(@Nonnull final MediaSourceSink mediaSourceSink) throws KinesisVideoException {
        this.sink = Preconditions.checkNotNull(mediaSourceSink);
    }

    /**
     * Configures the media source with audio-specific settings.
     * 
     * <p>This method sets up the audio streaming parameters including file location,
     * frame rate, content type, and other audio-specific configurations.</p>
     * 
     * @param configuration the audio file media source configuration
     * @throws IllegalStateException if the media source is already configured
     * @throws IllegalArgumentException if configuration is not an AudioFileMediaSourceConfiguration
     */
    @Override
    public void configure(final MediaSourceConfiguration configuration) {
        Preconditions.checkState(this.configuration == null, "MediaSource is already configured");
        Preconditions.checkArgument(configuration instanceof AudioFileMediaSourceConfiguration,
                "Configuration must be an instance of AudioFileMediaSourceConfiguration");

        this.configuration = (AudioFileMediaSourceConfiguration) configuration;
    }

    /**
     * Starts the audio streaming process.
     * 
     * <p>This method begins reading audio files and streaming them to Kinesis Video Streams
     * at the configured frame rate. The media source must be configured and initialized
     * before calling this method.</p>
     * 
     * @throws KinesisVideoException if streaming fails to start
     * @throws IllegalStateException if called before configuration or initialization
     */
    @Override
    public void start() throws KinesisVideoException {
        Preconditions.checkNotNull(configuration, "MediaSource must be configured before starting");
        Preconditions.checkNotNull(sink, "MediaSource must be initialized before starting");

        state = MediaSourceState.RUNNING;
        frameSource = new AudioFrameSource(configuration);
        frameSource.onStreamDataAvailable(new DefaultOnStreamDataAvailable(sink));
        frameSource.start();
    }

    /**
     * Stops the audio streaming process.
     * 
     * <p>This method gracefully stops the audio frame generation and streaming.
     * It ensures that the producer stream is properly closed and resources are cleaned up.</p>
     * 
     * @throws KinesisVideoException if stopping fails
     */
    @Override
    public void stop() throws KinesisVideoException {
        if (frameSource != null) {
            frameSource.stop();
        }

        try {
            if (sink != null && sink.getProducerStream() != null) {
                sink.getProducerStream().stopStreamSync();
            }
        } finally {
            state = MediaSourceState.STOPPED;
            completionFuture.complete(true);
        }
    }

    @Override
    public boolean isStopped() {
        return state == MediaSourceState.STOPPED;
    }

    @Override
    public void free() throws KinesisVideoException {
        // No resources to free
    }

    @Override
    public MediaSourceSink getMediaSourceSink() {
        return sink;
    }

    @Nullable
    @Override
    public StreamCallbacks getStreamCallbacks() {
        return null;
    }
}
