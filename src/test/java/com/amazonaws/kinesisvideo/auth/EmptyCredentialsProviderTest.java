package com.amazonaws.kinesisvideo.auth;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class EmptyCredentialsProviderTest {

    @Test
    public void testGetCredentialsReturnsEmptyCredentials() throws Exception {
        final EmptyCredentialsProvider provider = new EmptyCredentialsProvider();
        final KinesisVideoCredentials credentials = provider.getCredentials();

        assertNotNull("Credentials should not be null", credentials);
        assertEquals("Access key should be empty", "", credentials.getAccessKey());
        assertEquals("Secret key should be empty", "", credentials.getSecretKey());
        assertNull("Session token should be null", credentials.getSessionToken());
        assertEquals("Expiration should be 'never expire'",
                KinesisVideoCredentials.CREDENTIALS_NEVER_EXPIRE,
                credentials.getExpiration());
    }

    @Test
    public void testGetUpdatedCredentialsReturnsEmptyCredentials() throws Exception {
        final EmptyCredentialsProvider provider = new EmptyCredentialsProvider();
        final KinesisVideoCredentials credentials = provider.getUpdatedCredentials();

        assertNotNull("Credentials should not be null", credentials);
        assertEquals("Access key should be empty", "", credentials.getAccessKey());
        assertEquals("Secret key should be empty", "", credentials.getSecretKey());
        assertNull("Session token should be null", credentials.getSessionToken());
        assertEquals("Expiration should be 'never expire'",
                KinesisVideoCredentials.CREDENTIALS_NEVER_EXPIRE,
                credentials.getExpiration());
    }
}
