package com.amazonaws.kinesisvideo.stream.throttling;

/**
 * Interface for throttler based on bandwidth.
 */
public interface BandwidthThrottler {
    /**
     * Set upload max bandwidth in kilobits per seconds.
     *
     * @param kbps max bandwidth in kbps
     */
    void setUpstreamKbps(long kbps);

    /**
     * Get the allowed number of bytes to read from or write to socket.
     *
     * @param len maximum number of bytes.
     * @return allowed bytes
     */
    int getAllowedBytes(int len);
}
