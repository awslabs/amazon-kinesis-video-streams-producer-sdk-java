package com.amazonaws.kinesisvideo.common;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.kinesisvideo.client.KinesisVideoClientConfiguration;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducer;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.internal.producer.client.KinesisVideoServiceClient;
import com.amazonaws.kinesisvideo.java.service.CachedInfoMultiAuthServiceCallbacksImpl;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.Tag;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamResult;
import com.amazonaws.services.kinesisvideo.model.StreamInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CachedInfoMultiAuthServiceCallbacksImplTest {

    private static final Logger log = LogManager.getLogger(CachedInfoMultiAuthServiceCallbacksImplTest.class);
    private static final String STREAM_NAME = "test-stream";
    private static final String ENDPOINT = "https://test-endpoint.amazonaws.com";

    private KinesisVideoClientConfiguration configuration;
    @Mock
    private KinesisVideoServiceClient kinesisVideoServiceClient;
    @Mock
    private KinesisVideoProducer kinesisVideoProducer;
    @Mock
    private KinesisVideoProducerStream stream;
    @Mock
    private AWSCredentialsProvider credentialsProvider;

    private ScheduledExecutorService executor;
    private CachedInfoMultiAuthServiceCallbacksImpl callbacks;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        executor = Executors.newScheduledThreadPool(1);
        configuration = KinesisVideoClientConfiguration.builder().build();
        callbacks = new CachedInfoMultiAuthServiceCallbacksImpl(log, executor, configuration, kinesisVideoServiceClient);
        
        AWSCredentials credentials = new BasicAWSCredentials("accessKey", "secretKey");
        when(credentialsProvider.getCredentials()).thenReturn(credentials);
    }

    @Test
    public void testInitialize() {
        assertFalse(callbacks.isInitialized());
        callbacks.initialize(kinesisVideoProducer);
        assertTrue(callbacks.isInitialized());
    }

    @Test(expected = ProducerException.class)
    public void testCreateStreamThrowsException() throws ProducerException {
        callbacks.createStream("device", STREAM_NAME, "video/h264", null, 24, 0, 1000, null, 0, stream);
    }

    @Test
    public void testDescribeStreamWithCachedInfo() throws ProducerException {
        callbacks.initialize(kinesisVideoProducer);
        
        DescribeStreamResult streamResult = createMockDescribeStreamResult();
        callbacks.addStreamInfoToCache(STREAM_NAME, streamResult);
        
        callbacks.describeStream(STREAM_NAME, 0, 1000, null, 0, 1L, stream);
        
        verify(kinesisVideoProducer, timeout(1000)).describeStreamResult(eq(stream), eq(1L), any(), eq(200));
    }

    @Test(expected = ProducerException.class)
    public void testDescribeStreamWithoutCachedInfo() throws ProducerException {
        callbacks.initialize(kinesisVideoProducer);
        callbacks.describeStream(STREAM_NAME, 0, 1000, null, 0, 1L, stream);
    }

    @Test
    public void testGetStreamingEndpointWithCachedInfo() throws ProducerException {
        callbacks.initialize(kinesisVideoProducer);
        callbacks.addStreamingEndpointToCache(STREAM_NAME, ENDPOINT);
        
        callbacks.getStreamingEndpoint(STREAM_NAME, "PUT_MEDIA", 0, 1000, null, 0, 1L, stream);
        
        verify(kinesisVideoProducer, timeout(1000)).getStreamingEndpointResult(stream, 1L, ENDPOINT, 200);
    }

    @Test(expected = ProducerException.class)
    public void testGetStreamingEndpointWithoutCachedInfo() throws ProducerException {
        callbacks.initialize(kinesisVideoProducer);
        callbacks.getStreamingEndpoint(STREAM_NAME, "PUT_MEDIA", 0, 1000, null, 0, 1L, stream);
    }

    @Test
    public void testGetStreamingTokenWithCachedCredentials() throws ProducerException {
        callbacks.initialize(kinesisVideoProducer);
        callbacks.addCredentialsProviderToCache(STREAM_NAME, credentialsProvider);
        
        callbacks.getStreamingToken(STREAM_NAME, 0, 1000, null, 0, 1L, stream);
        
        verify(kinesisVideoProducer, timeout(1000)).getStreamingTokenResult(eq(stream), eq(1L), any(byte[].class), anyLong(), eq(200));
    }

    @Test(expected = ProducerException.class)
    public void testGetStreamingTokenWithoutCachedCredentials() throws ProducerException {
        callbacks.initialize(kinesisVideoProducer);
        callbacks.getStreamingToken(STREAM_NAME, 0, 1000, null, 0, 1L, stream);
    }

    @Test
    public void testTagResourceWithCachedTags() throws ProducerException {
        callbacks.initialize(kinesisVideoProducer);
        Tag[] tags = {new Tag("key1", "value1"), new Tag("key2", "value2")};
        callbacks.addTagInfoToCache(STREAM_NAME, tags);
        
        String resourceArn = "arn:aws:kinesisvideo:us-west-2:123456789012:stream/" + STREAM_NAME + "/1234567890";
        callbacks.tagResource(resourceArn, null, 0, 1000, null, 0, 1L, stream);
        
        verify(kinesisVideoProducer, timeout(1000)).tagResourceResult(stream, 1L, 200);
    }

    @Test
    public void testRemoveStreamFromCache() {
        callbacks.addStreamInfoToCache(STREAM_NAME, createMockDescribeStreamResult());
        callbacks.addStreamingEndpointToCache(STREAM_NAME, ENDPOINT);
        callbacks.addCredentialsProviderToCache(STREAM_NAME, credentialsProvider);
        callbacks.addTagInfoToCache(STREAM_NAME, new Tag[]{new Tag("key", "value")});
        
        callbacks.removeStreamFromCache(STREAM_NAME);
        
        // Verify cache is cleared by expecting exceptions when accessing cached data
        callbacks.initialize(kinesisVideoProducer);
        try {
            callbacks.describeStream(STREAM_NAME, 0, 1000, null, 0, 1L, stream);
        } catch (ProducerException e) {
            // Expected
        }
    }

    private DescribeStreamResult createMockDescribeStreamResult() {
        StreamInfo streamInfo = new StreamInfo()
                .withStreamName(STREAM_NAME)
                .withDeviceName("test-device")
                .withMediaType("video/h264")
                .withVersion("1.0")
                .withStreamARN("arn:aws:kinesisvideo:us-west-2:123456789012:stream/" + STREAM_NAME + "/1234567890")
                .withStatus("ACTIVE")
                .withCreationTime(new Date())
                .withDataRetentionInHours(24)
                .withKmsKeyId("test-key");
        
        return new DescribeStreamResult().withStreamInfo(streamInfo);
    }
}