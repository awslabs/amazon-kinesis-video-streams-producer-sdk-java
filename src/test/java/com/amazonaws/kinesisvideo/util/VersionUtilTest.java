package com.amazonaws.kinesisvideo.util;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import static com.amazonaws.kinesisvideo.util.VersionUtil.AWS_SDK_KVS_PRODUCER_VERSION_STRING;
import static com.amazonaws.kinesisvideo.util.VersionUtil.getUserAgent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class VersionUtilTest {

    private static final String POM_XML_LOCATION = "pom.xml";

    @Test
    public void test_versionString_isNotNullOrEmpty() {
        final String producerVersionString = AWS_SDK_KVS_PRODUCER_VERSION_STRING;
        assertNotNull(producerVersionString);
        assertNotEquals("", producerVersionString);
    }

    @Test
    public void test_versionString_isEqualToDeclaredInPomXML() throws IOException {
        final String producerVersionString = extractVersionFromPomXML();

        assertNotNull("project.version was not found in pom.xml!", producerVersionString);
        assertNotEquals("", producerVersionString);
        assertEquals(producerVersionString, AWS_SDK_KVS_PRODUCER_VERSION_STRING);
    }

    @Test
    public void test_userAgent_containsProjectVersion() throws IOException {
        final String userAgent = getUserAgent();
        final String pomDefinedProjectVersion = extractVersionFromPomXML();

        assertNotNull(userAgent);
        assertNotEquals("", userAgent);

        assertNotNull(pomDefinedProjectVersion);
        assertNotEquals("", pomDefinedProjectVersion);

        assertTrue(userAgent.contains(pomDefinedProjectVersion));
    }

    private String extractVersionFromPomXML() throws IOException {
        try (final BufferedReader reader = new BufferedReader(new FileReader(POM_XML_LOCATION))) {
            return reader.lines()
                    .filter(line -> line.contains("<version>"))
                    .map(line -> line.replace("<version>", "")
                            .replace("</version>", "").trim())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("'<version>projectVersion</version>' is not found in " + POM_XML_LOCATION));
        }
    }
}
