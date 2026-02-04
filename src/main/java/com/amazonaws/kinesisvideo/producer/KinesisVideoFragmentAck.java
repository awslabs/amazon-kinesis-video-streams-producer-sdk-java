package com.amazonaws.kinesisvideo.producer;

import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.util.CalledByNativeCode;
import com.amazonaws.kinesisvideo.util.StreamInfoConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Represents an acknowledgement received from Amazon Kinesis Video Streams for a fragment.
 * <p>
 * This class encapsulates the acknowledgement information sent back by the Kinesis Video Streams
 * service when fragments are processed. Acknowledgements provide feedback about the status of
 * fragments as they move through different stages of processing in the service pipeline.
 * </p>
 * <p>
 * Fragment acknowledgements can indicate various states such as:
 * <ul>
 *   <li><strong>BUFFERING</strong> - Fragment started buffering on the ingestion host</li>
 *   <li><strong>RECEIVED</strong> - Fragment has been received and parsed</li>
 *   <li><strong>PERSISTED</strong> - Fragment has been persisted to storage</li>
 *   <li><strong>ERROR</strong> - Fragment sent for ingestion encountered an error, check the error code reference
 *     in the PutMedia API documentation</li>
 *   <li><strong>IDLE</strong> - Keep-alive acknowledgement to maintain connection</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Thread Safety:</strong> This class is immutable and thread-safe.
 * </p>
 * <p>
 * <strong>Note:</strong> This class structure must match the Frame declaration in native code
 * located in /client/Include.h to ensure proper JNI interoperability.
 * </p>
 *
 * @see FragmentAckType
 * @see <a href="https://docs.aws.amazon.com/kinesisvideostreams/latest/dg/API_dataplane_PutMedia.html">PutMedia API Documentation</a>
 * @see <a href="https://docs.aws.amazon.com/kinesisvideostreams/latest/dg/producer-sdk.html">Producer SDK Documentation</a>
 */
@Immutable
@ThreadSafe
public class KinesisVideoFragmentAck {

    private static final Logger log = LogManager.getLogger(KinesisVideoFragmentAck.class);

    /**
     * The current version of the fragment acknowledgement struct in the native layer.
     */
    private final static int FRAGMENT_ACK_CURRENT_VERSION = 0;

    /**
     * The type of acknowledgement received from the service.
     * <p>
     * Indicates the current processing state of the fragment within the
     * Kinesis Video Streams service pipeline.
     * </p>
     *
     * @see FragmentAckType
     */
    private final FragmentAckType ackType;

    /**
     * The timestamp of the fragment in milliseconds.
     * <p>
     * This timestamp corresponds to the presentation timestamp (PTS) of the fragment
     * and is used to correlate acknowledgements with specific fragments in the stream.
     * </p>
     *
     * @see <a href="https://docs.aws.amazon.com/kinesisvideostreams/latest/dg/API_dataplane_PutMedia.html">PutMedia API Documentation</a>
     */
    private final long timestamp;

    /**
     * Fragment number. The unique sequence number identifying the fragment.
     * <p>
     * This sequence number is assigned by the Kinesis Video Streams service
     * and uniquely identifies each fragment within a stream.
     * </p>
     *
     * @see <a href="https://docs.aws.amazon.com/kinesisvideostreams/latest/dg/API_dataplane_PutMedia.html">PutMedia API Documentation</a>
     */
    private final String sequenceNumber;

    /**
     * The service call result code for the acknowledgement.
     * <p>
     * For successful operations, this will typically be HTTP 200 (OK).
     * For error conditions, this will contain the appropriate service-specific error code indicating the nature of the failure.
     * </p>
     *
     * @see com.amazonaws.kinesisvideo.util.StreamInfoConstants#HTTP_OK
     * @see <a href="https://docs.aws.amazon.com/kinesisvideostreams/latest/dg/API_dataplane_PutMedia.html">PutMedia API Documentation</a>
     */
    private final int result;

    /**
     * Constructs a new KinesisVideoFragmentAck with the specified parameters.
     * <p>
     * This constructor accepts an integer acknowledgement type and automatically
     * wraps it in a {@link FragmentAckType} object.
     * </p>
     *
     * @see KinesisVideoFragmentAck#KinesisVideoFragmentAck(FragmentAckType, long, String, int)
     */
    @CalledByNativeCode
    public KinesisVideoFragmentAck(final int ackType,
                                   final long timestamp,
                                   @Nonnull final String sequenceNumber,
                                   final int result) {
        this(new FragmentAckType(ackType), timestamp, sequenceNumber, result);
    }

    /**
     * Constructs a new KinesisVideoFragmentAck with the specified parameters.
     * <p>
     * This is the primary constructor that accepts a {@link FragmentAckType} object
     * directly, providing type safety and better API design. This constructor performs
     * runtime validation of all non-null parameters.
     * </p>
     *
     * @param ackType        the acknowledgement type
     * @param timestamp      the fragment timecode with milliseconds precision, which may be relative or absolute
     *                       depending on the configuration of the stream
     * @param sequenceNumber the unique sequence number for the fragment
     * @param result         the service call result code (HTTP status or error code)
     * @throws IllegalArgumentException if ackType or sequenceNumber is null
     * @see FragmentAckType
     */
    public KinesisVideoFragmentAck(@Nonnull final FragmentAckType ackType,
                                   final long timestamp,
                                   @Nonnull final String sequenceNumber,
                                   final int result) {

        Preconditions.checkArgument(ackType != null, "ackType cannot be null");
        Preconditions.checkArgument(sequenceNumber != null, "sequenceNumber cannot be null");

        // Some error acks don't come with a sequence number (e.g. INVALID_MKV_DATA)
        if (sequenceNumber.isEmpty()) {
            log.warn("Received empty sequence number! AckType: {}, Timestamp: {}, Result: {}",
                    ackType, timestamp, result);
        }

        this.ackType = ackType;
        this.timestamp = timestamp;
        this.sequenceNumber = sequenceNumber;
        this.result = result;
    }

    /**
     * Returns the version of the fragment acknowledgement format.
     * <p>
     * This version number helps maintain compatibility between different versions
     * of this struct in the native layer. The current version is 0.
     * </p>
     *
     * @return the current version number
     */
    @CalledByNativeCode
    public int getVersion() {
        return FRAGMENT_ACK_CURRENT_VERSION;
    }

    /**
     * Returns the acknowledgement type indicating the fragment's processing state.
     * <p>
     * The acknowledgement type provides information about where the fragment
     * is in the service processing pipeline, such as whether it's being buffered,
     * has been received, persisted, or encountered an error.
     * </p>
     *
     * @return the acknowledgement type
     * @see FragmentAckType
     */
    @Nonnull
    @CalledByNativeCode
    public FragmentAckType getAckType() {
        return this.ackType;
    }

    /**
     * Returns the {@code FragmentTimecode} of this fragment.
     * The timecode may be relative, or absolute, depending on the parameters used.
     * Scale is in milliseconds.
     *
     * @see <a href="https://docs.aws.amazon.com/kinesisvideostreams/latest/dg/API_dataplane_PutMedia.html">PutMedia API</a>
     * @see StreamInfoConstants#ABSOLUTE_TIMECODES
     * @see StreamInfoConstants#RELATIVE_TIMECODES
     */
    @CalledByNativeCode
    public long getTimestamp() {
        return this.timestamp;
    }

    /**
     * Returns the unique sequence number identifying the fragment.
     * <p>
     * This sequence number is assigned by the Kinesis Video Streams service
     * and uniquely identifies each fragment within a stream. It can be used
     * to track fragment processing and correlate acknowledgements with
     * the original fragments sent to the service.
     * </p>
     *
     * @return the fragment sequence number (never null)
     */
    @Nonnull
    @CalledByNativeCode
    public String getSequenceNumber() {
        return this.sequenceNumber;
    }

    /**
     * Returns the service call result code for the acknowledgement.
     * <p>
     * This result code indicates the outcome of the fragment:
     * <ul>
     *   <li>HTTP 200 (OK) - Successful processing (Buffering, Received, Persisted)</li>
     *   <li>HTTP 40xx - Client errors (e.g., malformed fragment)</li>
     *   <li>HTTP 45xx - Client errors related to KMS</li>
     *   <li>HTTP 50xx - Server errors (e.g., service unavailable)</li>
     * </ul>
     * </p>
     * <p>
     * If the code is not {@value com.amazonaws.kinesisvideo.util.StreamInfoConstants#HTTP_OK}, then this acknowledgement type is {@link FragmentAckType#FRAGMENT_ACK_TYPE_ERROR}.
     * </p>
     *
     * @return the service call result code
     * @see com.amazonaws.kinesisvideo.util.StreamInfoConstants#HTTP_OK
     */
    @CalledByNativeCode
    public int getResult() {
        return this.result;
    }

    /**
     * Returns a JSON-formatted string representation of the fragment acknowledgement.
     * <p>
     * The returned string follows the format used by the Kinesis Video Streams PutMedia API
     * for acknowledgement messages. The format includes:
     * <ul>
     *   <li><strong>EventType</strong> - The acknowledgement type (BUFFERING, RECEIVED, etc.)</li>
     *   <li><strong>FragmentTimecode</strong> - The fragment timestamp</li>
     *   <li><strong>FragmentNumber</strong> - The unique sequence number</li>
     *   <li><strong>ErrorId</strong> - The error code (only included for non-successful results)</li>
     * </ul>
     * </p>
     * <p>
     * <strong>Example output for successful acknowledgement:</strong>
     * <pre>
     * {
     *     Acknowledgement: {
     *         "EventType": "PERSISTED"
     *         "FragmentTimecode": 1234567890,
     *         "FragmentNumber": "12345678901234567890"
     *     }
     * }
     * </pre>
     * </p>
     * <p>
     * <strong>Example output for error acknowledgement:</strong>
     * <pre>
     * {
     *     Acknowledgement: {
     *         "EventType": "ERROR"
     *         "FragmentTimecode": 1234567890,
     *         "FragmentNumber": "12345678901234567890",
     *         "ErrorId": 400
     *     }
     * }
     * </pre>
     * </p>
     *
     * @return a JSON-formatted string representation of the acknowledgement
     * @see <a href="https://docs.aws.amazon.com/kinesisvideostreams/latest/dg/API_dataplane_PutMedia.html">PutMedia API Documentation</a>
     */
    @Override
    public String toString() {
        String errorId = "";
        if (this.result != StreamInfoConstants.HTTP_OK) {
            errorId = ",\n\t\t\"ErrorId\": " + this.result;
        }

        return "{\n" +
                "\tAcknowledgement: {\n" +
                "\t\t\"EventType\": \"" + this.ackType + "\"\n" +
                "\t\t\"FragmentTimecode\": " + this.timestamp + ",\n" +
                "\t\t\"FragmentNumber\": \"" + this.sequenceNumber + "\"" +
                errorId +
                "\n\t}\n" +
                "}";
    }
}
