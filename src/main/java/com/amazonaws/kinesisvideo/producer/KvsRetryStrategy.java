package com.amazonaws.kinesisvideo.producer;

import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.util.CalledByNativeCode;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Configuration class for retry strategy settings.
 * This maps to the native KvsRetryStrategy struct in PIC.
 * <p>
 * <strong>NOTE:</strong> RetryStrategy only gives control over the retry wait times.
 * The "which errors to retry" and "how many times to retry" is handled
 * by the PIC state machine.
 * </p>
 */
public class KvsRetryStrategy {

    /**
     * Corresponds to the native counterpart.
     */
    public enum RetryStrategyType {
        /**
         * Use the defaults from PIC.
         */
        DISABLED(0x00),
        /**
         * Use the exponential backoff from PIC.
         */
        EXPONENTIAL_BACKOFF_WAIT(0x01);

        private final int value;

        RetryStrategyType(final int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }

        public static RetryStrategyType fromValue(final int value) {
            for (final RetryStrategyType type : values()) {
                if (type.value == value) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown retry strategy type: " + value);
        }
    }

    private final RetryStrategyType retryStrategyType;
    private final ExponentialBackoffRetryStrategyConfig exponentialBackoffConfig;

    @SuppressWarnings("ConstantConditions")
    public KvsRetryStrategy(@Nonnull final KvsRetryStrategyBuilder builder) {
        Preconditions.checkArgument(builder != null, "KvsRetryStrategy builder cannot be null!");

        this.retryStrategyType = builder.retryStrategyType;
        this.exponentialBackoffConfig = builder.exponentialBackoffConfig;
    }

    public RetryStrategyType getRetryStrategyType() {
        return this.retryStrategyType;
    }

    @CalledByNativeCode
    public int getRetryStrategyTypeValue() {
        return getRetryStrategyType().getValue();
    }

    @CalledByNativeCode
    public ExponentialBackoffRetryStrategyConfig getExponentialBackoffConfig() {
        return this.exponentialBackoffConfig;
    }

    public interface RetryStrategyTypeStep {
        /**
         * Using {@link RetryStrategyType#DISABLED}.
         */
        KvsRetryStrategyBuilder disabled();

        /**
         * Using {@link RetryStrategyType#EXPONENTIAL_BACKOFF_WAIT}.
         */
        RetryStrategyConfigStep exponentialBackoff();
    }

    public interface RetryStrategyConfigStep {
        KvsRetryStrategyBuilder config(ExponentialBackoffRetryStrategyConfig exponentialBackoffConfig);
    }

    public static class KvsRetryStrategyBuilder implements RetryStrategyTypeStep, RetryStrategyConfigStep {
        @Nonnull
        private RetryStrategyType retryStrategyType = RetryStrategyType.DISABLED;
        @Nullable
        private ExponentialBackoffRetryStrategyConfig exponentialBackoffConfig = null;

        private KvsRetryStrategyBuilder() {
        }

        public static RetryStrategyTypeStep with() {
            return new KvsRetryStrategyBuilder();
        }

        public static KvsRetryStrategy defaults() {
            return new KvsRetryStrategyBuilder().build();
        }

        /**
         * Use the PIC defaults for the retry wait time calculations.
         */
        @Override
        public KvsRetryStrategyBuilder disabled() {
            this.retryStrategyType = RetryStrategyType.DISABLED;
            return this;
        }

        /**
         * Use the exponential backoff for the retry wait time calculations.
         */
        @Override
        public RetryStrategyConfigStep exponentialBackoff() {
            this.retryStrategyType = RetryStrategyType.EXPONENTIAL_BACKOFF_WAIT;
            return this;
        }

        @Override
        public KvsRetryStrategyBuilder config(@Nullable final ExponentialBackoffRetryStrategyConfig exponentialBackoffConfig) {
            this.exponentialBackoffConfig = exponentialBackoffConfig;
            return this;
        }

        public KvsRetryStrategy build() {
            return new KvsRetryStrategy(this);
        }
    }
}
