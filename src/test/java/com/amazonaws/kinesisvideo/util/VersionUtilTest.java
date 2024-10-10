package com.amazonaws.kinesisvideo.util;

import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

import static com.amazonaws.kinesisvideo.util.VersionUtil.AWS_SDK_KVS_PRODUCER_VERSION_STRING;
import static org.junit.Assert.*;

public class VersionUtilTest {

    @Test
    public void test_versionString_isNotNullOrEmpty() {
        final String userAgent = AWS_SDK_KVS_PRODUCER_VERSION_STRING;
        assertNotNull(userAgent);
        assertNotEquals("", userAgent);
    }

    @Test
    public void test_versionString_isEqualToDeclaredInPomXML() {
        final String version = extractVersionFromPomXML("pom.xml");

        assertNotNull("project.version was not found in pom.xml!", version);
        assertNotEquals("", version);
        assertEquals(version, AWS_SDK_KVS_PRODUCER_VERSION_STRING);
    }

    private String extractVersionFromPomXML(final String pomFilePath) {
        try (final BufferedReader reader = new BufferedReader(new FileReader(pomFilePath))) {
            return reader.lines()
                    .filter(line -> line.contains("<version>"))
                    .map(line -> line.replace("<version>", "")
                            .replace("</version>", "").trim())
                    .findFirst()
                    .orElse(null);
        } catch (final Exception ex) {
            fail("Error reading pom.xml: " + ex.getMessage());
            return null; // This return is unreachable because of fail(), but required for compilation
        }
    }
}
