package com.amazonaws.kinesisvideo.client;

import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentialsProvider;
import com.amazonaws.kinesisvideo.producer.StorageCallbacks;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

@RunWith(Parameterized.class)
public class KinesisVideoClientConfigurationTest {

    private final String region;
    private final String endpoint;
    private final Boolean isLegacyEndpoint;
    private final String expectedRegion;
    private final String expectedEndpoint;

    public KinesisVideoClientConfigurationTest(String region, String endpoint, Boolean isLegacyEndpoint,
                                               String expectedRegion, String expectedEndpoint) {
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
        KinesisVideoClientConfiguration.Builder builder = KinesisVideoClientConfiguration.builder();

        if (region != null) {
            builder.withRegion(region);
        }
        if (endpoint != null) {
            builder.withEndpoint(endpoint);
        }
        if (isLegacyEndpoint != null) {
            builder.withIsLegacyEndpoint(isLegacyEndpoint);
        }

        KinesisVideoClientConfiguration config = builder.build();

        assertEquals(expectedRegion, config.getRegion());
        assertEquals(expectedEndpoint, config.getEndpoint());
    }

    @Test
    public void testCustomStorageCallbacks() {
        StorageCallbacks mockStorageCallbacks = mock(StorageCallbacks.class);

        KinesisVideoClientConfiguration config = KinesisVideoClientConfiguration.builder()
                .withStorageCallbacks(mockStorageCallbacks)
                .withIsLegacyEndpoint(true)
                .build();

        assertEquals(mockStorageCallbacks, config.getStorageCallbacks());
    }

    @Test
    public void testCustomCredentialsProvider() {
        KinesisVideoCredentialsProvider mockProvider = mock(KinesisVideoCredentialsProvider.class);

        KinesisVideoClientConfiguration config = KinesisVideoClientConfiguration.builder()
                .withCredentialsProvider(mockProvider)
                .withIsLegacyEndpoint(true)
                .build();

        assertEquals(mockProvider, config.getCredentialsProvider());
    }

    @Test
    public void testIpVersionDefault() {
        KinesisVideoClientConfiguration config = KinesisVideoClientConfiguration.builder()
                .build();

        assertEquals(KinesisVideoClientConfigurationDefaults.BOTH_IPV4_AND_IPV6, config.getIpVersionFilter());
    }

    @Test
    public void testIpVersionSet() {
        KinesisVideoClientConfiguration config = KinesisVideoClientConfiguration.builder()
                .withIPVersionFilter(IPVersionFilter.IPV6)
                .build();

        assertEquals(IPVersionFilter.IPV6, config.getIpVersionFilter());
    }
}
