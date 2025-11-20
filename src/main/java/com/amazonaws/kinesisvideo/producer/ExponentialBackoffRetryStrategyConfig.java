package com.amazonaws.kinesisvideo.producer;

import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.util.CalledByNativeCode;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Configuration for exponential backoff retry strategy.
 * This maps to the native ExponentialBackoffRetryStrategyConfig struct in PIC.
 * <p>
 * Use 0 for any parameter to let PIC use its built-in default values.
 * </p>
 */
public class ExponentialBackoffRetryStrategyConfig {

    /**
     * Jitter types that correspond to the native ExponentialBackoffJitterType enum
     * <p>
     * Jitter is added after the calculated wait time. For example for the default configuration in PIC:
     * </p>
     * <ol>
     *     <li>Wait: 1000ms + jitter</li>
     *     <li>Wait: 2000ms + jitter</li>
     *     <li>Wait: 4000ms + jitter</li>
     *     <li>Wait: 8000ms + jitter</li>
     *     <li>Wait: 16000ms + jitter</li>
     *     <li>Wait: 16000ms + jitter</li>
     *     <li>Wait: 16000ms + jitter</li>
     * </ol>
     */
    public enum JitterType {
        /**
         * jitter = random number between {@code [0, wait time)}
         * <p>
         * This means the calculated wait time can be at most doubled.
         * </p>
         */
        FULL_JITTER(0x01),
        /**
         * jitter = random number between {@code [0, jitter factor)}
         *
         * @see #jitterFactorMs
         */
        FIXED_JITTER(0x02),
        /**
         * jitter = 0
         */
        NO_JITTER(0x03);

        private final int value;

        JitterType(final int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }

        public static JitterType fromValue(final int value) {
            for (final JitterType type : values()) {
                if (type.value == value) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown jitter type: " + value);
        }
    }

    // Sentinel value - 0 means "use PIC defaults"
    public static final long USE_PIC_DEFAULT = 0;

    /**
     * Max retries before resetting the count back to 0. Allows you to have a "wave" of times.
     * Use {@link #USE_PIC_DEFAULT} which is equal to KVS_INFINITE_EXPONENTIAL_RETRIES (0) from PIC.
     */
    private final long maxRetryCount;

    /**
     * Maximum retry wait time. Once the retry wait time reaches this value,
     * subsequent retries will wait for maxRetryWaitTime (plus jitter).
     */
    private final long maxRetryWaitTimeMs;

    // Values from PIC, see Include.h
    public static final long MIN_KVS_MAX_WAIT_TIME_MILLISECONDS = 10000L;
    public static final long LIMIT_KVS_MAX_WAIT_TIME_MILLISECONDS = 25000L;

    /**
     * Factor for computing the exponential backoff wait time
     */
    private final long retryFactorTimeMs;

    // Values from PIC, see Include.h
    public static final long MIN_KVS_RETRY_TIME_FACTOR_MILLISECONDS = 50L;
    public static final long LIMIT_KVS_RETRY_TIME_FACTOR_MILLISECONDS = 1000L;

    /**
     * The minimum time between two consecutive retries after which retry state will be reset i.e. retries
     * will start from initial retry state.
     */
    private final long minTimeToResetRetryStateMs;

    // Values from PIC, see Include.h
    public static final long MIN_KVS_MIN_TIME_TO_RESET_RETRY_STATE_MILLISECONDS = 90000L;
    public static final long LIMIT_KVS_MIN_TIME_TO_RESET_RETRY_STATE_MILLISECONDS = 120000L;

    /**
     * Jitter type indicating how much jitter to be added
     * Default will be {@link JitterType#FULL_JITTER}
     */
    private final JitterType jitterType;

    /**
     * Factor determining random jitter value.
     * Jitter will be between {@code [0, jitterFactor)}.
     * This parameter is only valid for jitter type {@link JitterType#FIXED_JITTER}
     */
    private final long jitterFactorMs;

    // Values from PIC, see Include.h
    public static final long MIN_KVS_JITTER_FACTOR_MILLISECONDS = 50L;
    public static final long LIMIT_KVS_JITTER_FACTOR_MILLISECONDS = 600L;

    @SuppressWarnings("ConstantConditions")
    public ExponentialBackoffRetryStrategyConfig(@Nonnull final ExponentialBackoffRetryStrategyConfigBuilder builder) {
        Preconditions.checkArgument(builder != null, "ExponentialBackoffRetryStrategyConfigBuilder builder cannot be null");

        this.maxRetryCount = builder.maxRetryCount;
        this.maxRetryWaitTimeMs = builder.maxRetryWaitTimeMs;
        this.retryFactorTimeMs = builder.retryFactorTimeMs;
        this.minTimeToResetRetryStateMs = builder.minTimeToResetRetryStateMs;
        this.jitterType = builder.jitterType;
        this.jitterFactorMs = builder.jitterFactor;
    }

    @CalledByNativeCode
    public long getMaxRetryCount() {
        return this.maxRetryCount;
    }

    @CalledByNativeCode
    public long getMaxRetryWaitTimeMs() {
        return this.maxRetryWaitTimeMs;
    }

    @CalledByNativeCode
    public long getRetryFactorTimeMs() {
        return this.retryFactorTimeMs;
    }

    @CalledByNativeCode
    public long getMinTimeToResetRetryStateMs() {
        return this.minTimeToResetRetryStateMs;
    }

    @Nullable
    public JitterType getJitterType() {
        return this.jitterType;
    }

    @CalledByNativeCode
    public int getJitterTypeValue() {
        return this.jitterType != null ? this.jitterType.getValue() : (int) USE_PIC_DEFAULT;
    }

    @CalledByNativeCode
    public long getJitterFactor() {
        return this.jitterFactorMs;
    }

    @Override
    public String toString() {
        return "ExponentialBackoffRetryStrategyConfig{" +
                "maxRetryCount=" + (this.maxRetryCount == USE_PIC_DEFAULT ? "PIC_DEFAULT" : this.maxRetryCount) +
                ", maxRetryWaitTimeMs=" + (this.maxRetryWaitTimeMs == USE_PIC_DEFAULT ? "PIC_DEFAULT" : this.maxRetryWaitTimeMs) +
                ", retryFactorTimeMs=" + (this.retryFactorTimeMs == USE_PIC_DEFAULT ? "PIC_DEFAULT" : this.retryFactorTimeMs) +
                ", minTimeToResetRetryStateMs=" + (this.minTimeToResetRetryStateMs == USE_PIC_DEFAULT ? "PIC_DEFAULT" : this.minTimeToResetRetryStateMs) +
                ", jitterType=" + (this.jitterType == null ? "PIC_DEFAULT" : this.jitterType) +
                ", jitterFactor=" + (this.jitterFactorMs == USE_PIC_DEFAULT ? "PIC_DEFAULT" : this.jitterFactorMs) +
                '}';
    }

    public interface MaxRetryCountStep {
        MaxRetryWaitTimeStep maxRetryCount(final long maxRetryCount);
    }

    public interface MaxRetryWaitTimeStep {
        RetryFactorTimeStep maxRetryWaitTimeMs(final long maxRetryWaitTimeMs);
    }

    public interface RetryFactorTimeStep {
        MinTimeToResetRetryStateStep retryFactorTimeMs(final long retryFactorTimeMs);
    }

    public interface MinTimeToResetRetryStateStep {
        JitterTypeStep minTimeToResetRetryStateMs(final long minTimeToResetRetryStateMs);
    }

    public interface JitterTypeStep {
        ExponentialBackoffRetryStrategyConfigBuilder noJitter();

        ExponentialBackoffRetryStrategyConfigBuilder fullJitter();

        JitterFactorStep fixedJitter();
    }

    public interface JitterFactorStep {
        ExponentialBackoffRetryStrategyConfigBuilder jitterFactorMillis(final long jitterFactor);
    }

    public static class ExponentialBackoffRetryStrategyConfigBuilder implements MaxRetryCountStep, MaxRetryWaitTimeStep,
            RetryFactorTimeStep, MinTimeToResetRetryStateStep, JitterTypeStep, JitterFactorStep {
        private long maxRetryCount = USE_PIC_DEFAULT;
        private long maxRetryWaitTimeMs = USE_PIC_DEFAULT;
        private long retryFactorTimeMs = USE_PIC_DEFAULT;
        private long minTimeToResetRetryStateMs = USE_PIC_DEFAULT;
        private JitterType jitterType = null;
        private long jitterFactor = USE_PIC_DEFAULT;

        private ExponentialBackoffRetryStrategyConfigBuilder() {
        }

        public static MaxRetryCountStep with() {
            return new ExponentialBackoffRetryStrategyConfigBuilder();
        }

        public static ExponentialBackoffRetryStrategyConfig defaults() {
            return new ExponentialBackoffRetryStrategyConfigBuilder().build();
        }

        /**
         * Max retries after which an error will be returned to the application.
         * For infinite retries, set this to KVS_INFINITE_EXPONENTIAL_RETRIES (0).
         */
        @Override
        public MaxRetryWaitTimeStep maxRetryCount(final long maxRetryCount) {
            Preconditions.checkArgument(maxRetryCount >= 0, "maxRetryCount cannot be negative");
            this.maxRetryCount = maxRetryCount;
            return this;
        }

        /**
         * Maximum retry wait time in milliseconds. Once the retry wait time reaches this value,
         * subsequent retries will wait for maxRetryWaitTime (plus jitter).
         */
        @Override
        public RetryFactorTimeStep maxRetryWaitTimeMs(final long maxRetryWaitTimeMs) {
            checkInRange(maxRetryWaitTimeMs, MIN_KVS_MAX_WAIT_TIME_MILLISECONDS, LIMIT_KVS_MAX_WAIT_TIME_MILLISECONDS);
            this.maxRetryWaitTimeMs = maxRetryWaitTimeMs;
            return this;
        }

        /**
         * Base factor for computing the exponential backoff wait time in milliseconds.
         * The formula is: {@code retryFactorTime * 2^retryCount + jitter}
         */
        @Override
        public MinTimeToResetRetryStateStep retryFactorTimeMs(final long retryFactorTimeMs) {
            checkInRange(retryFactorTimeMs, MIN_KVS_RETRY_TIME_FACTOR_MILLISECONDS, LIMIT_KVS_RETRY_TIME_FACTOR_MILLISECONDS);
            this.retryFactorTimeMs = retryFactorTimeMs;
            return this;
        }

        /**
         * The minimum time, in milliseconds, between two consecutive retries after which retry state will be reset
         * i.e. retries will start from initial retry state.
         */
        @Override
        public JitterTypeStep minTimeToResetRetryStateMs(final long minTimeToResetRetryStateMs) {
            checkInRange(minTimeToResetRetryStateMs, MIN_KVS_MIN_TIME_TO_RESET_RETRY_STATE_MILLISECONDS, LIMIT_KVS_MIN_TIME_TO_RESET_RETRY_STATE_MILLISECONDS);
            this.minTimeToResetRetryStateMs = minTimeToResetRetryStateMs;
            return this;
        }

        /**
         * jitter = 0
         */
        @Override
        public ExponentialBackoffRetryStrategyConfigBuilder noJitter() {
            this.jitterType = JitterType.NO_JITTER;
            return this;
        }

        /**
         * jitter = random number between {@code [0, calculated wait time)}
         * <p>
         * This means the calculated wait time can be at most doubled.
         * </p>
         */
        @Override
        public ExponentialBackoffRetryStrategyConfigBuilder fullJitter() {
            this.jitterType = JitterType.FULL_JITTER;
            return this;
        }

        /**
         * Jitter will be between {@code [0, jitterFactor)}.
         */
        @Override
        public JitterFactorStep fixedJitter() {
            this.jitterType = JitterType.FIXED_JITTER;
            return this;
        }

        /**
         * Specify jitterFactor in milliseconds.
         * Jitter will be between {@code [0, jitterFactor)}.
         */
        @Override
        public ExponentialBackoffRetryStrategyConfigBuilder jitterFactorMillis(final long jitterFactor) {
            checkInRange(jitterFactor, MIN_KVS_JITTER_FACTOR_MILLISECONDS, LIMIT_KVS_JITTER_FACTOR_MILLISECONDS);
            this.jitterFactor = jitterFactor;
            return this;
        }

        public ExponentialBackoffRetryStrategyConfig build() {
            return new ExponentialBackoffRetryStrategyConfig(this);
        }

        /**
         * Checks that {@code value} is either {@link #USE_PIC_DEFAULT} or
         * between {@code minimum} and {@code maximum} (inclusive).
         */
        private void checkInRange(final long value, final long minimum, final long maximum) {
            Preconditions.checkArgument(
                    value == USE_PIC_DEFAULT ||
                            (minimum <= value && value <= maximum)
                    , value + " must be between " + minimum + " and " + maximum + " (inclusive)");
        }
    }
}
