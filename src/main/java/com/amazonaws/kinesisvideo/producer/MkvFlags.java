package com.amazonaws.kinesisvideo.producer;

/**
 * MKV generator flags.
 *
 * NOTE: This structure must be the same as defined in /mkvgen/Include.h
 *
 *
 */

public class MkvFlags {
    /**
     * No flags specified. Used as a sentinel
     */
    public static final int MKV_GEN_FLAG_NONE = 0;

    /**
     * Always create clusters on the key frame boundary
     */
    public static final int MKV_GEN_KEY_FRAME_PROCESSING = (1 << 0);

    /**
     * Whether to use in-stream defined timestamps or call get time
     */
    public static final int MKV_GEN_IN_STREAM_TIME = (1 << 1);

    /**
     * Whether to generate absolute cluster timestamps
     */
    public static final int MKV_GEN_ABSOLUTE_CLUSTER_TIME = (1 << 2);
}
