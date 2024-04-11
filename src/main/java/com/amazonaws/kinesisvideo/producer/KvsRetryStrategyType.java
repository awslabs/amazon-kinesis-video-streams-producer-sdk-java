package com.amazonaws.kinesisvideo.producer;

public enum KvsRetryStrategyType {
    
    DISABLED(0), EXPONENTIAL_BACKOFF_WAIT(1), UNKNOWN(-1);

    private final int mValue;

    public static final int getRetryStrategyType(String type) {
        for (KvsRetryStrategyType strategyType : KvsRetryStrategyType.values()) {
            if (strategyType.name().equals(type)) {
                return strategyType.mValue;
            }
        }
        return UNKNOWN.intValue();
    }

    KvsRetryStrategyType(int value) {
        this.mValue = value;
    }

    public final int intValue() {
        return mValue;
    }
}
