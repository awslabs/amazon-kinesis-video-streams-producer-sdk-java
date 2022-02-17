package com.amazonaws.kinesisvideo.producer;

/**
 * Client information object.
 *
 * NOTE: This should follow the structure defined in /client/Include.h
 *
 * NOTE: Suppressing Findbug error as this code will be accessed from native codebase.
 */
public class ClientInfo {
    /**
     * Current version for the structure as defined in the native code
     */
<<<<<<< HEAD
    public static final int CLIENT_INFO_CURRENT_VERSION = 2;
    public static final int DEFAULT_LOG_LEVEL = 4;

    public static enum AutomaticStreamingFlags {
        AUTOMATIC_STREAMING_INTERMITTENT_PRODUCER(0), AUTOMATIC_STREAMING_ALWAYS_CONTINUOUS(256);
        private final int streamingFlagValue;

        private AutomaticStreamingFlags(int streamingFlagValue) {
            this.streamingFlagValue = streamingFlagValue;
        }

        public int getStreamingFlagValue() {
            return streamingFlagValue;
        }

    }
=======
    public static final int CLIENT_INFO_CURRENT_VERSION = 0;
    public static final int DEFAULT_LOG_LEVEL = 4;
>>>>>>> 74da9fc (Merge develop into master for release 1.12.0 (#155))

    private final int mVersion;
    private final long mCreateClientTimeout;
    private final long mCreateStreamTimeout;
    private final long mStopStreamTimeout;
    private final long mOfflineBufferAvailabilityTimeout;
    private final int mLogLevel;
    private final boolean mLogMetric;
    private final AutomaticStreamingFlags mAutomaticStreamingFlags;

    public ClientInfo() {
        mVersion = CLIENT_INFO_CURRENT_VERSION;
        mCreateClientTimeout = 0L;
        mCreateStreamTimeout = 0L;
        mStopStreamTimeout = 0L;
        mOfflineBufferAvailabilityTimeout = 0L;
        mLogLevel = DEFAULT_LOG_LEVEL;
        mLogMetric = true;
        mAutomaticStreamingFlags = AutomaticStreamingFlags.AUTOMATIC_STREAMING_INTERMITTENT_PRODUCER;
    }

    public ClientInfo(final long createClientTimeout, final long createStreamTimeout, final long stopStreamTimeout,
<<<<<<< HEAD
                      final long offlineBufferAvailabilityTimeout, final int logLevel,
                      final boolean logMetric) {
        this(createClientTimeout, createStreamTimeout, stopStreamTimeout, offlineBufferAvailabilityTimeout,
                logLevel, logMetric, AutomaticStreamingFlags.AUTOMATIC_STREAMING_INTERMITTENT_PRODUCER);
    }

    public ClientInfo(final long createClientTimeout, final long createStreamTimeout, final long stopStreamTimeout,
                      final long offlineBufferAvailabilityTimeout, final int logLevel,
                      final boolean logMetric, final AutomaticStreamingFlags flag) {
=======
                      final long offlineBufferAvailabilityTimeout,
                      final int logLevel, final boolean logMetric) {
>>>>>>> 74da9fc (Merge develop into master for release 1.12.0 (#155))
        mVersion = CLIENT_INFO_CURRENT_VERSION;
        mCreateClientTimeout = createClientTimeout;
        mCreateStreamTimeout = createStreamTimeout;
        mStopStreamTimeout = stopStreamTimeout;
        mOfflineBufferAvailabilityTimeout = offlineBufferAvailabilityTimeout;
<<<<<<< HEAD
        mLogLevel = logLevel;
=======
        mLogLevel = DEFAULT_LOG_LEVEL;
>>>>>>> 74da9fc (Merge develop into master for release 1.12.0 (#155))
        mLogMetric = logMetric;
        mAutomaticStreamingFlags = flag;
    }

    public int getVersion() {
        return mVersion;
    }

    public long getCreateClientTimeout() {
        return mCreateClientTimeout;
    }

    public long getCreateStreamTimeout() {
        return mCreateStreamTimeout;
    }

    public long getStopStreamTimeout() {
        return mStopStreamTimeout;
    }

    public long getOfflineBufferAvailabilityTimeout() {
        return mOfflineBufferAvailabilityTimeout;
    }

    public int getLoggerLogLevel() {
        return mLogLevel;
    }

    public boolean getLogMetric() {
        return mLogMetric;
    }

    public int getAutomaticStreamingFlags() {
        return mAutomaticStreamingFlags.getStreamingFlagValue();
    }
}
