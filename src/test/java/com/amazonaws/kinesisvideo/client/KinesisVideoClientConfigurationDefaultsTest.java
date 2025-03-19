package com.amazonaws.kinesisvideo.client;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class KinesisVideoClientConfigurationDefaultsTest {
    private static final boolean LEGACY = true;
    private static final boolean DUAL_STACK = false;

    private final String region;
    private final boolean isLegacyEndpoint;
    private final String expectedEndpoint;

    public KinesisVideoClientConfigurationDefaultsTest(final String region,
                                                       final boolean isLegacyEndpoint,
                                                       final String expectedEndpoint) {
        this.region = region;
        this.isLegacyEndpoint = isLegacyEndpoint;
        this.expectedEndpoint = expectedEndpoint;
    }

    @Parameterized.Parameters(name = "{index}: getControlPlaneEndpoint({0}, {1}) => {2}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                // Normal regions, legacy
                {"us-west-2", LEGACY, "kinesisvideo.us-west-2.amazonaws.com"},
                {"eu-central-1", LEGACY, "kinesisvideo.eu-central-1.amazonaws.com"},

                // China regions, legacy
                {"cn-north-1", LEGACY, "kinesisvideo.cn-north-1.amazonaws.com.cn"},
                {"cn-northwest-1", LEGACY, "kinesisvideo.cn-northwest-1.amazonaws.com.cn"},

                // Normal regions, dual stack
                {"us-west-2", DUAL_STACK, "kinesisvideo.us-west-2.api.aws"},
                {"eu-central-1", DUAL_STACK, "kinesisvideo.eu-central-1.api.aws"},

                // China regions, dual stack
                {"cn-north-1", DUAL_STACK, "kinesisvideo.cn-north-1.api.amazonwebservices.com.cn"},
                {"cn-northwest-1", DUAL_STACK, "kinesisvideo.cn-northwest-1.api.amazonwebservices.com.cn"}
        });
    }

    @Test
    public void testGetControlPlaneEndpoint() {
        assertEquals(expectedEndpoint, KinesisVideoClientConfigurationDefaults.getControlPlaneEndpoint(region, isLegacyEndpoint));
    }
}
