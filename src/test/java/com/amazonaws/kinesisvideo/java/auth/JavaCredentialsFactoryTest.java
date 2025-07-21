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
    public void whenGetKinesisVideoCredentialsProviderWithNullAwsCredentialsProvider_thenThrowsIllegalArgumentException() {
        JavaCredentialsFactory.getKinesisVideoCredentialsProvider(null);
    }

    @Test(expected = IllegalArgumentException.class)
    @SuppressWarnings({"ConstantConditions"})
    public void whenGetKinesisVideoCredentialsProviderWithNullDuration_thenThrowsIllegalArgumentException() {
        final AWSCredentialsProvider provider = TestAWSCredentialsProvider.createNonTemporaryCredentialsProvider();

        JavaCredentialsFactory.getKinesisVideoCredentialsProvider(provider, null);
    }

    @Test
    @SuppressWarnings({"ConstantConditions"})
    public void whenGetKinesisVideoCredentialsProviderWithSessionCredentials_thenReturnsProviderWithRefreshInterval() {
        // Given
        final AWSCredentialsProvider provider = TestAWSCredentialsProvider.createTemporaryCredentialsProvider();
        final Duration duration = Duration.ofMinutes(30);

        // When
        final KinesisVideoCredentialsProvider result = JavaCredentialsFactory.getKinesisVideoCredentialsProvider(provider, duration);

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
    public void whenGetKinesisVideoCredentialsProviderWithNonSessionCredentials_thenReturnsProviderWithoutRefreshInterval() {
        // Given
        final AWSCredentialsProvider provider = TestAWSCredentialsProvider.createNonTemporaryCredentialsProvider();
        final Duration duration = Duration.ofMinutes(30);

        // When
        final KinesisVideoCredentialsProvider result = JavaCredentialsFactory.getKinesisVideoCredentialsProvider(provider, duration);

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
    public void whenGetKinesisVideoCredentialsProviderWithDefaultDuration_thenUsesOneHourDuration() {
        // Given
        final AWSCredentialsProvider provider = TestAWSCredentialsProvider.createTemporaryCredentialsProvider();

        // When
        final KinesisVideoCredentialsProvider result = JavaCredentialsFactory.getKinesisVideoCredentialsProvider(provider);

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
}
