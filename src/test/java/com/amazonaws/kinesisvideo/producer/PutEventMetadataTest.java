package com.amazonaws.kinesisvideo.producer;

import com.amazonaws.kinesisvideo.common.ProducerTestBase;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.internal.producer.StreamEventMetadata;
import com.amazonaws.kinesisvideo.internal.producer.StreamEventType;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.producer.Time;
import com.amazonaws.kinesisvideo.util.KinesisVideoStreamResource;
import com.amazonaws.kinesisvideo.util.LogCaptureRule;
import com.google.common.base.Strings;
import org.apache.logging.log4j.Level;
import org.junit.Before;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

import static com.amazonaws.kinesisvideo.internal.producer.jni.NativeKinesisVideoProducerJni.PRODUCER_NATIVE_LIBRARY_NAME;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class PutEventMetadataTest extends ProducerTestBase {
    
    private static final org.apache.logging.log4j.Logger log = org.apache.logging.log4j.LogManager.getLogger(PutEventMetadataTest.class);

    private KinesisVideoStreamResource.KinesisVideoStreamConfiguration streamConfiguration;
    private KinesisVideoStreamResource kinesisVideoStreamResource;
    private KinesisVideoProducerStream stream;
    private static final int ONE_SECOND_HUNDREDS_OF_NANOS = 1000 * 10000;
    private static final int TEN_SECONDS_HUNDREDS_OF_NANOS = 10 * ONE_SECOND_HUNDREDS_OF_NANOS;
    private static final int STORAGE_INFO_VERSION_ZERO = 0;
    private static final int DEBUG_LOG_LEVEL=1;
    private static final String SUCCESS_MESSAGE = "Successfully added event metadata";
    
    // Use constants from StreamEventMetadata
    private static final int EVENT_METADATA_KEY_MAX_LENGTH = StreamEventMetadata.MKV_MAX_TAG_NAME_LEN;
    private static final int EVENT_METADATA_VALUE_MAX_LENGTH = StreamEventMetadata.MKV_MAX_TAG_VALUE_LEN;
    
    // Error codes from native layer
    private static final long STATUS_INVALID_METADATA_NAME = 0x52000077L;
    private static final long STATUS_DUPLICATE_STREAM_EVENT_METADATA = 0x5200008BL;
    private static final long STATUS_INVALID_IMAGE_METADATA_KEY_LENGTH = 0x5200008EL;
    private static final long STATUS_INVALID_IMAGE_METADATA_VALUE_LENGTH = 0x5200008FL;
    
    private StreamEventMetadata createMetadata(String key, String value) {
        HashMap<String, String> metadataMap = new HashMap<>();
        metadataMap.put(key, value);
        return new StreamEventMetadata(metadataMap);
    }

    @SuppressWarnings({"UnnecessaryLocalVariable", "ExtractMethodRecommender"})
    @Before
    public void setUp() throws ProducerException {
        assumeTrue("JNI library not available, skipping test", isJNILoaded());
        assumeTrue("Log level must be DEBUG to capture success messages, skipping the PutEventMetadataTest", 
                log.isDebugEnabled());
        this.streamConfiguration = new KinesisVideoStreamResource.KinesisVideoStreamConfiguration();
        this.kinesisVideoStreamResource = new KinesisVideoStreamResource(this.streamConfiguration);
        createProducer();

        this.stream = createTestStream(streamConfiguration.streamName,
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME,
                TEST_LATENCY, TEST_BUFFER_DURATION);
    }

    @Rule
    public LogCaptureRule logCapture = new LogCaptureRule();

    @Test
    public void given_invalidAwsPrefix_when_putEventMetadata_then_shouldThrowException() throws ProducerException {
        sendKeyFrame(0, System.currentTimeMillis());

        StreamEventMetadata metadata = createMetadata("AWSInvalidName", "value1");
        
        try {
            stream.putEventMetadata(StreamEventType.STREAM_EVENT_TYPE_IMAGE_GENERATION.getIntType(), metadata);
            fail("Expected ProducerException for invalid metadata name starting with AWS");
        } catch (ProducerException e) {
            // Verify exact error code for AWS prefix validation
            assertEquals("Expected STATUS_INVALID_METADATA_NAME for AWS prefix, got: 0x" + 
                Long.toHexString(e.getStatusCode()), STATUS_INVALID_METADATA_NAME, e.getStatusCode());
        }
    }
    
    @Test
    public void given_nameTooLong_when_putEventMetadata_then_shouldThrowException() throws ProducerException {
        sendKeyFrame(1, System.currentTimeMillis());

        String longName = Strings.repeat("a", EVENT_METADATA_KEY_MAX_LENGTH + 1);
        StreamEventMetadata metadata = createMetadata(longName, "value1");
        
        try {
            stream.putEventMetadata(StreamEventType.STREAM_EVENT_TYPE_IMAGE_GENERATION.getIntType(), metadata);
            fail("Expected ProducerException for name too long");
        } catch (ProducerException e) {
            // Verify exact error code for name length validation
            assertEquals("Expected STATUS_INVALID_IMAGE_METADATA_KEY_LENGTH for name too long, got: 0x" + 
                Long.toHexString(e.getStatusCode()), STATUS_INVALID_IMAGE_METADATA_KEY_LENGTH, e.getStatusCode());
        }
    }
    
    @Test
    public void given_valueTooLong_when_putEventMetadata_then_shouldThrowException() throws ProducerException {
        sendKeyFrame(2, System.currentTimeMillis());

        String longValue = Strings.repeat("a", EVENT_METADATA_VALUE_MAX_LENGTH + 1);
        StreamEventMetadata metadata = createMetadata("validName", longValue);
        
        try {
            stream.putEventMetadata(StreamEventType.STREAM_EVENT_TYPE_IMAGE_GENERATION.getIntType(), metadata);
            fail("Expected ProducerException for value too long");
        } catch (ProducerException e) {
            // Verify exact error code for value length validation
            assertEquals("Expected STATUS_INVALID_IMAGE_METADATA_VALUE_LENGTH for value too long, got: 0x" + 
                Long.toHexString(e.getStatusCode()), STATUS_INVALID_IMAGE_METADATA_VALUE_LENGTH, e.getStatusCode());
        }
    }
    


    @Test
    public void given_negativeEventType_when_putEventMetadata_then_shouldThrowException() throws ProducerException {
        StreamEventMetadata metadata = createMetadata("", "");
        
        try {
            stream.putEventMetadata(-1, metadata);
            fail("Expected IllegalArgumentException for negative event type");
        } catch (IllegalArgumentException e) {
            // Verify the exact error message from Preconditions.checkArgument
            assertTrue("Exception message should contain 'Event type cannot be negative', got: " + e.getMessage(),
                e.getMessage().contains("Event type cannot be negative"));
        }
    }
    
    @Test
    public void given_eventTypeNone_when_putEventMetadata_then_shouldThrowException() throws ProducerException {
        sendKeyFrame(4, System.currentTimeMillis());

        StreamEventMetadata metadata = createMetadata("validKey", "validValue");
        
        try {
            stream.putEventMetadata(StreamEventType.STREAM_EVENT_TYPE_NONE.getIntType(), metadata);
            fail("Expected ProducerException for STREAM_EVENT_TYPE_NONE");
        } catch (ProducerException e) {
            assertTrue("Expected error for STREAM_EVENT_TYPE_NONE", e.getStatusCode() != 0);
        }
    }
    
    @Test
    public void given_eventTypeLast_when_putEventMetadata_then_shouldThrowException() throws ProducerException {
        sendKeyFrame(5, System.currentTimeMillis());

        StreamEventMetadata metadata = createMetadata("validKey", "validValue");
        
        try {
            stream.putEventMetadata(StreamEventType.STREAM_EVENT_TYPE_LAST.getIntType(), metadata);
            fail("Expected ProducerException for STREAM_EVENT_TYPE_LAST");
        } catch (ProducerException e) {
            assertTrue("Expected error for STREAM_EVENT_TYPE_LAST", e.getStatusCode() != 0);
        }
    }
    
    @Test
    public void given_metadataAfterNonKeyFrame_when_putEventMetadata_then_shouldThrowException() throws ProducerException {
        sendNonKeyFrame(8, System.currentTimeMillis());
        
        StreamEventMetadata metadata = createMetadata("camera_id", "camera_1");
        
        try {
            stream.putEventMetadata(StreamEventType.STREAM_EVENT_TYPE_IMAGE_GENERATION.getIntType(), metadata);
            fail("Expected ProducerException for metadata after non-key frame");
        } catch (ProducerException e) {
            assertTrue("Expected non-zero error code for metadata after non-key frame, got: 0x" + 
                Long.toHexString(e.getStatusCode()), e.getStatusCode() != 0);
        }
    }
    
    @Test
    public void given_validMetadata_when_putEventMetadata_then_shouldSucceed() throws ProducerException {
        sendKeyFrame(6, System.currentTimeMillis());
        StreamEventMetadata metadata = createMetadata("camera_id", "camera_1");

        try {
            stream.putEventMetadata(StreamEventType.STREAM_EVENT_TYPE_IMAGE_GENERATION.getIntType(), metadata);
            log.info("SUCCESS: Simple metadata accepted!");
        } catch (Exception e) {
            log.error("FAILED with simple metadata: " + e);
            if (e instanceof ProducerException) {
                ProducerException pe = (ProducerException) e;
                log.error("Status code: 0x" + Long.toHexString(pe.getStatusCode()));
            }
        }

        List<String> errorMessages = logCapture.getLogMessagesAtLevel(Level.DEBUG);
        assertTrue("FAILED to add metadata correctly",
                errorMessages.stream().anyMatch(msg -> msg.contains(SUCCESS_MESSAGE)));
    }
    
    @Test
    public void given_nullMetadata_when_putEventMetadata_then_shouldSucceed() throws ProducerException {
        sendKeyFrame(7, System.currentTimeMillis());
        
        // This should not throw an exception - null metadata is allowed
        stream.putEventMetadata(StreamEventType.STREAM_EVENT_TYPE_IMAGE_GENERATION.getIntType(), null);
        List<String> errorMessages = logCapture.getLogMessagesAtLevel(Level.DEBUG);
        assertTrue("FAILED to add metadata correctly",
                errorMessages.stream().anyMatch(msg -> msg.contains(SUCCESS_MESSAGE)));
    }
    
    @Test
    public void given_multipleEventTypes_when_putEventMetadata_then_shouldThrowException() throws ProducerException {
        sendKeyFrame(13, System.currentTimeMillis());
        
        StreamEventMetadata metadata = createMetadata("key1", "value1");
        
        // Try to use bitwise OR of multiple event types
        int combinedEventType = StreamEventType.STREAM_EVENT_TYPE_IMAGE_GENERATION.getIntType() | 
                               StreamEventType.STREAM_EVENT_TYPE_LAST.getIntType();
        
        try {
            stream.putEventMetadata(combinedEventType, metadata);
            fail("Expected ProducerException for multiple event types");
        } catch (ProducerException e) {
            assertTrue("Expected non-zero error code for multiple event types, got: 0x" + 
                Long.toHexString(e.getStatusCode()), e.getStatusCode() != 0);
        }
    }
    
    @Test
    public void given_duplicateMetadataSameEventType_when_putEventMetadata_then_shouldThrowException() throws ProducerException {
        sendKeyFrame(14, System.currentTimeMillis());
        
        StreamEventMetadata metadata1 = createMetadata("key1", "value1");
        StreamEventMetadata metadata2 = createMetadata("key1", "value1");
        
        // First metadata should succeed
        stream.putEventMetadata(StreamEventType.STREAM_EVENT_TYPE_IMAGE_GENERATION.getIntType(), metadata1);
        
        // Second metadata with same event type should fail
        try {
            stream.putEventMetadata(StreamEventType.STREAM_EVENT_TYPE_IMAGE_GENERATION.getIntType(), metadata2);
            fail("Expected ProducerException for duplicate metadata with same event type");
        } catch (ProducerException e) {
            // Verify exact error code for duplicate event metadata
            assertEquals("Expected STATUS_DUPLICATE_STREAM_EVENT_METADATA for duplicate metadata, got: 0x" + 
                Long.toHexString(e.getStatusCode()), STATUS_DUPLICATE_STREAM_EVENT_METADATA, e.getStatusCode());
        }
    }
    
    @Test
    public void given_duplicateMetadataDifferentEventType_when_putEventMetadata_then_shouldSucceed() throws ProducerException {
        sendKeyFrame(15, System.currentTimeMillis());
        
        StreamEventMetadata metadata1 = createMetadata("key1", "value1");
        StreamEventMetadata metadata2 = createMetadata("key1", "value1");
        
        // Both should succeed as they have different event types
        try {
            stream.putEventMetadata(StreamEventType.STREAM_EVENT_TYPE_IMAGE_GENERATION.getIntType(), metadata1);
            stream.putEventMetadata(StreamEventType.STREAM_EVENT_TYPE_NOTIFICATION.getIntType(), metadata2);
        } catch (ProducerException e) {
            fail("Expected no exception for different event types, got: " + e.getMessage() + 
                " with status code: 0x" + Long.toHexString(e.getStatusCode()));
        }
        
        List<String> errorMessages = logCapture.getLogMessagesAtLevel(Level.DEBUG);
        long successCount = errorMessages.stream().filter(msg -> msg.contains(SUCCESS_MESSAGE)).count();
        assertEquals("Expected 2 successful metadata additions", 2, successCount);
    }
    

    
    @Test
    public void given_hashMapWithNullValue_when_createStreamEventMetadata_then_shouldThrowIllegalArgumentException() {
        // Test Java layer validation - should throw IllegalArgumentException before reaching JNI
        HashMap<String, String> metadataMap = new HashMap<>();
        metadataMap.put("key1", "value1");
        metadataMap.put("key2", null);
        
        assertThrows(IllegalArgumentException.class, () -> {
            new StreamEventMetadata(metadataMap);
        });
    }
    

    
    @Test
    public void given_multipleFragments_when_putEventMetadataOnEach_then_shouldSucceed() throws ProducerException {
        int frameIndex = 0;
        long baseTimeMs = System.currentTimeMillis();
        
        // Send 5 fragments, each with: 1 key frame + metadata + 4 non-key frames
        for (int fragment = 0; fragment < 5; fragment++) {
            // Send key frame to start new fragment
            sendKeyFrame(frameIndex++, baseTimeMs + fragment * 1000);
            
            // Send metadata for this fragment
            HashMap<String, String> metadataMap = new HashMap<>();
            metadataMap.put("fragment_id", "fragment_" + fragment);
            metadataMap.put("camera_angle", "angle_" + (fragment * 45));
            StreamEventMetadata metadata = new StreamEventMetadata(metadataMap);
            stream.putEventMetadata(StreamEventType.STREAM_EVENT_TYPE_IMAGE_GENERATION.getIntType(), metadata);
            
            // Send 4 non-key frames
            for (int nonKeyFrame = 0; nonKeyFrame < 4; nonKeyFrame++) {
                sendNonKeyFrame(frameIndex++, baseTimeMs + fragment * 1000 + (nonKeyFrame + 1) * 100);
            }
        }
        log.info("SUCCESS: Sent 5 fragments with metadata and frames");
        
        List<String> errorMessages = logCapture.getLogMessagesAtLevel(Level.DEBUG);
        long successCount = errorMessages.stream().filter(msg -> msg.contains(SUCCESS_MESSAGE)).count();
        assertEquals("Expected 5 successful metadata additions", 5, successCount);
    }
    
    private void sendKeyFrame(int frameIndex, long timeMs) throws ProducerException {
        byte[] frameData = ("key frame " + frameIndex).getBytes(StandardCharsets.UTF_8);
        long timestampUs = timeMs * 1000;
        
        KinesisVideoFrame frame = new KinesisVideoFrame(
            frameIndex,
            FRAME_FLAG_KEY_FRAME,
            timestampUs * Time.HUNDREDS_OF_NANOS_IN_A_MICROSECOND,
            timestampUs * Time.HUNDREDS_OF_NANOS_IN_A_MICROSECOND,
            frameDuration_,
            ByteBuffer.wrap(frameData)
        );
        
        stream.putFrame(frame);
    }
    
    private void sendNonKeyFrame(int frameIndex, long timeMs) throws ProducerException {
        byte[] frameData = ("non-key frame " + frameIndex).getBytes(StandardCharsets.UTF_8);
        long timestampUs = timeMs * 1000;
        
        KinesisVideoFrame frame = new KinesisVideoFrame(
            frameIndex,
            FRAME_FLAG_NONE, // non-key frame
            timestampUs * Time.HUNDREDS_OF_NANOS_IN_A_MICROSECOND,
            timestampUs * Time.HUNDREDS_OF_NANOS_IN_A_MICROSECOND,
            frameDuration_,
            ByteBuffer.wrap(frameData)
        );
        
        stream.putFrame(frame);
    }
    @Test
    public void given_hashMapModifiedAfterConstruction_when_putEventMetadata_then_shouldNotAffectMetadata() throws ProducerException {
        sendKeyFrame(16, System.currentTimeMillis());
        
        HashMap<String, String> metadataMap = new HashMap<>();
        metadataMap.put("original_key", "original_value");
        metadataMap.put("original_key2", "original_value2");
        StreamEventMetadata metadata = new StreamEventMetadata(metadataMap);
        
        // Modify the original HashMap after creating StreamEventMetadata with invalid values
        // If defensive copy wasn't made, these would cause putEventMetadata to fail
        metadataMap.put("AWSInvalidPrefix", "value"); // Would fail with STATUS_INVALID_METADATA_NAME
        metadataMap.put(Strings.repeat("a", EVENT_METADATA_KEY_MAX_LENGTH + 1), "value"); // Would fail with STATUS_INVALID_IMAGE_METADATA_KEY_LENGTH
        
        // The metadata should still work with original values (defensive copy)
        // If the modified invalid values were used, this would throw ProducerException
        try {
            stream.putEventMetadata(StreamEventType.STREAM_EVENT_TYPE_IMAGE_GENERATION.getIntType(), metadata);
        } catch (ProducerException e) {
            fail("putEventMetadata should succeed with original values, but failed with: " + e.getMessage() +
                " (status code: 0x" + Long.toHexString(e.getStatusCode()) + "). This indicates defensive copy was not made.");
        }
        
        List<String> errorMessages = logCapture.getLogMessagesAtLevel(Level.DEBUG);
        assertTrue("Metadata should work despite HashMap modification",
                errorMessages.stream().anyMatch(msg -> msg.contains(SUCCESS_MESSAGE)));
    }

    @After
    public void tearDown() {
        // Clean up stream first
        if (this.stream != null) {
            try {
                this.stream.stopStreamSync();
            } catch (Exception e) {
                log.warn("Failed to stop stream", e);
            }
        }
        
        // Clean up stream resource
        if (this.kinesisVideoStreamResource != null) {
            try {
                this.kinesisVideoStreamResource.close();
            } catch (Exception e) {
                log.warn("Failed to close stream resource", e);
            }
        }
        
        // Clean up producer
        try {
            free();
        } catch (Exception e) {
            log.error("Failed to free producer resources", e);
        }
    }
}