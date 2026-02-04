package com.amazonaws.kinesisvideo.internal.client.mediasource;

import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.internal.producer.StreamEventMetadata;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Interface that acts as a consumer/sink for media frames produced by a {@link MediaSource}.
 *
 * <p>The MediaSourceSink serves as the bridge between a MediaSource and the Kinesis Video Streams
 * Producer SDK. It receives media frames and metadata from the MediaSource and forwards them
 * to the underlying Kinesis Video Producer for streaming.</p>
 *
 * <p><strong>Key responsibilities:</strong></p>
 * <ul>
 *   <li>Receive video/audio frames from MediaSource via {@link #onFrame(KinesisVideoFrame)}</li>
 *   <li>Handle codec configuration data via {@link #onCodecPrivateData(byte[])}</li>
 *   <li>Process fragment-level metadata via {@link #onFragmentMetadata(String, String, boolean)}</li>
 *   <li>Forward all data to the underlying {@link KinesisVideoProducerStream}</li>
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
 * @see MediaSource
 * @see KinesisVideoFrame
 * @see KinesisVideoProducerStream
 */
public interface MediaSourceSink {

    /**
     * Receives a video or audio frame from the MediaSource for streaming to Kinesis Video Streams.
     *
     * <p>This is the primary method for frame delivery. The MediaSource calls this method
     * for each frame it produces (video frames, audio frames, or both).</p>
     *
     * <p><strong>Frame requirements:</strong></p>
     * <ul>
     *   <li>Frame must contain valid encoded data (H.264, H.265, AAC, etc.)</li>
     *   <li>Frame timestamps must be monotonically increasing</li>
     *   <li>Key frames should be marked appropriately for video</li>
     *   <li>Frame duration should be set for proper playback timing</li>
     * </ul>
     *
     * <p><strong>Error handling:</strong></p>
     * <ul>
     *   <li>Throws KinesisVideoException if frame cannot be processed</li>
     *   <li>MediaSource should handle exceptions and decide whether to continue or stop, combined with listening for
     *   any {@link com.amazonaws.kinesisvideo.producer.StreamCallbacks}.</li>
     * </ul>
     *
     * @param kinesisVideoFrame the frame to be streamed, containing encoded media data and metadata
     * @throws KinesisVideoException if the frame cannot be processed or streamed
     */
    void onFrame(final @Nonnull KinesisVideoFrame kinesisVideoFrame) throws KinesisVideoException;

    /**
     * Receives codec private data (codec configuration) for the default track.
     *
     * <p>Codec private data contains essential configuration information that decoders
     * need to properly decode the stream. Examples include:</p>
     * <ul>
     *   <li><strong>H.264:</strong> SPS (Sequence Parameter Set) and PPS (Picture Parameter Set)</li>
     *   <li><strong>H.265:</strong> VPS, SPS, and PPS</li>
     * </ul>
     *
     * <p><strong>When to send:</strong></p>
     * <ul>
     *   <li>At stream start, before sending any frames</li>
     *   <li>When codec parameters change (e.g. resolution)</li>
     *   <li>After stream errors that require reinitialization</li>
     * </ul>
     *
     * <p>This method is equivalent to calling {@link #onCodecPrivateData(byte[], int)}
     * with the default track ID.</p>
     *
     * @param codecPrivateData the codec configuration data, or null if not available
     * @throws KinesisVideoException if the codec data cannot be processed
     */
    void onCodecPrivateData(final @Nullable byte[] codecPrivateData) throws KinesisVideoException;

    /**
     * Receives codec private data (codec configuration) for a specific track in multi-track streams.
     *
     * <p>This method is used for streams with multiple tracks (e.g., separate video and audio tracks).
     * Each track can have its own codec configuration data.</p>
     *
     * <p><strong>Example multi-track scenario:</strong></p>
     * <ul>
     *   <li>Video track (ID 1) with H.264 SPS/PPS</li>
     *   <li>Audio track (ID 2) with AAC configuration</li>
     * </ul>
     *
     * <p><strong>Track ID management:</strong></p>
     * <ul>
     *   <li>Track IDs should be consistent with those used in {@link KinesisVideoFrame}</li>
     *   <li>Track IDs must be unique within the stream</li>
     * </ul>
     *
     * @param codecPrivateData the codec configuration data for the specified track
     * @param trackId          the unique identifier for the track this codec data applies to
     * @throws KinesisVideoException if the codec data cannot be processed
     */
    void onCodecPrivateData(final @Nullable byte[] codecPrivateData, final int trackId) throws KinesisVideoException;

    /**
     * Receives fragment-level metadata to be associated with the stream.
     *
     * <p>Fragment metadata allows attaching custom key-value pairs to stream fragments.
     * This metadata can be retrieved when consuming the stream and is useful for:</p>
     * <ul>
     *   <li>Adding timestamps or sensor readings</li>
     *   <li>Marking important events or scenes</li>
     *   <li>Storing application-specific annotations (e.g. bounding boxes)</li>
     *   <li>Adding searchable tags for content discovery</li>
     * </ul>
     *
     * <p><strong>Usage examples:</strong></p>
     * <pre>
     * // Mark motion detection event
     * sink.onFragmentMetadata("motion_detected", "true", false);
     *
     * // Add sensor data
     * sink.onFragmentMetadata("temperature", "23.5", false);
     * </pre>
     *
     * @param metadataName  the name/key of the metadata field
     * @param metadataValue the value of the metadata field
     * @param persistent    true if metadata should be stored permanently, false for transient metadata
     * @throws KinesisVideoException if the metadata cannot be processed
     * @see <a href="https://docs.aws.amazon.com/kinesisvideostreams/latest/dg/limits.html#limits-streaming-metadata">Fragment metadata limits</a>
     */
    void onFragmentMetadata(final @Nonnull String metadataName, final @Nonnull String metadataValue, final boolean persistent)
            throws KinesisVideoException;
    
    void onEventMetadata(final int event, @Nullable final StreamEventMetadata streamEventMetadata)
            throws KinesisVideoException;

    /**
     * Returns the underlying Kinesis Video Producer stream that receives the media data.
     *
     * <p><strong>Caution:</strong> Direct manipulation of the producer stream should be done
     * carefully to avoid interfering with the normal MediaSource &rarr; MediaSourceSink flow.</p>
     *
     * @return the underlying {@link KinesisVideoProducerStream} instance
     */
    KinesisVideoProducerStream getProducerStream();
}
