package com.amazonaws.kinesisvideo.auth;

import org.junit.Test;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
        assertEquals(KinesisVideoCredentials.getCredentialsNeverExpire(), credentials.getExpiration());
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
    @SuppressWarnings({"ConstantConditions"})
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
    @SuppressWarnings({"ConstantConditions"})
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
        new KinesisVideoCredentials(ACCESS_KEY, SECRET_KEY, SESSION_TOKEN, KinesisVideoCredentials.getCredentialsNeverExpire());
    }

    // ========== THREAD SAFETY TESTS ==========

    /**
     * Tests that when getExpiration() is called concurrently by multiple threads,
     * then defensive copies are returned that can be safely modified without affecting internal state.
     */
    @Test
    public void whenGetExpirationCalledConcurrently_thenReturnsDefensiveCopiesSafely() throws InterruptedException {
        final Date originalExpiration = new Date(System.currentTimeMillis() + 3600000); // 1 hour from now
        final KinesisVideoCredentials credentials = new KinesisVideoCredentials(
                ACCESS_KEY, SECRET_KEY, SESSION_TOKEN, originalExpiration);

        final int numThreads = 10;
        final int operationsPerThread = 100;
        final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        final CountDownLatch startLatch = new CountDownLatch(1); // All threads wait for this
        final CountDownLatch completionLatch = new CountDownLatch(numThreads);
        final AtomicBoolean testFailed = new AtomicBoolean(false);
        final AtomicReference<String> failureReason = new AtomicReference<>();

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    // Wait for all threads to be ready before starting
                    startLatch.await();

                    for (int j = 0; j < operationsPerThread; j++) {
                        // Get expiration and modify it
                        final Date expiration = credentials.getExpiration();
                        final long originalTime = expiration.getTime();

                        // Modify the returned date
                        expiration.setTime(0);

                        // Verify the modification didn't affect subsequent calls
                        final Date newExpiration = credentials.getExpiration();
                        if (newExpiration.getTime() != originalTime) {
                            testFailed.set(true);
                            failureReason.set("Internal state was modified by external date mutation");
                            return;
                        }

                        // Verify each call returns a new instance
                        final Date anotherExpiration = credentials.getExpiration();
                        if (expiration == anotherExpiration) {
                            testFailed.set(true);
                            failureReason.set("getExpiration() returned the same instance instead of defensive copy");
                            return;
                        }
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    testFailed.set(true);
                    failureReason.set("Thread was interrupted: " + e.getMessage());
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();

        assertTrue("Test threads did not complete within timeout",
                completionLatch.await(10, TimeUnit.SECONDS));
        assertFalse("Thread safety test failed: " + failureReason.get(), testFailed.get());

        executor.shutdown();
    }

    /**
     * Tests that when getCredentialsNeverExpire() is called concurrently by multiple threads,
     * then defensive copies are returned that can be safely modified without affecting the constant.
     */
    @Test
    public void whenGetCredentialsNeverExpireCalledConcurrently_thenReturnsDefensiveCopiesSafely() throws InterruptedException {
        final int numThreads = 10;
        final int operationsPerThread = 100;
        final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        final CountDownLatch latch = new CountDownLatch(numThreads);
        final AtomicBoolean testFailed = new AtomicBoolean(false);
        final AtomicReference<String> failureReason = new AtomicReference<>();

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        // Get the never expire date and modify it
                        final Date neverExpire = KinesisVideoCredentials.getCredentialsNeverExpire();
                        final long originalTime = neverExpire.getTime();

                        // Verify it has the expected value
                        if (originalTime != Long.MAX_VALUE) {
                            testFailed.set(true);
                            failureReason.set("getCredentialsNeverExpire() returned unexpected value");
                            return;
                        }

                        // Modify the returned date
                        neverExpire.setTime(0);

                        // Verify subsequent calls still return the correct value
                        final Date newNeverExpire = KinesisVideoCredentials.getCredentialsNeverExpire();
                        if (newNeverExpire.getTime() != Long.MAX_VALUE) {
                            testFailed.set(true);
                            failureReason.set("Internal constant was modified by external date mutation");
                            return;
                        }

                        // Verify each call returns a new instance
                        final Date anotherNeverExpire = KinesisVideoCredentials.getCredentialsNeverExpire();
                        if (neverExpire == anotherNeverExpire) {
                            testFailed.set(true);
                            failureReason.set("getCredentialsNeverExpire() returned the same instance instead of defensive copy");
                            return;
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue("Test threads did not complete within timeout",
                latch.await(10, TimeUnit.SECONDS));
        assertFalse("Thread safety test failed: " + failureReason.get(), testFailed.get());

        executor.shutdown();
    }

    /**
     * Tests that when multiple threads access all KinesisVideoCredentials methods concurrently,
     * then no race conditions occur and data remains consistent across all threads.
     */
    @Test
    public void whenAllMethodsAccessedConcurrently_thenNoRaceConditionsOccur() throws InterruptedException {
        final Date expiration = new Date(System.currentTimeMillis() + 3600000);
        final KinesisVideoCredentials credentials = new KinesisVideoCredentials(
                ACCESS_KEY, SECRET_KEY, SESSION_TOKEN, expiration);

        final int numThreads = 20;
        final int operationsPerThread = 50;
        final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        final CountDownLatch latch = new CountDownLatch(numThreads);
        final AtomicBoolean testFailed = new AtomicBoolean(false);
        final AtomicReference<String> failureReason = new AtomicReference<>();
        final AtomicInteger successfulOperations = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        try {
                            // Test all getter methods
                            final String accessKey = credentials.getAccessKey();
                            final String secretKey = credentials.getSecretKey();
                            final String sessionToken = credentials.getSessionToken();
                            final Date exp = credentials.getExpiration();
                            final boolean isTemp = credentials.isTemporary();

                            // Verify values are consistent
                            if (!ACCESS_KEY.equals(accessKey) ||
                                    !SECRET_KEY.equals(secretKey) ||
                                    !SESSION_TOKEN.equals(sessionToken) ||
                                    !isTemp) {
                                testFailed.set(true);
                                failureReason.set("Inconsistent values returned from concurrent access");
                                return;
                            }

                            // Modify the returned date to ensure it doesn't affect internal state
                            exp.setTime(0);

                            successfulOperations.incrementAndGet();
                        } catch (final Exception e) {
                            testFailed.set(true);
                            failureReason.set("Exception during concurrent access: " + e.getMessage());
                            return;
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue("Test threads did not complete within timeout",
                latch.await(15, TimeUnit.SECONDS));
        assertFalse("Thread safety test failed: " + failureReason.get(), testFailed.get());
        assertEquals("Not all operations completed successfully",
                numThreads * operationsPerThread, successfulOperations.get());

        executor.shutdown();
    }

    /**
     * Tests that when the deprecated CREDENTIALS_NEVER_EXPIRE constant is accessed concurrently,
     * then no exceptions occur and backwards compatibility is maintained.
     */
    @Test
    @SuppressWarnings({"deprecation", "ConstantConditions"})
    public void whenDeprecatedConstantAccessedConcurrently_thenBackwardsCompatibilityMaintained() throws InterruptedException {
        final int numThreads = 10;
        final int operationsPerThread = 50;
        final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        final CountDownLatch latch = new CountDownLatch(numThreads);
        final AtomicBoolean testFailed = new AtomicBoolean(false);
        final AtomicReference<String> failureReason = new AtomicReference<>();

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        // Access the deprecated constant
                        final Date neverExpire = KinesisVideoCredentials.CREDENTIALS_NEVER_EXPIRE;

                        // Verify it has the expected initial value (might be modified by other threads)
                        // We can't guarantee the value due to the mutable nature, but we can test access
                        if (neverExpire == null) {
                            testFailed.set(true);
                            failureReason.set("CREDENTIALS_NEVER_EXPIRE returned null");
                            return;
                        }

                        // Test that we can read the time without exceptions
                        final long time = neverExpire.getTime();

                        // Note: We don't modify it here to avoid affecting other tests
                        // This test just verifies concurrent read access works
                    }
                } catch (final Exception e) {
                    testFailed.set(true);
                    failureReason.set("Exception during deprecated constant access: " + e.getMessage());
                    return;
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue("Test threads did not complete within timeout",
                latch.await(10, TimeUnit.SECONDS));
        assertFalse("Thread safety test failed: " + failureReason.get(), testFailed.get());

        executor.shutdown();
    }

    /**
     * Tests that when multiple KinesisVideoCredentials instances are created concurrently,
     * then all objects are properly constructed with correct defensive copying behavior.
     */
    @Test
    public void whenCredentialsCreatedConcurrently_thenAllObjectsProperlyConstructed() throws InterruptedException {
        final Date expiration = new Date(System.currentTimeMillis() + 3600000);
        final int numThreads = 10;
        final int objectsPerThread = 20;
        final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        final CountDownLatch latch = new CountDownLatch(numThreads);
        final AtomicBoolean testFailed = new AtomicBoolean(false);
        final AtomicReference<String> failureReason = new AtomicReference<>();
        final AtomicInteger successfulCreations = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < objectsPerThread; j++) {
                        try {
                            // Create credentials with same parameters
                            final KinesisVideoCredentials creds1 = new KinesisVideoCredentials(
                                    ACCESS_KEY, SECRET_KEY, SESSION_TOKEN, expiration);
                            final KinesisVideoCredentials creds2 = new KinesisVideoCredentials(
                                    ACCESS_KEY, SECRET_KEY);

                            // Verify they have expected properties
                            if (!creds1.isTemporary() || creds2.isTemporary()) {
                                testFailed.set(true);
                                failureReason.set("Incorrect temporary status");
                                return;
                            }

                            // Verify defensive copying works
                            final Date exp1 = creds1.getExpiration();
                            final Date exp2 = creds1.getExpiration();
                            if (exp1 == exp2) {
                                testFailed.set(true);
                                failureReason.set("getExpiration() returned same instance");
                                return;
                            }

                            successfulCreations.incrementAndGet();
                        } catch (final Exception e) {
                            testFailed.set(true);
                            failureReason.set("Exception during concurrent creation: " + e.getMessage());
                            return;
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue("Test threads did not complete within timeout",
                latch.await(10, TimeUnit.SECONDS));
        assertFalse("Thread safety test failed: " + failureReason.get(), testFailed.get());
        assertEquals("Not all object creations completed successfully",
                numThreads * objectsPerThread, successfulCreations.get());

        executor.shutdown();
    }
}
