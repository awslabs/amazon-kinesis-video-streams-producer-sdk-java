package com.amazonaws.kinesisvideo.auth;

import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class KinesisVideoCredentialsTest {

    @Test
    public void testConstructorWithoutSessionToken() {
        final String accessKey = "AKIA_TEST_KEY";
        final String secretKey = "SECRET_TEST_KEY";

        final KinesisVideoCredentials creds = new KinesisVideoCredentials(accessKey, secretKey);

        assertEquals(accessKey, creds.getAccessKey());
        assertEquals(secretKey, creds.getSecretKey());
        assertNull(creds.getSessionToken());
        assertEquals(KinesisVideoCredentials.CREDENTIALS_NEVER_EXPIRE, creds.getExpiration());
    }

    @Test
    public void testConstructorWithSessionToken() {
        final String accessKey = "AKIA_WITH_SESSION";
        final String secretKey = "SECRET_WITH_SESSION";
        final String sessionToken = "SESSION_TOKEN";
        final Date expiration = new Date(System.currentTimeMillis() + 60000); // 1 minute from now

        final KinesisVideoCredentials creds = new KinesisVideoCredentials(accessKey, secretKey, sessionToken, expiration);

        assertEquals(accessKey, creds.getAccessKey());
        assertEquals(secretKey, creds.getSecretKey());
        assertEquals(sessionToken, creds.getSessionToken());
        assertEquals(expiration, creds.getExpiration());
    }

    @Test
    public void testEmptyCredentials() {
        final KinesisVideoCredentials emptyCreds = KinesisVideoCredentials.EMPTY_KINESIS_VIDEO_CREDENTIALS;

        assertEquals("", emptyCreds.getAccessKey());
        assertEquals("", emptyCreds.getSecretKey());
        assertNull(emptyCreds.getSessionToken());
        assertEquals(KinesisVideoCredentials.CREDENTIALS_NEVER_EXPIRE, emptyCreds.getExpiration());
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullAccessKey() {
        new KinesisVideoCredentials(null, "secret");
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullSecretKey() {
        new KinesisVideoCredentials("access", null);
    }

    @Test(expected = NullPointerException.class)
    public void testFullConstructorNullAccessKey() {
        new KinesisVideoCredentials(null, "secret", "token", new Date());
    }

    @Test(expected = NullPointerException.class)
    public void testFullConstructorNullSecretKey() {
        new KinesisVideoCredentials("access", null, "token", new Date());
    }
}
