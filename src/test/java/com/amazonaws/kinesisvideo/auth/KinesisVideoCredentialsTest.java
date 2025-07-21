package com.amazonaws.kinesisvideo.auth;

import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link KinesisVideoCredentials} class.
 * Tests verify the behavior of credential objects for both temporary and non-temporary credentials.
 */
public class KinesisVideoCredentialsTest {

    private static final String ACCESS_KEY = "test-access-key";
    private static final String SECRET_KEY = "test-secret-key";
    private static final String SESSION_TOKEN = "test-session-token";
    private static final Date EXPIRATION = new Date(System.currentTimeMillis() + 3600000); // 1 hour from now

    /**
     * Tests that when creating non-temporary credentials:
     * - The access key and secret key are properly stored
     * - The session token is null
     * - The expiration is set to CREDENTIALS_NEVER_EXPIRE
     * - isTemporary() returns false
     */
    @Test
    public void whenCreatingNonTemporaryCredentials_thenFieldsAreSetCorrectly() {
        final KinesisVideoCredentials credentials = new KinesisVideoCredentials(ACCESS_KEY, SECRET_KEY);

        assertEquals(ACCESS_KEY, credentials.getAccessKey());
        assertEquals(SECRET_KEY, credentials.getSecretKey());
        assertNull(credentials.getSessionToken());
        assertEquals(KinesisVideoCredentials.CREDENTIALS_NEVER_EXPIRE, credentials.getExpiration());

        assertFalse(credentials.isTemporary());
    }

    /**
     * Tests that when creating temporary credentials:
     * - The access key, secret key, session token, and expiration are properly stored
     * - isTemporary() returns true
     */
    @Test
    public void whenCreatingTemporaryCredentials_thenFieldsAreSetCorrectly() {
        final KinesisVideoCredentials credentials = new KinesisVideoCredentials(
                ACCESS_KEY, SECRET_KEY, SESSION_TOKEN, EXPIRATION);

        assertEquals(ACCESS_KEY, credentials.getAccessKey());
        assertEquals(SECRET_KEY, credentials.getSecretKey());
        assertEquals(SESSION_TOKEN, credentials.getSessionToken());
        assertEquals(EXPIRATION, credentials.getExpiration());

        assertTrue(credentials.isTemporary());
    }

    /**
     * Tests that when providing a null access key:
     * - An IllegalArgumentException is thrown
     */
    @Test(expected = IllegalArgumentException.class)
    public void whenAccessKeyIsNull_thenThrowsIllegalArgumentException() {
        new KinesisVideoCredentials(null, SECRET_KEY);
    }

    /**
     * Tests that when providing a null secret key:
     * - An IllegalArgumentException is thrown
     */
    @Test(expected = IllegalArgumentException.class)
    public void whenSecretKeyIsNull_thenThrowsIllegalArgumentException() {
        new KinesisVideoCredentials(ACCESS_KEY, null);
    }

    /**
     * Tests that when providing an empty access key:
     * - An IllegalArgumentException is thrown
     */
    @Test(expected = IllegalArgumentException.class)
    public void whenAccessKeyIsEmpty_thenThrowsIllegalArgumentException() {
        new KinesisVideoCredentials("", SECRET_KEY);
    }

    /**
     * Tests that when providing an empty secret key:
     * - An IllegalArgumentException is thrown
     */
    @Test(expected = IllegalArgumentException.class)
    public void whenSecretKeyIsEmpty_thenThrowsIllegalArgumentException() {
        new KinesisVideoCredentials(ACCESS_KEY, "");
    }

    /**
     * Tests that when providing a null expiration:
     * - An IllegalArgumentException is thrown
     */
    @Test(expected = IllegalArgumentException.class)
    public void whenExpirationIsNull_thenThrowsIllegalArgumentException() {
        new KinesisVideoCredentials(ACCESS_KEY, SECRET_KEY, SESSION_TOKEN, null);
    }

    /**
     * Tests that when providing an empty session token:
     * - An IllegalArgumentException is thrown
     */
    @Test(expected = IllegalArgumentException.class)
    public void whenSessionTokenIsEmpty_thenThrowsIllegalArgumentException() {
        new KinesisVideoCredentials(ACCESS_KEY, SECRET_KEY, "", EXPIRATION);
    }

    /**
     * Tests that when providing a custom expiration:
     * - The expiration is stored correctly
     * - isTemporary() returns true for any expiration other than CREDENTIALS_NEVER_EXPIRE
     */
    @Test
    public void whenProvidingCustomExpiration_thenExpirationIsStoredCorrectly() {
        // Create credentials with custom expiration
        final Date customExpiration = new Date(System.currentTimeMillis() + 7200000); // 2 hours from now
        final KinesisVideoCredentials credentials = new KinesisVideoCredentials(
                ACCESS_KEY, SECRET_KEY, SESSION_TOKEN, customExpiration);

        assertEquals(customExpiration, credentials.getExpiration());
        assertTrue(credentials.isTemporary());
    }

    /**
     * Tests that when creating temporary credentials without a session token:
     * - An IllegalArgumentException is thrown
     */
    @Test(expected = IllegalArgumentException.class)
    public void whenCreatingTemporaryCredentialsWithoutSessionToken_thenThrowsIllegalArgumentException() {
        new KinesisVideoCredentials(ACCESS_KEY, SECRET_KEY, null, EXPIRATION);
    }

    /**
     * Tests that when creating non-temporary credentials with a session token:
     * - An IllegalArgumentException is thrown
     */
    @Test(expected = IllegalArgumentException.class)
    public void whenCreatingNonTemporaryCredentialsWithSessionToken_thenThrowsIllegalArgumentException() {
        new KinesisVideoCredentials(ACCESS_KEY, SECRET_KEY, SESSION_TOKEN, KinesisVideoCredentials.CREDENTIALS_NEVER_EXPIRE);
    }
}
