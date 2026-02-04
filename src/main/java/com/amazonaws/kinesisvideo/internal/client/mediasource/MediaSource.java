package com.amazonaws.kinesisvideo.internal.client.mediasource;

import com.amazonaws.kinesisvideo.client.mediasource.MediaSourceState;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.internal.client.NativeKinesisVideoClient;
import com.amazonaws.kinesisvideo.internal.mediasource.ProducerStreamSink;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.producer.StreamCallbacks;
import com.amazonaws.kinesisvideo.producer.StreamInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Interface representing a media source that produces video/audio frames and delivers them to Kinesis Video Streams.
 *
 * <p>A MediaSource is responsible for:</p>
 * <ul>
 *   <li>Generating or capturing media frames (video/audio data)</li>
 *   <li>Converting frames into {@link com.amazonaws.kinesisvideo.producer.KinesisVideoFrame} objects</li>
 *   <li>Pushing frames to a {@link MediaSourceSink} for streaming to Kinesis Video Streams</li>
 *   <li>Managing its own lifecycle (start, stop, cleanup)</li>
 * </ul>
 *
 * <p>The {@link MediaSource} follows a producer-consumer pattern where:</p>
 * <ul>
 *   <li>The {@link MediaSource} acts as the <strong>producer</strong> of media frames</li>
 *   <li>The {@link MediaSourceSink} acts as the <strong>consumer</strong> that receives frames</li>
 *   <li>The sink handles the actual frame submission to the Kinesis Video Stream via
 *   {@link com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream#putFrame(KinesisVideoFrame)}</li>
 * </ul>
 *
 * <p><strong>Data flow:</strong></p>
 * <pre>
 * MediaSource &rarr; MediaSourceSink &rarr; KinesisVideoProducerStream &rarr; Kinesis Video Streams
 * </pre>
 *
 * <p><strong>Frame delivery:</strong></p>
 * <ul>
 *   <li>MediaSource calls {@code sink.onFrame(frame)} for each video/audio frame</li>
 *   <li>Sink validates and forwards frame to the producer stream</li>
 *   <li>Producer handles packaging, and network transmission</li>
 * </ul>
 *
 * <p><strong>Typical usage flow:</strong></p>
 * <ol>
 *   <li>Create and {@link #configure(MediaSourceConfiguration)} the {@link MediaSource}</li>
 *   <li>Submit the {@link MediaSource} to the {@link com.amazonaws.kinesisvideo.client.KinesisVideoClient} via
 *   {@link com.amazonaws.kinesisvideo.client.KinesisVideoClient#registerMediaSource(MediaSource)}</li>
 *   <li>Start the {@link MediaSource}</li>
 *   <li>Unregister the {@link MediaSource} via </li>
 * </ol>
 *
 * <p><strong>Interactions with {@link com.amazonaws.kinesisvideo.internal.client.NativeKinesisVideoClient}:</strong></p>
 * <ol>
 *   <li>{@link com.amazonaws.kinesisvideo.internal.client.NativeKinesisVideoClient#registerMediaSource(MediaSource)}
 *   will {@link #initialize(MediaSourceSink)} it with an instance of {@link ProducerStreamSink} created using the
 *   provided {@link StreamInfo} via {@link #getStreamInfo()}</li>
 *   <li>{@link com.amazonaws.kinesisvideo.internal.client.NativeKinesisVideoClient#unregisterMediaSource(MediaSource)}
 *   will stop the media source if not already, then call {@link #free()}.</li>
 * </ol>
 *
 * @see MediaSourceSink
 * @see com.amazonaws.kinesisvideo.producer.KinesisVideoFrame
 * @see MediaSourceConfiguration
 */
public interface MediaSource {

    /**
     * Returns the current state of this media source.
     *
     * <p>States include: STARTED, STOPPED, etc.</p>
     * <p>Use this to check if the media source is ready to start streaming.</p>
     *
     * @return the current {@link MediaSourceState}
     */
    MediaSourceState getMediaSourceState();

    /**
     * Returns the configuration object used to create and configure this media source.
     *
     * <p>The configuration contains media source specific settings such as:</p>
     * <ul>
     *   <li>Media source description</li>
     *   <li>Source-specific parameters (mime type, camera id, file path, etc.)</li>
     * </ul>
     *
     * @return the {@link MediaSourceConfiguration} for this media source
     */
    MediaSourceConfiguration getConfiguration();

    /**
     * Returns stream information describing the Kinesis Video Stream this is producing frames for.
     *
     * <p>{@link com.amazonaws.kinesisvideo.internal.client.NativeKinesisVideoClient#registerMediaSource(MediaSource)}
     * will {@link #initialize(MediaSourceSink)} it with an instance of {@link ProducerStreamSink} created using the
     * provided {@link StreamInfo} returned by this method.</p>
     *
     * @return the {@link StreamInfo} describing this stream
     * @throws KinesisVideoException if stream info cannot be determined
     */
    StreamInfo getStreamInfo() throws KinesisVideoException;

    /**
     * Initializes the media source with a sink that will receive produced frames.
     * {@link com.amazonaws.kinesisvideo.internal.client.NativeKinesisVideoClient#registerMediaSource(MediaSource)}
     * will call this method with an instance of {@link ProducerStreamSink} created using the
     * provided {@link StreamInfo} via {@link #getStreamInfo()}
     *
     * <p>This method:</p>
     * <ul>
     *   <li>Establishes the connection between this MediaSource and the provided sink</li>
     *   <li>Prepares the media source for frame production</li>
     *   <li>Must be called before {@link #start()}</li>
     * </ul>
     *
     * <p>After initialization, the MediaSource should be ready to start producing frames
     * when {@link #start()} is called.</p>
     *
     * @param mediaSourceSink the sink that will receive frames from this media source
     * @throws KinesisVideoException if initialization fails
     */
    void initialize(@Nonnull MediaSourceSink mediaSourceSink) throws KinesisVideoException;

    /**
     * Applies configuration settings to this media source.
     *
     * <p>This method allows runtime configuration of the media source with
     * source-specific parameters. Should typically be called before {@link #initialize(MediaSourceSink)}.</p>
     *
     * @param configuration the configuration to apply to this media source
     */
    void configure(MediaSourceConfiguration configuration);

    /**
     * Starts frame production and streaming.
     *
     * <p>After calling this method:</p>
     * <ul>
     *   <li>MediaSource begins capturing/generating frames</li>
     *   <li>Frames are continuously sent to the MediaSourceSink via {@link MediaSourceSink#onFrame(com.amazonaws.kinesisvideo.producer.KinesisVideoFrame)}</li>
     *   <li>Codec private data is sent via {@link MediaSourceSink#onCodecPrivateData(byte[])}</li>
     *   <li>Any metadata is sent via {@link MediaSourceSink#onFragmentMetadata(String, String, boolean)}</li>
     * </ul>
     *
     * <p>The MediaSource must be initialized before starting.</p>
     *
     * @throws KinesisVideoException if the media source cannot be started or is not properly initialized
     */
    void start() throws KinesisVideoException;

    /**
     * Stops frame production and streaming synchronously. This method should be idempotent.
     *
     * <p>After calling this method and it returns:</p>
     * <ul>
     *   <li>No more frames will be produced or sent to the sink</li>
     *   <li>Resources may be released, but the MediaSource can potentially be restarted</li>
     *   <li>Use {@link #isStopped()} to verify the stop operation completed</li>
     * </ul>
     *
     * <p>To completely clean up resources, call {@link #free()} after stopping.</p>
     *
     * @throws KinesisVideoException if the media source cannot be stopped cleanly
     */
    void stop() throws KinesisVideoException;

    /**
     * Returns true if the media source has been stopped and is no longer producing frames.
     *
     * <p>This method can be used to:</p>
     * <ul>
     *   <li>Verify that {@link #stop()} completed successfully</li>
     *   <li>Check if the media source needs to be restarted</li>
     *   <li>Determine if cleanup is needed</li>
     * </ul>
     *
     * @return true if the media source is stopped, false if it's still active
     */
    boolean isStopped();

    /**
     * Releases all resources held by this media source and performs final cleanup.
     *
     * <p>{@link com.amazonaws.kinesisvideo.internal.client.NativeKinesisVideoClient} will call this when
     * {@link NativeKinesisVideoClient#free()} is called with this Media Source still registered. If the Media Source
     * was {@link NativeKinesisVideoClient#unregisterMediaSource(MediaSource)}, then you will need to call this method
     * yourself.
     *
     * <p><strong>{@link MediaSource} cannot be used again after freeing.</strong></p>
     *
     * <p>This method:</p>
     * <ul>
     *   <li>Releases native resources (the native Kinesis Video Stream).</li>
     *   <li>Should be called after {@link #stop()} to ensure clean shutdown</li>
     *   <li>Makes the MediaSource unusable - it cannot be restarted after freeing</li>
     * </ul>
     *
     * @throws KinesisVideoException if cleanup fails
     */
    void free() throws KinesisVideoException;

    /**
     * Returns the sink that receives frames from this media source.
     *
     * <p>This is the same sink that was provided to {@link #initialize(MediaSourceSink)}.
     * Useful for accessing the underlying producer stream or for debugging.</p>
     *
     * @return the {@link MediaSourceSink} receiving frames from this source, or null if not initialized
     */
    MediaSourceSink getMediaSourceSink();

    /**
     * Returns stream-specific callback implementations for handling producer events.
     *
     * <p>StreamCallbacks allow the MediaSource to:</p>
     * <ul>
     *   <li>Receive acknowledgments from Kinesis Video Streams</li>
     *   <li>Handle stream errors and implement custom retry logic</li>
     *   <li>Monitor stream health and performance metrics</li>
     *   <li>Implement custom stream lifecycle management</li>
     * </ul>
     *
     * <p>Return null if no custom callbacks are needed (default behavior will be used).</p>
     *
     * @return custom {@link StreamCallbacks} implementation, or null for default behavior
     */
    @Nullable
    StreamCallbacks getStreamCallbacks();
}
