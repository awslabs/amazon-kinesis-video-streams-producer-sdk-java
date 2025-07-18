package com.amazonaws.kinesisvideo.producer;

import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.util.StreamInfoConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import java.nio.ByteBuffer;

import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_TRACK_ID;

/**
 * Kinesis Video frame representation.
 * <p>NOTE: This class must match the Frame declaration in native code in
 * /mkvgen/Include.h</p>
 *
 * @see <a href="https://github.com/awslabs/amazon-kinesis-video-streams-pic/blob/master/src/mkvgen/include/com/amazonaws/kinesis/video/mkvgen/Include.h">PIC's MKVGEN</a>
 */
@Immutable
@ThreadSafe
public class KinesisVideoFrame {

    private static final Logger log = LogManager.getLogger(KinesisVideoFrame.class);

    /**
     * Current version for the structure as defined in the native code
     */
    public static final int FRAME_CURRENT_VERSION = 0;

    public static final int FRAME_VERSION_ZERO = 0;

    /**
     * Version of frame structure
     */
    private final int version;

    /**
     * Index of the frame
     */
    private final int index;

    /**
     * Frame flags
     */
    private final int flags;

    /**
     * The decoding timestamp of the frame in 100ns precision
     */
    private final long decodingTs;

    /**
     * The presentation timestamp of the frame in 100ns precision
     */
    private final long presentationTs;

    /**
     * The duration of the frame in 100ns precision
     */
    private final long duration;

    /**
     * The track id of the frame
     */
    private final long trackId;

    /**
     * The actual frame data
     */
    @Nonnull
    private final ByteBuffer data;

    /**
     * Creates a V{@value #FRAME_VERSION_ZERO} struct version of a KinesisVideoFrame. Submit these frames to a Stream using
     * {@link com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream#putFrame(KinesisVideoFrame)}
     *
     * @param index          ID for this frame.
     * @param flags          Flags associated with this frame. They should be bitwise OR'ed together
     *                       when specifying more than 1. See {@link FrameFlags}.
     * @param decodingTs     DTS of this frame in hundreds of nanosecond units.
     * @param presentationTs PTS of this frame in hundreds of nanosecond units.
     * @param duration       Duration of this frame in hundreds of nanosecond units. Can be 0.
     * @param data           The frame contents.
     * @param trackId        The track number this frame belongs to.
     * @throws OutOfMemoryError if not enough memory to create a copy of the data.
     */
    public KinesisVideoFrame(final int index, final int flags, final long decodingTs, final long presentationTs,
                             final long duration, @Nonnull final ByteBuffer data, final long trackId) {

        Preconditions.checkNotNull(data, "Null data was passed in!");

        this.version = FRAME_VERSION_ZERO;
        this.index = index;
        this.flags = flags;
        this.decodingTs = decodingTs;
        this.presentationTs = presentationTs;
        this.duration = duration;
        this.trackId = trackId;

        // Make a copy of the content as to be truly immutable
        try {
            final ByteBuffer temp = ByteBuffer.allocateDirect(data.remaining());
            temp.put(data.duplicate());
            temp.flip();
            this.data = temp.asReadOnlyBuffer();
        } catch (final OutOfMemoryError err) {
            log.error("Ran out of memory while creating the frame with size: {}!", data.remaining(), err);
            throw err;
        }
    }

    /**
     * Creates a Kinesis Video Frame V0 with trackId = {@value StreamInfoConstants#DEFAULT_TRACK_ID}.
     * See {@link #KinesisVideoFrame(int, int, long, long, long, ByteBuffer, long)}
     */
    public KinesisVideoFrame(final int index, final int flags, final long decodingTs, final long presentationTs,
                             final long duration, @Nonnull final ByteBuffer data) {
        this(index, flags, decodingTs, presentationTs, duration, data, DEFAULT_TRACK_ID);
    }

    public int getIndex() {
        return this.index;
    }

    public int getFlags() {
        return this.flags;
    }

    public long getDecodingTs() {
        return this.decodingTs;
    }

    public long getPresentationTs() {
        return this.presentationTs;
    }

    public long getDuration() {
        return this.duration;
    }

    public int getSize() {
        return this.data.capacity();
    }

    /**
     * Native code will access this buffer via {@code GetDirectBufferAddress()}.
     * See {@code setFrame} in the JNI.
     *
     * @return a read-only ByteBuffer containing this frame's data.
     */
    @Nonnull
    public ByteBuffer getData() {
        // Don't return mData directly to make sure each caller gets its own set of position/limit pointers.
        return this.data.duplicate();
    }

    public long getTrackId() {
        return this.trackId;
    }

    @Override
    public String toString() {
        return new StringBuilder().append(getClass().getSimpleName()).append("{").append("index=").append(this.index)
                .append(", flags=").append(this.flags).append(", decodingTs=").append(this.decodingTs)
                .append(", presentationTs=").append(this.presentationTs).append(", duration=").append(this.duration)
                .append(", data=").append(this.data).append(", trackId=").append(this.trackId).append("}")
                .toString();
    }

    public int getVersion() {
        return this.version;
    }
}
