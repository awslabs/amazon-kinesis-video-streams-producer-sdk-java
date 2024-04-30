package com.amazonaws.kinesisvideo.producer;

public enum KvsRetryStrategyType {  
    DISABLED(0), EXPONENTIAL_BACKOFF_WAIT(1);
    private final int kvsRetryStrategyTypeValue;

    private KvsRetryStrategyType(int kvsRetryStrategyTypeValue) {
        this.kvsRetryStrategyTypeValue = kvsRetryStrategyTypeValue;
    }

    public int getKvsRetryStrategyType() {
        return kvsRetryStrategyTypeValue;
    }
}
