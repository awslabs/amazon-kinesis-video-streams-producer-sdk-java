package com.amazonaws.kinesisvideo.producer;

enum MkvTrackInfoType {
    VIDEO(0), AUDIO(1), UNKNOWN(-1);
    private final int mValue;

    public static final int getStatusCode(String status) {
        for (MkvTrackInfoType trackInfoType : MkvTrackInfoType.values()) {
            if (trackInfoType.name().equals(status)) {
                return trackInfoType.mValue;
            }
        }
        return UNKNOWN.intValue();
    }

    MkvTrackInfoType(int value) {
        this.mValue = value;
    }

    public final int intValue() {
        return mValue;
    }
}
