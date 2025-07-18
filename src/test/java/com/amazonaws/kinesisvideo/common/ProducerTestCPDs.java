package com.amazonaws.kinesisvideo.common;

import com.amazonaws.kinesisvideo.producer.StreamInfo;

import java.util.Arrays;

/**
 * Utility class with some pre-defined CPDs used for the Stream creation.
 */
public final class ProducerTestCPDs {
    private ProducerTestCPDs() {
        throw new UnsupportedOperationException();
    }

    /**
     * AVCC format codec private data
     */
    public static final byte[] AVCC_EXTRA_DATA = {
            (byte) 0x01, (byte) 0x42, (byte) 0x00, (byte) 0x1E, (byte) 0xFF, (byte) 0xE1, (byte) 0x00, (byte) 0x22,
            (byte) 0x27, (byte) 0x42, (byte) 0x00, (byte) 0x1E, (byte) 0x89, (byte) 0x8B, (byte) 0x60, (byte) 0x50,
            (byte) 0x1E, (byte) 0xD8, (byte) 0x08, (byte) 0x80, (byte) 0x00, (byte) 0x13, (byte) 0x88,
            (byte) 0x00, (byte) 0x03, (byte) 0xD0, (byte) 0x90, (byte) 0x70, (byte) 0x30, (byte) 0x00, (byte) 0x5D,
            (byte) 0xC0, (byte) 0x00, (byte) 0x17, (byte) 0x70, (byte) 0x5E, (byte) 0xF7, (byte) 0xC1, (byte) 0xF0,
            (byte) 0x88, (byte) 0x46, (byte) 0xE0, (byte) 0x01, (byte) 0x00, (byte) 0x04, (byte) 0x28, (byte) 0xCE,
            (byte) 0x1F, (byte) 0x20};

    /**
     * Annex-B format codec private data (NAL units with start codes).
     */
    public static final byte[] ANNEXB_EXTRA_DATA = {
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x67, (byte) 0x42, (byte) 0x00, (byte) 0x1E,
            (byte) 0x89, (byte) 0x8B, (byte) 0x60, (byte) 0x50, (byte) 0x1E, (byte) 0xD8, (byte) 0x08, (byte) 0x80,
            (byte) 0x00, (byte) 0x13, (byte) 0x88, (byte) 0x00, (byte) 0x03, (byte) 0xD0, (byte) 0x90, (byte) 0x70,
            (byte) 0x30, (byte) 0x00, (byte) 0x5D, (byte) 0xC0, (byte) 0x00, (byte) 0x17, (byte) 0x70, (byte) 0x5E,
            (byte) 0xF7, (byte) 0xC1, (byte) 0xF0, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x68,
            (byte) 0xCE, (byte) 0x1F, (byte) 0x20
    };

    /**
     * Select the appropriate CPD based on the NAL adaptation flag
     */
    public static byte[] getTestCPD(final StreamInfo.NalAdaptationFlags nalAdaptationFlags) {
        if (nalAdaptationFlags == StreamInfo.NalAdaptationFlags.NAL_ADAPTATION_AVCC_NALS ||
                nalAdaptationFlags == StreamInfo.NalAdaptationFlags.NAL_ADAPTATION_ANNEXB_CPD_NALS ||
                nalAdaptationFlags == StreamInfo.NalAdaptationFlags.NAL_ADAPTATION_ANNEXB_CPD_AND_FRAME_NALS) {
            return Arrays.copyOf(ProducerTestCPDs.ANNEXB_EXTRA_DATA, ProducerTestCPDs.ANNEXB_EXTRA_DATA.length);
        } else {
            return Arrays.copyOf(ProducerTestCPDs.AVCC_EXTRA_DATA, ProducerTestCPDs.AVCC_EXTRA_DATA.length);
        }
    }
}
