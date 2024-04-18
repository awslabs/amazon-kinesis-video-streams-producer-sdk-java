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

    
    private final int mVersion;
    private final long mCreateClientTimeout;
    private final long mCreateStreamTimeout;
    private final long mStopStreamTimeout;
    private final long mOfflineBufferAvailabilityTimeout;

    private final int mLoggerLogLevel;
    private final boolean mLogMetric;
    private final AutomaticStreamingFlags mAutomaticStreamingFlags;

    private final long mServiceCallCompletionTimeout;
    private final long mServiceCallConnectionTimeout;

    /**
     * NOTE: The below members are not supported for setting/getting in Java. These will be set to
     * default values in the JNI and C layers.
     */
    private final long mMetricLoggingPeriod;
    private final long mReservedCallbackPeriod;
    private final KvsRetryStrategy mKvsRetryStrategy;
    private final KvsRetryStrategyCallbacks mKvsRetryStrategyCallbacks;


    public ClientInfo() {
        mVersion = CLIENT_INFO_CURRENT_VERSION;
        mCreateClientTimeout = 0L;
        mCreateStreamTimeout = 0L;
        mStopStreamTimeout = 0L;
        mOfflineBufferAvailabilityTimeout = 0L;
        mLoggerLogLevel = DEFAULT_LOG_LEVEL;
        mLogMetric = true;
        mAutomaticStreamingFlags = AutomaticStreamingFlags.AUTOMATIC_STREAMING_INTERMITTENT_PRODUCER;
        mServiceCallCompletionTimeout = 0L;
        mServiceCallConnectionTimeout = 0L;
        mMetricLoggingPeriod = 0;
        mReservedCallbackPeriod = 0;
        mKvsRetryStrategyCallbacks = new DefaultKvsRetryStrategyCallbacks();
        mKvsRetryStrategy = new KvsRetryStrategy();
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
        mLoggerLogLevel = logLevel;
        mLogMetric = logMetric;
        mAutomaticStreamingFlags = flag;
        mServiceCallCompletionTimeout = 0L;
        mServiceCallConnectionTimeout = 0L;
        mMetricLoggingPeriod = 0;
        mReservedCallbackPeriod = 0;
        mKvsRetryStrategyCallbacks = new DefaultKvsRetryStrategyCallbacks();
        mKvsRetryStrategy = new KvsRetryStrategy();    }

    public ClientInfo(final long createClientTimeout, final long createStreamTimeout, final long stopStreamTimeout,
                      final long offlineBufferAvailabilityTimeout, final long serviceCallConnectionTimeou,
                      final long serviceCallCompletionTimeout, final int logLevel,
                      final boolean logMetric, final AutomaticStreamingFlags flag) {
        mVersion = CLIENT_INFO_CURRENT_VERSION;
        mCreateClientTimeout = createClientTimeout;
        mCreateStreamTimeout = createStreamTimeout;
        mStopStreamTimeout = stopStreamTimeout;
        mOfflineBufferAvailabilityTimeout = offlineBufferAvailabilityTimeout;
        mLoggerLogLevel = logLevel;
        mLogMetric = logMetric;
        mAutomaticStreamingFlags = flag;
        mServiceCallCompletionTimeout = serviceCallCompletionTimeout;
        mServiceCallConnectionTimeout = serviceCallConnectionTimeou;
        mMetricLoggingPeriod = 0;
        mReservedCallbackPeriod = 0;
        mKvsRetryStrategyCallbacks = new DefaultKvsRetryStrategyCallbacks();
        mKvsRetryStrategy = new KvsRetryStrategy();    }

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

    public long getServiceCallCompletionTimeout() {
        return mServiceCallCompletionTimeout;
    }

    public long getServiceCallConnectionTimeout() {
        return mServiceCallConnectionTimeout;
    }

    public int getLoggerLogLevel() {
        return mLoggerLogLevel;
    }

    public boolean getLogMetric() {
        return mLogMetric;
    }

    public int getAutomaticStreamingFlags() {
        return mAutomaticStreamingFlags.getStreamingFlagValue();
    }


    /**
     * NOTE: The below getters are not supported for setting/getting in Java. These will return
     * null to be initialized to default/null values in the JNI and C layers.
     */
    public long getMetricLoggingPeriod() {
        return 0;
    }
    
    public long getReservedCallbackPeriod() {
        return 0;
    }

    public KvsRetryStrategy getKvsRetryStrategy() {
        return null;
    }

    public KvsRetryStrategyCallbacks getKvsRetryStrategyCallbacks() {
        return null;
    }
}
