package com.amazonaws.kinesisvideo.producer;

/**
 * Definition of the flags for a frame.
 *
 * NOTE: This structure must be the same as defined in /mkvgen/Include.h
 *
 *
 */

public class FrameFlags {
    /**
     * No flags specified. Used as a sentinel
     */
    public static final int FRAME_FLAG_NONE = 0;

    /**
     * The frame is a key frame - I or IDR
     */
    public static final int FRAME_FLAG_KEY_FRAME = (1 << 0);

    /**
     * The frame is discardable - no other frames depend on it
     */
    public static final int FRAME_FLAG_DISCARDABLE_FRAME = (1 << 1);

    /**
     * The frame is invisible for rendering
     */
    public static final int FRAME_FLAG_INVISIBLE_FRAME = (1 << 2);

    /**
     * Returns whether the flags specify a key frame
     * @param frameFlags frame flags
     * @return whether it's a key frame
     */
    public static boolean isKeyFrame(final int frameFlags) {
        return (frameFlags & FRAME_FLAG_KEY_FRAME) == FRAME_FLAG_KEY_FRAME;
    }

    /**
     * Returns whether the flags specify a discardable frame
     * @param frameFlags frame flags
     * @return whether it's a discardable frame
     */
    public static boolean isDiscardableFrame(final int frameFlags) {
        return (frameFlags & FRAME_FLAG_DISCARDABLE_FRAME) == FRAME_FLAG_DISCARDABLE_FRAME;
    }

    /**
     * Returns whether the flags specify an invisible frame
     * @param frameFlags frame flags
     * @return whether it's an invisible frame
     */
    public static boolean isInvisibleFrame(final int frameFlags) {
        return (frameFlags & FRAME_FLAG_INVISIBLE_FRAME) == FRAME_FLAG_INVISIBLE_FRAME;
    }
}