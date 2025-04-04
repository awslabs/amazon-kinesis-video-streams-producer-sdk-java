package com.amazonaws.kinesisvideo.client;

import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentials;
import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentialsProvider;
import com.amazonaws.kinesisvideo.auth.StaticCredentialsProvider;
import com.amazonaws.kinesisvideo.internal.service.DefaultServiceCallbacksImpl;
import com.amazonaws.kinesisvideo.producer.StorageCallbacks;
import com.amazonaws.kinesisvideo.storage.DefaultStorageCallbacks;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClient;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoPutMediaClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

@RunWith(Parameterized.class)
public class KinesisVideoClientConfigurationTest {

    private final String region;
    private final String endpoint;
    private final Boolean isLegacyEndpoint;
    private final String expectedRegion;
    private final String expectedEndpoint;

    public KinesisVideoClientConfigurationTest(final String region, final String endpoint, final Boolean isLegacyEndpoint,
                                               final String expectedRegion, final String expectedEndpoint) {
        this.region = region;
        this.endpoint = endpoint;
        this.isLegacyEndpoint = isLegacyEndpoint;
        this.expectedRegion = expectedRegion;
        this.expectedEndpoint = expectedEndpoint;
    }

    @Parameterized.Parameters(name = "{index}: region={0}, endpoint={1}, isLegacy={2} => expectedRegion={3}, expectedEndpoint={4}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                // Default values should be applied when no region/endpoint is provided
                {null, null, true, "us-west-2", "kinesisvideo.us-west-2.amazonaws.com"},
                {null, null, false, "us-west-2", "kinesisvideo.us-west-2.api.aws"},

                // Specified region but no endpoint, SDK should construct the endpoint
                {"eu-central-1", null, true, "eu-central-1", "kinesisvideo.eu-central-1.amazonaws.com"},
                {"eu-central-1", null, false, "eu-central-1", "kinesisvideo.eu-central-1.api.aws"},

                // If endpoint override provided, use that
                {null, "custom-endpoint.amazonaws.com", true, "us-west-2", "custom-endpoint.amazonaws.com"},
                {null, "custom-endpoint.api.aws", false, "us-west-2", "custom-endpoint.api.aws"},

                // Check CN regions since they have a different format
                {"cn-north-1", null, true, "cn-north-1", "kinesisvideo.cn-north-1.amazonaws.com.cn"},
                {"cn-north-1", null, false, "cn-north-1", "kinesisvideo.cn-north-1.api.amazonwebservices.com.cn"},
        });
    }

    @Test
    public void testRegionAndEndpoint() {
        final KinesisVideoClientConfiguration.Builder builder = KinesisVideoClientConfiguration.builder();

        if (region != null) {
            builder.withRegion(region);
        }
        if (endpoint != null) {
            builder.withEndpoint(endpoint);
        }
        if (isLegacyEndpoint != null) {
            builder.withIsLegacyEndpoint(isLegacyEndpoint);
        }

        final KinesisVideoClientConfiguration config = builder.build();

        assertEquals(expectedRegion, config.getRegion());
        assertEquals(expectedEndpoint, config.getEndpoint());
    }

    @Test
    public void testCustomStorageCallbacks() {
        final StorageCallbacks mockStorageCallbacks = mock(StorageCallbacks.class);

        final KinesisVideoClientConfiguration config = KinesisVideoClientConfiguration.builder()
                .withStorageCallbacks(mockStorageCallbacks)
                .withIsLegacyEndpoint(true)
                .build();

        assertEquals(mockStorageCallbacks, config.getStorageCallbacks());
    }

    @Test
    public void testCustomCredentialsProvider() {
        final KinesisVideoCredentialsProvider mockProvider = mock(KinesisVideoCredentialsProvider.class);

        final KinesisVideoClientConfiguration config = KinesisVideoClientConfiguration.builder()
                .withCredentialsProvider(mockProvider)
                .withIsLegacyEndpoint(true)
                .build();

        assertEquals(mockProvider, config.getCredentialsProvider());
    }

    @Test
    public void testIpVersionDefault() {
        final KinesisVideoClientConfiguration config = KinesisVideoClientConfiguration.builder()
                .build();

        assertEquals(KinesisVideoClientConfigurationDefaults.BOTH_IPV4_AND_IPV6, config.getIpVersionFilter());
    }

    @Test
    public void testIpVersionSet() {
        final KinesisVideoClientConfiguration config = KinesisVideoClientConfiguration.builder()
                .withIPVersionFilter(IPVersionFilter.IPV6)
                .build();

        assertEquals(IPVersionFilter.IPV6, config.getIpVersionFilter());
    }

    @Test
    public void testToStringDoesntThrowNullPointerException() {
        final KinesisVideoClientConfiguration config = KinesisVideoClientConfiguration.builder().build();
        final String toStringOutput = config.toString();

        // Ensure it's not the default Object.toString() output
        // (which typically contains "@" and class name)
        final boolean looksDefault = toStringOutput.matches(".*@\\p{XDigit}+");
        assertFalse("Expected toString() to be overridden, but was " + toStringOutput, looksDefault);
    }

    @Test
    public void testToStringIsNotDefaultImplementation() {
        final KinesisVideoClientConfiguration config = KinesisVideoClientConfiguration.builder()
                .withCredentialsProvider(new StaticCredentialsProvider(new KinesisVideoCredentials("ak", "sk")))
                .withStorageCallbacks(new DefaultStorageCallbacks())
                .withRegion("us-east-1")
                .withEndpoint("custom-endpoint.amazonaws.com")
                .withIsLegacyEndpoint(true)
                .build();

        final String toStringOutput = config.toString();

        // Ensure it's not the default Object.toString() output
        // (which typically contains "@" and class name)
        final boolean looksDefault = toStringOutput.matches(".*@\\p{XDigit}+");
        assertFalse("Expected toString() to be overridden, but was " + toStringOutput, looksDefault);

        // Sanity check it contains useful field info
        assertTrue("Expected toString() to contain 'us-east-1'", toStringOutput.contains("us-east-1"));
        assertTrue("Expected toString() to contain 'custom-endpoint' which was set", toStringOutput.contains("custom-endpoint"));
    }

    @Test
    public void checkServiceName() {
        final KinesisVideoClientConfiguration config = KinesisVideoClientConfiguration.builder()
                .build();

        assertEquals("kinesisvideo", config.getServiceName());
    }
}
