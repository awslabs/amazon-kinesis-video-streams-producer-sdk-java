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
    public static final int CLIENT_INFO_CURRENT_VERSION = 3;
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

    private final int mVersion;
    private final long mCreateClientTimeout;
    private final long mCreateStreamTimeout;
    private final long mStopStreamTimeout;
    private final long mOfflineBufferAvailabilityTimeout;

    private final int mLogLevel;
    private final boolean mLogMetric;
    private final AutomaticStreamingFlags mAutomaticStreamingFlags;

    private final long mServiceCompletionTimeout;

    private final long mServiceConnectionTimeout;

    //private final KvsRetryStrategyCallbacks mKvsRetryStrategyCallbacks;


    public ClientInfo() {
        mVersion = CLIENT_INFO_CURRENT_VERSION;
        mCreateClientTimeout = 0L;
        mCreateStreamTimeout = 0L;
        mStopStreamTimeout = 0L;
        mOfflineBufferAvailabilityTimeout = 0L;
        mLogLevel = DEFAULT_LOG_LEVEL;
        mLogMetric = true;
        mAutomaticStreamingFlags = AutomaticStreamingFlags.AUTOMATIC_STREAMING_INTERMITTENT_PRODUCER;
        mServiceConnectionTimeout = 0L;
        mServiceCompletionTimeout = 0L;
    }

    public ClientInfo(final long createClientTimeout, final long createStreamTimeout, final long stopStreamTimeout,
                      final long offlineBufferAvailabilityTimeout, final int logLevel,
                      final boolean logMetric) {
        this(createClientTimeout, createStreamTimeout, stopStreamTimeout, offlineBufferAvailabilityTimeout,
                logLevel, logMetric, AutomaticStreamingFlags.AUTOMATIC_STREAMING_INTERMITTENT_PRODUCER);
    }

    public ClientInfo(final long createClientTimeout, final long createStreamTimeout, final long stopStreamTimeout,
                      final long offlineBufferAvailabilityTimeout, final int logLevel,
                      final boolean logMetric, final AutomaticStreamingFlags flag) {
        mVersion = CLIENT_INFO_CURRENT_VERSION;
        mCreateClientTimeout = createClientTimeout;
        mCreateStreamTimeout = createStreamTimeout;
        mStopStreamTimeout = stopStreamTimeout;
        mOfflineBufferAvailabilityTimeout = offlineBufferAvailabilityTimeout;
        mLogLevel = logLevel;
        mLogMetric = logMetric;
        mAutomaticStreamingFlags = flag;
        mServiceConnectionTimeout = 0L;
        mServiceCompletionTimeout = 0L;
    }

    public ClientInfo(final long createClientTimeout, final long createStreamTimeout, final long stopStreamTimeout,
                      final long offlineBufferAvailabilityTimeout, final long serviceConnectionTimeout,
                      final long serviceCompletionTimeout, final int logLevel,
                      final boolean logMetric, final AutomaticStreamingFlags flag) {
        mVersion = CLIENT_INFO_CURRENT_VERSION;
        mCreateClientTimeout = createClientTimeout;
        mCreateStreamTimeout = createStreamTimeout;
        mStopStreamTimeout = stopStreamTimeout;
        mOfflineBufferAvailabilityTimeout = offlineBufferAvailabilityTimeout;
        mLogLevel = logLevel;
        mLogMetric = logMetric;
        mAutomaticStreamingFlags = flag;
        mServiceConnectionTimeout = serviceConnectionTimeout;
        mServiceCompletionTimeout = serviceCompletionTimeout;
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

    public long getServiceConnectionTimeout() { return mServiceConnectionTimeout; }

    public long getServiceCompletionTimeout() { return mServiceCompletionTimeout; }

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
