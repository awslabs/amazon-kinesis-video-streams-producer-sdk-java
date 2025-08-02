package com.amazonaws.kinesisvideo.java.auth;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentials;
import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentialsProvider;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JavaCredentialsFactoryTest {

    private static final String TEST_ACCESS_KEY = "testAccessKey";
    private static final String TEST_SECRET_KEY = "testSecretKey";
    private static final String TEST_SESSION_TOKEN = "testSessionToken";

    @Test(expected = IllegalArgumentException.class)
    @SuppressWarnings({"ConstantConditions"}) // Passing null into parameter marked @Nonnull
    public void whenCreateKinesisVideoCredentialsProviderWithNullAwsCredentialsProvider_thenThrowsIllegalArgumentException() {
        JavaCredentialsFactory.createKinesisVideoCredentialsProvider(null);
    }

    @Test(expected = IllegalArgumentException.class)
    @SuppressWarnings({"ConstantConditions"})
    public void whenCreateKinesisVideoCredentialsProviderWithNullDuration_thenThrowsIllegalArgumentException() {
        final AWSCredentialsProvider provider = TestAWSCredentialsProvider.createNonTemporaryCredentialsProvider();

        JavaCredentialsFactory.createKinesisVideoCredentialsProvider(provider, null);
    }

    @Test
    @SuppressWarnings({"ConstantConditions"})
    public void whenCreateKinesisVideoCredentialsProviderWithSessionCredentials_thenReturnsProviderWithRefreshInterval() {
        // Given
        final AWSCredentialsProvider provider = TestAWSCredentialsProvider.createTemporaryCredentialsProvider();
        final Duration duration = Duration.ofMinutes(30);

        // When
        final KinesisVideoCredentialsProvider result = JavaCredentialsFactory.createKinesisVideoCredentialsProvider(provider, duration);

        // Then
        assertNotNull(result);
        assertTrue(result instanceof JavaCredentialsProviderImpl);

        // Verify the correct constructor was used (the one with refresh interval)
        try {
            final KinesisVideoCredentials credentials = result.getCredentials();
            assertTrue(credentials.isTemporary());
            assertNotEquals(KinesisVideoCredentials.CREDENTIALS_NEVER_EXPIRE, credentials.getExpiration());
            assertEquals(TEST_ACCESS_KEY, credentials.getAccessKey());
            assertEquals(TEST_SECRET_KEY, credentials.getSecretKey());
            assertEquals(TEST_SESSION_TOKEN, credentials.getSessionToken());
        } catch (final KinesisVideoException e) {
            fail("Exception should not be thrown: " + e.getMessage());
        }
    }

    @Test
    @SuppressWarnings({"ConstantConditions"})
    public void whenCreateKinesisVideoCredentialsProviderWithNonSessionCredentials_thenReturnsProviderWithoutRefreshInterval() {
        // Given
        final AWSCredentialsProvider provider = TestAWSCredentialsProvider.createNonTemporaryCredentialsProvider();
        final Duration duration = Duration.ofMinutes(30);

        // When
        final KinesisVideoCredentialsProvider result = JavaCredentialsFactory.createKinesisVideoCredentialsProvider(provider, duration);

        // Then
        assertNotNull(result);
        assertTrue(result instanceof JavaCredentialsProviderImpl);

        // Verify the correct constructor was used (the one without refresh interval)
        try {
            final KinesisVideoCredentials credentials = result.getCredentials();
            assertFalse(credentials.isTemporary());
            assertEquals(KinesisVideoCredentials.CREDENTIALS_NEVER_EXPIRE, credentials.getExpiration());
            assertEquals(TEST_ACCESS_KEY, credentials.getAccessKey());
            assertEquals(TEST_SECRET_KEY, credentials.getSecretKey());
            assertNull(credentials.getSessionToken());
        } catch (final KinesisVideoException e) {
            fail("Exception should not be thrown: " + e.getMessage());
        }
    }

    @Test
    @SuppressWarnings({"ConstantConditions"})
    public void whenCreateKinesisVideoCredentialsProviderWithDefaultDuration_thenUsesOneHourDuration() {
        // Given
        final AWSCredentialsProvider provider = TestAWSCredentialsProvider.createTemporaryCredentialsProvider();

        // When
        final KinesisVideoCredentialsProvider result = JavaCredentialsFactory.createKinesisVideoCredentialsProvider(provider);

        // Then
        assertNotNull(result);
        assertTrue(result instanceof JavaCredentialsProviderImpl);

        // Verify the correct constructor was used (the one with refresh interval)
        try {
            final KinesisVideoCredentials credentials = result.getCredentials();
            assertTrue(credentials.isTemporary());
            assertNotEquals(KinesisVideoCredentials.CREDENTIALS_NEVER_EXPIRE, credentials.getExpiration());
            assertEquals(TEST_ACCESS_KEY, credentials.getAccessKey());
            assertEquals(TEST_SECRET_KEY, credentials.getSecretKey());
            assertEquals(TEST_SESSION_TOKEN, credentials.getSessionToken());
        } catch (final KinesisVideoException e) {
            fail("Exception should not be thrown: " + e.getMessage());
        }
    }

    @Test
    public void whenInstantiatingJavaCredentialsFactory_thenThrowsUnsupportedOperationException() {
        // When/Then
        try {
            // Using reflection to call private constructor
            final java.lang.reflect.Constructor<JavaCredentialsFactory> constructor =
                    JavaCredentialsFactory.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
            fail("Expected UnsupportedOperationException");
        } catch (final Exception e) {
            // The reflection API wraps the original exception
            assertTrue(e.getCause() instanceof UnsupportedOperationException);
        }
    }

    /**
     * Custom implementation of AWSCredentialsProvider for testing
     */
    private static class TestAWSCredentialsProvider implements AWSCredentialsProvider {
        private final boolean useSessionCredentials;

        public static TestAWSCredentialsProvider createTemporaryCredentialsProvider() {
            return new TestAWSCredentialsProvider(true);
        }

        public static TestAWSCredentialsProvider createNonTemporaryCredentialsProvider() {
            return new TestAWSCredentialsProvider(false);
        }

        private TestAWSCredentialsProvider(final boolean useSessionCredentials) {
            this.useSessionCredentials = useSessionCredentials;
        }

        @Override
        public AWSCredentials getCredentials() {
            if (this.useSessionCredentials) {
                return new BasicSessionCredentials(TEST_ACCESS_KEY, TEST_SECRET_KEY, TEST_SESSION_TOKEN);
            } else {
                return new BasicAWSCredentials(TEST_ACCESS_KEY, TEST_SECRET_KEY);
            }
        }

        @Override
        public void refresh() {
            // No-op for testing
        }
    }

    // ========== THREAD SAFETY TESTS ==========

    /**
     * Tests that when JavaCredentialsFactory.getKinesisVideoCredentialsProvider() is called
     * concurrently by multiple threads, then all providers are created successfully without race conditions.
     */
    @Test
    @SuppressWarnings("ConstantConditions")
    public void whenCredentialsProviderCreatedConcurrently_thenAllProvidersCreatedSuccessfully() throws InterruptedException {
        final int numThreads = 10;
        final int operationsPerThread = 20;
        final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        final CountDownLatch startLatch = new CountDownLatch(1); // All threads wait for this
        final CountDownLatch completionLatch = new CountDownLatch(numThreads);
        final AtomicBoolean testFailed = new AtomicBoolean(false);
        final AtomicReference<String> failureReason = new AtomicReference<>();
        final AtomicInteger successfulCreations = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    // Wait for all threads to be ready before starting
                    startLatch.await();

                    for (int j = 0; j < operationsPerThread; j++) {
                        try {
                            // Test creating non-temporary credentials provider
                            final AWSCredentialsProvider awsProvider1 = TestAWSCredentialsProvider.createNonTemporaryCredentialsProvider();
                            final KinesisVideoCredentialsProvider provider1 =
                                    JavaCredentialsFactory.createKinesisVideoCredentialsProvider(awsProvider1);

                            // Test creating temporary credentials provider
                            final AWSCredentialsProvider awsProvider2 = TestAWSCredentialsProvider.createTemporaryCredentialsProvider();
                            final KinesisVideoCredentialsProvider provider2 =
                                    JavaCredentialsFactory.createKinesisVideoCredentialsProvider(
                                            awsProvider2, Duration.ofMinutes(30));

                            // Verify providers are not null and work correctly
                            if (provider1 == null || provider2 == null) {
                                testFailed.set(true);
                                failureReason.set("Created provider was null");
                                return;
                            }

                            // Test that providers can be used to get credentials
                            final KinesisVideoCredentials creds1 = provider1.getCredentials();
                            final KinesisVideoCredentials creds2 = provider2.getCredentials();

                            if (creds1 == null || creds2 == null) {
                                testFailed.set(true);
                                failureReason.set("Provider returned null credentials");
                                return;
                            }

                            // Verify credential properties
                            if (creds1.isTemporary() || !creds2.isTemporary()) {
                                testFailed.set(true);
                                failureReason.set("Incorrect temporary status for credentials");
                                return;
                            }

                            successfulCreations.incrementAndGet();
                        } catch (final Exception e) {
                            testFailed.set(true);
                            failureReason.set("Exception during concurrent creation: " + e.getMessage());
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
                completionLatch.await(15, TimeUnit.SECONDS));
        assertFalse("Thread safety test failed: " + failureReason.get(), testFailed.get());
        assertEquals("Not all provider creations completed successfully",
                numThreads * operationsPerThread, successfulCreations.get());

        executor.shutdown();
    }

    /**
     * Tests that when multiple threads access factory methods with a shared AWS credentials provider,
     * then all operations complete successfully with consistent credential values.
     */
    @Test
    public void whenSharedAWSProviderAccessedConcurrently_thenConsistentCredentialsReturned() throws InterruptedException {
        final AWSCredentialsProvider sharedAwsProvider = TestAWSCredentialsProvider.createTemporaryCredentialsProvider();

        final int numThreads = 15;
        final int operationsPerThread = 10;
        final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        final CountDownLatch startLatch = new CountDownLatch(1); // All threads wait for this
        final CountDownLatch completionLatch = new CountDownLatch(numThreads);
        final AtomicBoolean testFailed = new AtomicBoolean(false);
        final AtomicReference<String> failureReason = new AtomicReference<>();
        final AtomicInteger successfulOperations = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    // Wait for all threads to be ready before starting
                    startLatch.await();

                    for (int j = 0; j < operationsPerThread; j++) {
                        try {
                            // Create provider using shared AWS provider
                            final KinesisVideoCredentialsProvider provider =
                                    JavaCredentialsFactory.createKinesisVideoCredentialsProvider(
                                            sharedAwsProvider, Duration.ofHours(1));

                            // Get credentials and verify they work
                            final KinesisVideoCredentials credentials = provider.getCredentials();

                            if (credentials == null) {
                                testFailed.set(true);
                                failureReason.set("Provider returned null credentials");
                                return;
                            }

                            // Verify credential values are consistent
                            if (!TEST_ACCESS_KEY.equals(credentials.getAccessKey()) ||
                                    !TEST_SECRET_KEY.equals(credentials.getSecretKey()) ||
                                    !TEST_SESSION_TOKEN.equals(credentials.getSessionToken()) ||
                                    !credentials.isTemporary()) {
                                testFailed.set(true);
                                failureReason.set("Inconsistent credential values");
                                return;
                            }

                            successfulOperations.incrementAndGet();
                        } catch (final Exception e) {
                            testFailed.set(true);
                            failureReason.set("Exception during concurrent access: " + e.getMessage());
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
        assertEquals("Not all operations completed successfully",
                numThreads * operationsPerThread, successfulOperations.get());

        executor.shutdown();
    }
}
