package com.amazonaws.kinesisvideo.producer;

import com.amazonaws.kinesisvideo.util.CalledByNativeCode;

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
    public static final int LOG_LEVEL_DEBUG = 1;

    public static enum AutomaticStreamingFlags {
        AUTOMATIC_STREAMING_INTERMITTENT_PRODUCER(0), AUTOMATIC_STREAMING_ALWAYS_CONTINUOUS(256);
        private final int streamingFlagValue;

        private AutomaticStreamingFlags(final int streamingFlagValue) {
            this.streamingFlagValue = streamingFlagValue;
        }

        public int getStreamingFlagValue() {
            return streamingFlagValue;
        }

    }

    private int mVersion;
    private long mCreateClientTimeout;
    private long mCreateStreamTimeout;
    private long mStopStreamTimeout;
    private long mOfflineBufferAvailabilityTimeout;
    private int mLogLevel;
    private boolean mLogMetric;
    private AutomaticStreamingFlags mAutomaticStreamingFlags;
    private long mServiceCallCompletionTimeout;
    private long mServiceCallConnectionTimeout;
    private long mMetricLoggingPeriod;
    private long mReservedCallbackPeriod;
    private KvsRetryStrategy mKvsRetryStrategy;

    @Deprecated
    public ClientInfo() {
        mVersion = CLIENT_INFO_CURRENT_VERSION;
        mCreateClientTimeout = 0L;
        mCreateStreamTimeout = 0L;
        mStopStreamTimeout = 0L;
        mOfflineBufferAvailabilityTimeout = 0L;
        mLogLevel = DEFAULT_LOG_LEVEL;
        mLogMetric = true;
        mAutomaticStreamingFlags = AutomaticStreamingFlags.AUTOMATIC_STREAMING_INTERMITTENT_PRODUCER;
        mServiceCallCompletionTimeout = 0L;
        mServiceCallConnectionTimeout = 0L;
        mMetricLoggingPeriod = 0L;
        mReservedCallbackPeriod = 0L;
        mKvsRetryStrategy = null;
    }

    @Deprecated
    public ClientInfo(final long createClientTimeout, final long createStreamTimeout, final long stopStreamTimeout,
                      final long offlineBufferAvailabilityTimeout, final int logLevel,
                      final boolean logMetric, final long serviceCallCompletionTimeout, final long serviceCallConnectionTimeout) {
        this(createClientTimeout, createStreamTimeout, stopStreamTimeout, offlineBufferAvailabilityTimeout,
                logLevel, logMetric, AutomaticStreamingFlags.AUTOMATIC_STREAMING_INTERMITTENT_PRODUCER, 
                serviceCallCompletionTimeout, serviceCallConnectionTimeout, 0L, 0L, null);
    }

    @Deprecated
    public ClientInfo(final long createClientTimeout, final long createStreamTimeout, final long stopStreamTimeout,
                      final long offlineBufferAvailabilityTimeout, final int logLevel,
                      final boolean logMetric, final AutomaticStreamingFlags flag, final long serviceCallCompletionTimeout,
                      final long serviceCallConnectionTimeout) {
        this(createClientTimeout, createStreamTimeout, stopStreamTimeout, offlineBufferAvailabilityTimeout,
                logLevel, logMetric, flag, serviceCallCompletionTimeout, serviceCallConnectionTimeout, 0L, 0L, null);
    }

    @Deprecated
    public ClientInfo(final long createClientTimeout, final long createStreamTimeout, final long stopStreamTimeout,
                      final long offlineBufferAvailabilityTimeout, final int logLevel,
                      final boolean logMetric, final AutomaticStreamingFlags flag, final long serviceCallCompletionTimeout,
                      final long serviceCallConnectionTimeout, final long metricLoggingPeriod, 
                      final long reservedCallbackPeriod, final KvsRetryStrategy kvsRetryStrategy) {
        mVersion = CLIENT_INFO_CURRENT_VERSION;
        mCreateClientTimeout = createClientTimeout;
        mCreateStreamTimeout = createStreamTimeout;
        mStopStreamTimeout = stopStreamTimeout;
        mOfflineBufferAvailabilityTimeout = offlineBufferAvailabilityTimeout;
        mLogLevel = logLevel;
        mLogMetric = logMetric;
        mAutomaticStreamingFlags = flag;
        mServiceCallCompletionTimeout = serviceCallCompletionTimeout;
        mServiceCallConnectionTimeout = serviceCallConnectionTimeout;
        mMetricLoggingPeriod = metricLoggingPeriod;
        mReservedCallbackPeriod = reservedCallbackPeriod;
        mKvsRetryStrategy = kvsRetryStrategy;
    }

    public static ClientInfo createClientInfoV0(final long createClientTimeout, final long createStreamTimeout, final long stopStreamTimeout,
                                                final long offlineBufferAvailabilityTimeout, final int logLevel, final boolean logMetric) {
        return new ClientInfo(createClientTimeout, createStreamTimeout, stopStreamTimeout, offlineBufferAvailabilityTimeout, logLevel, logMetric);
    }

    // V0 constructor
    private ClientInfo(final long createClientTimeout, final long createStreamTimeout, final long stopStreamTimeout,
                       final long offlineBufferAvailabilityTimeout, final int logLevel, final boolean logMetric) {
        this.mVersion = 0;
        this.mCreateClientTimeout = createClientTimeout;
        this.mCreateStreamTimeout = createStreamTimeout;
        this.mStopStreamTimeout = stopStreamTimeout;
        this.mOfflineBufferAvailabilityTimeout = offlineBufferAvailabilityTimeout;
        this.mLogLevel = logLevel;
        this.mLogMetric = logMetric;
    }

    public static ClientInfo createClientInfoV1(final long createClientTimeout, final long createStreamTimeout, final long stopStreamTimeout,
                                                final long offlineBufferAvailabilityTimeout, final int logLevel, final boolean logMetric, final long metricLoggingPeriod) {
        return new ClientInfo(createClientTimeout, createStreamTimeout, stopStreamTimeout, offlineBufferAvailabilityTimeout, logLevel, logMetric, metricLoggingPeriod);
    }

    // V1 constructor
    private ClientInfo(final long createClientTimeout, final long createStreamTimeout, final long stopStreamTimeout,
                       final long offlineBufferAvailabilityTimeout, final int logLevel, final boolean logMetric,
                       final long metricLoggingPeriod) {
        this(createClientTimeout, createStreamTimeout, stopStreamTimeout, offlineBufferAvailabilityTimeout, logLevel, logMetric);

        this.mVersion = 1;
        this.mMetricLoggingPeriod = metricLoggingPeriod;
    }

    public static ClientInfo createClientInfoV2(final long createClientTimeout, final long createStreamTimeout, final long stopStreamTimeout,
                                                final long offlineBufferAvailabilityTimeout, final int logLevel, final boolean logMetric,
                                                final long metricLoggingPeriod, final AutomaticStreamingFlags automaticStreamingFlags,
                                                final long reservedCallbackPeriod, KvsRetryStrategy kvsRetryStrategy) {
        return new ClientInfo(createClientTimeout, createStreamTimeout, stopStreamTimeout, offlineBufferAvailabilityTimeout, logLevel,
                logMetric, metricLoggingPeriod, automaticStreamingFlags, reservedCallbackPeriod, kvsRetryStrategy);
    }

    // V2 constructor
    private ClientInfo(final long createClientTimeout, final long createStreamTimeout, final long stopStreamTimeout,
                       final long offlineBufferAvailabilityTimeout, final int logLevel, final boolean logMetric,
                       final long metricLoggingPeriod, final AutomaticStreamingFlags automaticStreamingFlags,
                       final long reservedCallbackPeriod, KvsRetryStrategy kvsRetryStrategy) {
        this(createClientTimeout, createStreamTimeout, stopStreamTimeout, offlineBufferAvailabilityTimeout, logLevel, logMetric);

        this.mVersion = 2;
        this.mMetricLoggingPeriod = metricLoggingPeriod;
        this.mAutomaticStreamingFlags = automaticStreamingFlags;
        this.mReservedCallbackPeriod = reservedCallbackPeriod;
        this.mKvsRetryStrategy = kvsRetryStrategy;
    }

    public static ClientInfo createClientInfoV3(final long createClientTimeout, final long createStreamTimeout, final long stopStreamTimeout,
                                                final long offlineBufferAvailabilityTimeout, final int logLevel, final boolean logMetric,
                                                final long metricLoggingPeriod, final AutomaticStreamingFlags automaticStreamingFlags,
                                                final long reservedCallbackPeriod, final KvsRetryStrategy kvsRetryStrategy,
                                                final long serviceCallCompletionTimeout, final long serviceCallConnectionTimeout) {
        return new ClientInfo(createClientTimeout, createStreamTimeout, stopStreamTimeout, offlineBufferAvailabilityTimeout, logLevel,
                logMetric, metricLoggingPeriod, automaticStreamingFlags, reservedCallbackPeriod, kvsRetryStrategy,
                serviceCallCompletionTimeout, serviceCallConnectionTimeout);
    }

    // V3 constructor
    private ClientInfo(final long createClientTimeout, final long createStreamTimeout, final long stopStreamTimeout,
                       final long offlineBufferAvailabilityTimeout, final int logLevel, final boolean logMetric,
                       final long metricLoggingPeriod, final AutomaticStreamingFlags automaticStreamingFlags,
                       final long reservedCallbackPeriod, final KvsRetryStrategy kvsRetryStrategy,
                       final long serviceCallCompletionTimeout, final long serviceCallConnectionTimeout) {
        this(createClientTimeout, createStreamTimeout, stopStreamTimeout, offlineBufferAvailabilityTimeout, logLevel, logMetric,
                metricLoggingPeriod, automaticStreamingFlags, reservedCallbackPeriod, kvsRetryStrategy);

        this.mVersion = 3;
        this.mServiceCallCompletionTimeout = serviceCallCompletionTimeout;
        this.mServiceCallConnectionTimeout = serviceCallConnectionTimeout;
    }

    @CalledByNativeCode
    public int getVersion() {
        return mVersion;
    }

    @CalledByNativeCode
    public long getCreateClientTimeout() {
        return mCreateClientTimeout;
    }

    @CalledByNativeCode
    public long getCreateStreamTimeout() {
        return mCreateStreamTimeout;
    }

    @CalledByNativeCode
    public long getStopStreamTimeout() {
        return mStopStreamTimeout;
    }

    @CalledByNativeCode
    public long getOfflineBufferAvailabilityTimeout() {
        return mOfflineBufferAvailabilityTimeout;
    }

    @CalledByNativeCode
    public int getLoggerLogLevel() {
        return mLogLevel;
    }

    @CalledByNativeCode
    public boolean getLogMetric() {
        return mLogMetric;
    }

    @CalledByNativeCode
    public int getAutomaticStreamingFlags() {
        if (mAutomaticStreamingFlags == null) {
            return 0;
        }
        return mAutomaticStreamingFlags.getStreamingFlagValue();
    }

    @CalledByNativeCode
    public long getServiceCompletionTimeout() {
        return mServiceCallCompletionTimeout;
    }

    @CalledByNativeCode
    public long getServiceConnectionTimeout() {
        return mServiceCallConnectionTimeout;
    }

    public long getMetricLoggingPeriod() {
        return mMetricLoggingPeriod;
    }

    public long getReservedCallbackPeriod() {
        return mReservedCallbackPeriod;
    }

    public KvsRetryStrategy getKvsRetryStrategy() {
        return mKvsRetryStrategy;
    }
}
