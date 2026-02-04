package com.amazonaws.kinesisvideo.producer;

import org.junit.Test;

import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.HTTP_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class KinesisVideoFragmentAckTest {

    private static final long TEST_TIMESTAMP = 1234567890L;
    private static final String TEST_SEQUENCE_NUMBER = "12345678901234567890";
    private static final int TEST_ERROR_RESULT = 400;

    @Test
    public void whenCreatingFragmentAckWithIntAckType_thenShouldInitializeCorrectly() {
        // Given
        int ackType = FragmentAckType.FRAGMENT_ACK_TYPE_BUFFERING;
        
        // When
        KinesisVideoFragmentAck fragmentAck = new KinesisVideoFragmentAck(
            ackType, TEST_TIMESTAMP, TEST_SEQUENCE_NUMBER, HTTP_OK);
        
        // Then
        assertEquals(FragmentAckType.FRAGMENT_ACK_TYPE_BUFFERING, fragmentAck.getAckType().getIntType());
        assertEquals(TEST_TIMESTAMP, fragmentAck.getTimestamp());
        assertEquals(TEST_SEQUENCE_NUMBER, fragmentAck.getSequenceNumber());
        assertEquals(HTTP_OK, fragmentAck.getResult());
    }

    @Test
    public void whenCreatingFragmentAckWithFragmentAckTypeObject_thenShouldInitializeCorrectly() {
        // Given
        FragmentAckType ackType = new FragmentAckType(FragmentAckType.FRAGMENT_ACK_TYPE_RECEIVED);
        
        // When
        KinesisVideoFragmentAck fragmentAck = new KinesisVideoFragmentAck(
            ackType, TEST_TIMESTAMP, TEST_SEQUENCE_NUMBER, HTTP_OK);
        
        // Then
        assertEquals(ackType, fragmentAck.getAckType());
        assertEquals(TEST_TIMESTAMP, fragmentAck.getTimestamp());
        assertEquals(TEST_SEQUENCE_NUMBER, fragmentAck.getSequenceNumber());
        assertEquals(HTTP_OK, fragmentAck.getResult());
    }

    @Test
    public void whenGettingVersion_thenShouldReturnCurrentVersion() {
        // Given
        KinesisVideoFragmentAck fragmentAck = new KinesisVideoFragmentAck(
            FragmentAckType.FRAGMENT_ACK_TYPE_PERSISTED, TEST_TIMESTAMP, TEST_SEQUENCE_NUMBER, HTTP_OK);
        
        // When
        int version = fragmentAck.getVersion();
        
        // Then
        assertEquals(0, version); // FRAGMENT_ACK_CURRENT_VERSION is 0
    }

    @Test
    public void whenGettingAckType_thenShouldReturnCorrectType() {
        // Given
        FragmentAckType expectedType = new FragmentAckType(FragmentAckType.FRAGMENT_ACK_TYPE_ERROR);
        KinesisVideoFragmentAck fragmentAck = new KinesisVideoFragmentAck(
            expectedType, TEST_TIMESTAMP, TEST_SEQUENCE_NUMBER, TEST_ERROR_RESULT);
        
        // When
        FragmentAckType actualType = fragmentAck.getAckType();
        
        // Then
        assertEquals(expectedType, actualType);
        assertEquals(FragmentAckType.FRAGMENT_ACK_TYPE_ERROR, actualType.getIntType());
    }

    @Test
    public void whenGettingTimestamp_thenShouldReturnCorrectValue() {
        // Given
        long expectedTimestamp = 9876543210L;
        KinesisVideoFragmentAck fragmentAck = new KinesisVideoFragmentAck(
            FragmentAckType.FRAGMENT_ACK_TYPE_IDLE, expectedTimestamp, TEST_SEQUENCE_NUMBER, HTTP_OK);
        
        // When
        long actualTimestamp = fragmentAck.getTimestamp();
        
        // Then
        assertEquals(expectedTimestamp, actualTimestamp);
    }

    @Test
    public void whenGettingSequenceNumber_thenShouldReturnCorrectValue() {
        // Given
        String expectedSequenceNumber = "98765432109876543210";
        KinesisVideoFragmentAck fragmentAck = new KinesisVideoFragmentAck(
            FragmentAckType.FRAGMENT_ACK_TYPE_BUFFERING, TEST_TIMESTAMP, expectedSequenceNumber, HTTP_OK);
        
        // When
        String actualSequenceNumber = fragmentAck.getSequenceNumber();
        
        // Then
        assertEquals(expectedSequenceNumber, actualSequenceNumber);
    }

    @Test
    public void whenGettingResult_thenShouldReturnCorrectValue() {
        // Given
        int expectedResult = 500;
        KinesisVideoFragmentAck fragmentAck = new KinesisVideoFragmentAck(
            FragmentAckType.FRAGMENT_ACK_TYPE_ERROR, TEST_TIMESTAMP, TEST_SEQUENCE_NUMBER, expectedResult);
        
        // When
        int actualResult = fragmentAck.getResult();
        
        // Then
        assertEquals(expectedResult, actualResult);
    }

    @Test
    public void whenToStringWithSuccessResult_thenShouldNotIncludeErrorId() {
        // Given
        KinesisVideoFragmentAck fragmentAck = new KinesisVideoFragmentAck(
            FragmentAckType.FRAGMENT_ACK_TYPE_PERSISTED, TEST_TIMESTAMP, TEST_SEQUENCE_NUMBER, HTTP_OK);
        
        // When
        String result = fragmentAck.toString();
        
        // Then
        assertNotNull(result);
        assertTrue(result.contains("\"EventType\": \"PERSISTED\""));
        assertTrue(result.contains("\"FragmentTimecode\": " + TEST_TIMESTAMP));
        assertTrue(result.contains("\"FragmentNumber\": \"" + TEST_SEQUENCE_NUMBER + "\""));
        assertTrue(!result.contains("ErrorId")); // Should not contain ErrorId for HTTP_OK
    }

    @Test
    public void whenToStringWithErrorResult_thenShouldIncludeErrorId() {
        // Given
        KinesisVideoFragmentAck fragmentAck = new KinesisVideoFragmentAck(
            FragmentAckType.FRAGMENT_ACK_TYPE_ERROR, TEST_TIMESTAMP, TEST_SEQUENCE_NUMBER, TEST_ERROR_RESULT);
        
        // When
        String result = fragmentAck.toString();
        
        // Then
        assertNotNull(result);
        assertTrue(result.contains("\"EventType\": \"ERROR\""));
        assertTrue(result.contains("\"FragmentTimecode\": " + TEST_TIMESTAMP));
        assertTrue(result.contains("\"FragmentNumber\": \"" + TEST_SEQUENCE_NUMBER + "\""));
        assertTrue(result.contains("\"ErrorId\": " + TEST_ERROR_RESULT));
    }

    @Test
    public void whenToStringWithBufferingType_thenShouldShowCorrectEventType() {
        // Given
        KinesisVideoFragmentAck fragmentAck = new KinesisVideoFragmentAck(
            FragmentAckType.FRAGMENT_ACK_TYPE_BUFFERING, TEST_TIMESTAMP, TEST_SEQUENCE_NUMBER, HTTP_OK);
        
        // When
        String result = fragmentAck.toString();
        
        // Then
        assertTrue(result.contains("\"EventType\": \"BUFFERING\""));
    }

    @Test
    public void whenToStringWithReceivedType_thenShouldShowCorrectEventType() {
        // Given
        KinesisVideoFragmentAck fragmentAck = new KinesisVideoFragmentAck(
            FragmentAckType.FRAGMENT_ACK_TYPE_RECEIVED, TEST_TIMESTAMP, TEST_SEQUENCE_NUMBER, HTTP_OK);
        
        // When
        String result = fragmentAck.toString();
        
        // Then
        assertTrue(result.contains("\"EventType\": \"RECEIVED\""));
    }

    @Test
    public void whenToStringWithPersistedType_thenShouldShowCorrectEventType() {
        // Given
        KinesisVideoFragmentAck fragmentAck = new KinesisVideoFragmentAck(
            FragmentAckType.FRAGMENT_ACK_TYPE_PERSISTED, TEST_TIMESTAMP, TEST_SEQUENCE_NUMBER, HTTP_OK);
        
        // When
        String result = fragmentAck.toString();
        
        // Then
        assertTrue(result.contains("\"EventType\": \"PERSISTED\""));
    }

    @Test
    public void whenToStringWithIdleType_thenShouldShowCorrectEventType() {
        // Given
        KinesisVideoFragmentAck fragmentAck = new KinesisVideoFragmentAck(
            FragmentAckType.FRAGMENT_ACK_TYPE_IDLE, TEST_TIMESTAMP, TEST_SEQUENCE_NUMBER, HTTP_OK);
        
        // When
        String result = fragmentAck.toString();
        
        // Then
        assertTrue(result.contains("\"EventType\": \"IDLE\""));
    }

    @Test
    public void whenToStringWithUndefinedType_thenShouldShowCorrectEventType() {
        // Given
        KinesisVideoFragmentAck fragmentAck = new KinesisVideoFragmentAck(
            FragmentAckType.FRAGMENT_ACK_TYPE_UNDEFINED, TEST_TIMESTAMP, TEST_SEQUENCE_NUMBER, HTTP_OK);
        
        // When
        String result = fragmentAck.toString();
        
        // Then
        assertTrue(result.contains("\"EventType\": \"UNDEFINED\""));
    }

    @Test
    public void whenToStringWithZeroTimestamp_thenShouldHandleCorrectly() {
        // Given
        KinesisVideoFragmentAck fragmentAck = new KinesisVideoFragmentAck(
            FragmentAckType.FRAGMENT_ACK_TYPE_BUFFERING, 0L, TEST_SEQUENCE_NUMBER, HTTP_OK);
        
        // When
        String result = fragmentAck.toString();
        
        // Then
        assertTrue(result.contains("\"FragmentTimecode\": 0"));
    }

    @Test
    public void whenToStringWithLargeTimestamp_thenShouldHandleCorrectly() {
        // Given
        long largeTimestamp = Long.MAX_VALUE;
        KinesisVideoFragmentAck fragmentAck = new KinesisVideoFragmentAck(
            FragmentAckType.FRAGMENT_ACK_TYPE_PERSISTED, largeTimestamp, TEST_SEQUENCE_NUMBER, HTTP_OK);
        
        // When
        String result = fragmentAck.toString();
        
        // Then
        assertTrue(result.contains("\"FragmentTimecode\": " + largeTimestamp));
    }

    @Test
    public void whenToStringWithNegativeResult_thenShouldIncludeErrorId() {
        // Given
        int negativeResult = -1;
        KinesisVideoFragmentAck fragmentAck = new KinesisVideoFragmentAck(
            FragmentAckType.FRAGMENT_ACK_TYPE_ERROR, TEST_TIMESTAMP, TEST_SEQUENCE_NUMBER, negativeResult);
        
        // When
        String result = fragmentAck.toString();
        
        // Then
        assertTrue(result.contains("\"ErrorId\": " + negativeResult));
    }

    @Test
    public void whenToStringWithZeroResult_thenShouldIncludeErrorId() {
        // Given
        int zeroResult = 0;
        KinesisVideoFragmentAck fragmentAck = new KinesisVideoFragmentAck(
            FragmentAckType.FRAGMENT_ACK_TYPE_ERROR, TEST_TIMESTAMP, TEST_SEQUENCE_NUMBER, zeroResult);
        
        // When
        String result = fragmentAck.toString();
        
        // Then
        assertTrue(result.contains("\"ErrorId\": " + zeroResult));
    }

    @Test
    public void whenToStringFormatting_thenShouldContainProperJsonStructure() {
        // Given
        KinesisVideoFragmentAck fragmentAck = new KinesisVideoFragmentAck(
            FragmentAckType.FRAGMENT_ACK_TYPE_RECEIVED, TEST_TIMESTAMP, TEST_SEQUENCE_NUMBER, HTTP_OK);
        
        // When
        String result = fragmentAck.toString();
        
        // Then
        assertTrue(result.startsWith("{"));
        assertTrue(result.endsWith("}"));
        assertTrue(result.contains("Acknowledgement"));
        assertTrue(result.contains("\t")); // Should contain tabs for formatting
        assertTrue(result.contains("\n")); // Should contain newlines for formatting
    }

    @Test
    public void whenCreatingFragmentAckWithEmptySequenceNumberUsingIntConstructor_thenShouldNotThrowException() {
        // Given
        int ackType = FragmentAckType.FRAGMENT_ACK_TYPE_BUFFERING;
        
        // When
        new KinesisVideoFragmentAck(ackType, TEST_TIMESTAMP, "", HTTP_OK);
        
        // Then - no exception should be thrown
    }

    @Test
    public void whenCreatingFragmentAckWithEmptySequenceNumberUsingObjectConstructor_thenShouldNotThrowException() {
        // Given
        FragmentAckType ackType = new FragmentAckType(FragmentAckType.FRAGMENT_ACK_TYPE_RECEIVED);
        
        // When
        new KinesisVideoFragmentAck(ackType, TEST_TIMESTAMP, "", HTTP_OK);
        
        // Then - no exception should be thrown
    }

    @Test(expected = IllegalArgumentException.class)
    public void whenCreatingFragmentAckWithNullSequenceNumberUsingIntConstructor_thenShouldThrowException() {
        // Given
        int ackType = FragmentAckType.FRAGMENT_ACK_TYPE_BUFFERING;
        
        // When
        new KinesisVideoFragmentAck(ackType, TEST_TIMESTAMP, null, HTTP_OK);
        
        // Then - exception should be thrown
    }

    @Test(expected = IllegalArgumentException.class)
    public void whenCreatingFragmentAckWithNullSequenceNumberUsingObjectConstructor_thenShouldThrowException() {
        // Given
        FragmentAckType ackType = new FragmentAckType(FragmentAckType.FRAGMENT_ACK_TYPE_RECEIVED);
        
        // When
        new KinesisVideoFragmentAck(ackType, TEST_TIMESTAMP, null, HTTP_OK);
        
        // Then - exception should be thrown
    }

    @Test(expected = IllegalArgumentException.class)
    public void whenCreatingFragmentAckWithNullAckType_thenShouldThrowException() {
        // Given
        FragmentAckType nullAckType = null;
        
        // When
        new KinesisVideoFragmentAck(nullAckType, TEST_TIMESTAMP, TEST_SEQUENCE_NUMBER, HTTP_OK);
        
        // Then - exception should be thrown
    }

    @Test
    public void whenCreatingFragmentAckWithValidParameters_thenShouldNotThrowException() {
        // Given
        FragmentAckType ackType = new FragmentAckType(FragmentAckType.FRAGMENT_ACK_TYPE_PERSISTED);
        
        // When
        KinesisVideoFragmentAck fragmentAck = new KinesisVideoFragmentAck(
            ackType, TEST_TIMESTAMP, TEST_SEQUENCE_NUMBER, HTTP_OK);
        
        // Then
        assertNotNull(fragmentAck);
        assertEquals(ackType, fragmentAck.getAckType());
        assertEquals(TEST_SEQUENCE_NUMBER, fragmentAck.getSequenceNumber());
    }

    @Test
    public void whenCreatingMultipleInstancesWithSameValues_thenShouldMaintainIndependence() {
        // Given
        FragmentAckType ackType = new FragmentAckType(FragmentAckType.FRAGMENT_ACK_TYPE_BUFFERING);
        
        // When
        KinesisVideoFragmentAck fragmentAck1 = new KinesisVideoFragmentAck(
            ackType, TEST_TIMESTAMP, TEST_SEQUENCE_NUMBER, HTTP_OK);
        KinesisVideoFragmentAck fragmentAck2 = new KinesisVideoFragmentAck(
            ackType, TEST_TIMESTAMP, TEST_SEQUENCE_NUMBER, HTTP_OK);
        
        // Then
        assertEquals(fragmentAck1.getAckType().getIntType(), fragmentAck2.getAckType().getIntType());
        assertEquals(fragmentAck1.getTimestamp(), fragmentAck2.getTimestamp());
        assertEquals(fragmentAck1.getSequenceNumber(), fragmentAck2.getSequenceNumber());
        assertEquals(fragmentAck1.getResult(), fragmentAck2.getResult());
        assertEquals(fragmentAck1.getVersion(), fragmentAck2.getVersion());
    }
}
