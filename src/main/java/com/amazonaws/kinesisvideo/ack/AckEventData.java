package com.amazonaws.kinesisvideo.ack;

public class AckEventData {
    private String type;
    private int errorCode;
    private long fragmentTimecode;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public long getFragmentTimecode() {
        return fragmentTimecode;
    }

    public void setFragmentTimecode(long fragmentTimecode) {
        this.fragmentTimecode = fragmentTimecode;
    }
}
