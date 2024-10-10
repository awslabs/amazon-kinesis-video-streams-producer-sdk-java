package com.amazonaws.kinesisvideo.util;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.FileReader;

import static com.amazonaws.kinesisvideo.util.VersionUtil.AWS_SDK_KVS_PRODUCER_VERSION_STRING;
import static com.amazonaws.kinesisvideo.util.VersionUtil.getUserAgent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class VersionUtilTest {

    @Test
    public void test_versionString_isNotNullOrEmpty() {
        final String producerVersionString = AWS_SDK_KVS_PRODUCER_VERSION_STRING;
        assertNotNull(producerVersionString);
        assertNotEquals("", producerVersionString);
    }

    @Test
    public void test_versionString_isEqualToDeclaredInPomXML() {
        final String producerVersionString = extractVersionFromPomXML("pom.xml");

        assertNotNull("project.version was not found in pom.xml!", producerVersionString);
        assertNotEquals("", producerVersionString);
        assertEquals(producerVersionString, AWS_SDK_KVS_PRODUCER_VERSION_STRING);
    }

    @Test
    public void test_userAgent_containsProjectVersion() {
        final String userAgent = getUserAgent();
        final String pomDefinedProjectVersion = extractVersionFromPomXML("pom.xml");

        assertNotNull(userAgent);
        assertNotEquals("", userAgent);

        assertNotNull(pomDefinedProjectVersion);
        assertNotEquals("", pomDefinedProjectVersion);

        assertTrue(userAgent.contains(pomDefinedProjectVersion));
    }

    private String extractVersionFromPomXML(final String pomFilePath) {
        try (final BufferedReader reader = new BufferedReader(new FileReader(pomFilePath))) {
            return reader.lines()
                    .filter(line -> line.contains("<version>"))
                    .map(line -> line.replace("<version>", "")
                            .replace("</version>", "").trim())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("'<version>{version}</version>' is not found in pom.xml"));
        } catch (final Exception ex) {
            fail("Error reading pom.xml: " + ex.getMessage());
            return null; // This return is unreachable because of fail(), but required for compilation
        }
    }
}
