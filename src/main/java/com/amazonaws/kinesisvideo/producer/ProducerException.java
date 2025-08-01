package com.amazonaws.kinesisvideo.producer;

import javax.annotation.Nonnull;

import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.util.CalledByNativeCode;

/**
 * Producer exception class extending basic {@link Exception}.
 *
 *
 */
public class ProducerException extends KinesisVideoException {
    /**
     * Various common status code.
     *
     * For the complete list of errors please refer to the errors section
     * in the public Include.h files in the native codebase.
     */
    public static final int STATUS_SUCCESS = 0x00000000;
    public static final int STATUS_BASE = 0x00000000;
    public static final int STATUS_NULL_ARG = STATUS_BASE + 0x00000001;
    public static final int STATUS_INVALID_ARG = STATUS_BASE + 0x00000002;
    public static final int STATUS_INVALID_ARG_LEN = STATUS_BASE + 0x00000003;
    public static final int STATUS_OUT_OF_MEMORY = STATUS_BASE + 0x00000004;
    public static final int STATUS_BUFFER_TOO_SMALL = STATUS_BASE + 0x00000005;
    public static final int STATUS_UNEXPECTED_EOF = STATUS_BASE + 0x00000006;
    public static final int STATUS_FORMAT_ERROR = STATUS_BASE + 0x00000007;
    public static final int STATUS_INVALID_HANDLE_ERROR = STATUS_BASE + 0x00000008;
    public static final int STATUS_OPEN_FILE_FAILED = STATUS_BASE + 0x00000009;
    public static final int STATUS_READ_FILE_FAILED = STATUS_BASE + 0x0000000a;
    public static final int STATUS_WRITE_TO_FILE_FAILED = STATUS_BASE + 0x0000000b;
    public static final int STATUS_INTERNAL_ERROR = STATUS_BASE + 0x0000000c;
    public static final int STATUS_INVALID_OPERATION = STATUS_BASE + 0x0000000d;
    public static final int STATUS_NOT_IMPLEMENTED = STATUS_BASE + 0x0000000e;
    public static final int STATUS_OPERATION_TIMED_OUT = STATUS_BASE + 0x0000000f;

    public static final int STATUS_DESCRIBE_STREAM_CALL_FAILED = 0x52000011;
    public static final int STATUS_CREATE_STREAM_CALL_FAILED = 0x52000019;
    public static final int STATUS_GET_STREAMING_TOKEN_CALL_FAILED = 0x5200002a;
    public static final int STATUS_GET_STREAMING_ENDPOINT_CALL_FAILED = 0x5200002b;
    public static final int STATUS_PUT_STREAM_CALL_FAILED = 0x5200002d;
    public static final int STATUS_INVALID_TOKEN_EXPIRATION = 0x52000049;

    /**
     * Returns true if the status code is a retryable exception.
     *
     * @param ex exception containing the status code
     * @return true if the error is retryable
     *
     * @see <a href="">PIC's IS_RETRYABLE_ERROR in src/client/include/com/amazonaws/kinesis/video/client/Include.h</a>
     */
    @SuppressWarnings({"ConstantConditions"})
    public static boolean isRetryableError(@Nonnull final ProducerException ex) {
        Preconditions.checkArgument(ex != null, "The exception can't be null");
        return isRetryableError(ex.getStatusCode());
    }

    /**
     * Returns true if the status code is a retryable exception.
     *
     * @param statusCode status code to check
     * @return true if the status code is retryable
     *
     * @see <a href="">PIC's IS_RETRYABLE_ERROR in src/client/include/com/amazonaws/kinesis/video/client/Include.h</a>
     */
    public static boolean isRetryableError(final int statusCode) {
        return statusCode == STATUS_DESCRIBE_STREAM_CALL_FAILED ||
                statusCode == STATUS_CREATE_STREAM_CALL_FAILED ||
                statusCode == STATUS_GET_STREAMING_TOKEN_CALL_FAILED ||
                statusCode == STATUS_GET_STREAMING_ENDPOINT_CALL_FAILED ||
                statusCode == STATUS_PUT_STREAM_CALL_FAILED ||
                statusCode == STATUS_INVALID_TOKEN_EXPIRATION;
    }

    /**
     * Status code returned from native
     */
    private final int mStatusCode;

    /**
     * Static function to map {@link KinesisVideoException} to a status code
     * @param exception KinesisVideoException
     * @return status code
     */
    private static int statusCodeFromException(final @Nonnull Exception exception) {
        return STATUS_NOT_IMPLEMENTED;
    }

    public ProducerException(final @Nonnull Exception exception) {
        super(exception);
        mStatusCode = statusCodeFromException(exception);
    }

    @CalledByNativeCode
    public ProducerException(final @Nonnull String message, final int statusCode) {
        super(message + " StatusCode: 0x" + Integer.toHexString(statusCode));
        mStatusCode = statusCode;
    }

    public int getStatusCode() {
        return mStatusCode;
    }
}
