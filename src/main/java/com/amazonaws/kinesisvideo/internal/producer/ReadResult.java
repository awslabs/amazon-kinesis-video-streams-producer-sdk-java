package com.amazonaws.kinesisvideo.internal.producer;

/**
 * Class to hold the read results from the native code call.
 * The class has a setter method that will be called from the native method to avoid object creation.
 */
public class ReadResult {
    /**
     * Invalid upload handle value which is specified in the native codebase.
     */
    public static final long INVALID_UPLOAD_HANDLE_VALUE = -1;

    private long uploadHandle = INVALID_UPLOAD_HANDLE_VALUE;
    private int readBytes = 0;
    private boolean isEndOfStream = false;

    /**
     * Setter method which is called from the native codebase.
     * @param uploadHandle Upload handle
     * @param readBytes Read bytes
     * @param isEndOfStream Whether its the end of stream
     */
    public void setReadResult(final long uploadHandle, final int readBytes, final boolean isEndOfStream) {
        this.uploadHandle = uploadHandle;
        this.readBytes = readBytes;
        this.isEndOfStream = isEndOfStream;
    }

    public long getUploadHandle() {
        return uploadHandle;
    }

    public int getReadBytes() {
        return readBytes;
    }

    public boolean isEndOfStream() {
        return isEndOfStream;
    }
}
