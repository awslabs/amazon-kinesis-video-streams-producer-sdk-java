package com.amazonaws.kinesisvideo.producer;

/**
 * Definition of the StreamStatus.
 *
 * NOTE: This enum must be the same as defined in /client/Include.h
 *
 */
public enum StreamStatus {
    /**
     * Stream is being created, active, updating, deleting, unknown
     */
    CREATING(0), ACTIVE(1), UPDATING(2), DELETING(3), UNKNOWN(-1);

    private final int mValue;

    public static final int getStatusCode(String status) {
        for (StreamStatus streamStatus : StreamStatus.values()) {
            if (streamStatus.name().equals(status)) {
                return streamStatus.mValue;
            }
        }
        return UNKNOWN.intValue();
    }

    StreamStatus(int value) {
        this.mValue = value;
    }

    public final int intValue() {
        return mValue;
    }
}