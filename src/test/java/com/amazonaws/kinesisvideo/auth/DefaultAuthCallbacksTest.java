package com.amazonaws.kinesisvideo.auth;

import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.producer.AuthCallbacks;
import com.amazonaws.kinesisvideo.producer.AuthInfo;
import com.amazonaws.kinesisvideo.producer.AuthInfoType;
import com.amazonaws.kinesisvideo.producer.Time;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nullable;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DefaultAuthCallbacksTest {

    private ScheduledExecutorService executor;

    @Before
    public void setUp() {
        executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
                .setNameFormat("DefaultAuthCallbacksTest-thread-%d").build());
    }

    @Test
    public void testGetSecurityTokenReturnsValidAuthInfo() {
        // Set credentials that expire in 1 hour
        final long currentTimeMillis = System.currentTimeMillis();
        final Date expirationDate = new Date(currentTimeMillis + 3600 * 1000);
        final KinesisVideoCredentials credentials = new KinesisVideoCredentials("AKIA123", "secret", "token", expirationDate);

        final KinesisVideoCredentialsProvider credentialsProvider = new StaticCredentialsProvider(credentials);
        final AuthCallbacks authCallbacks = new DefaultAuthCallbacks(credentialsProvider, executor);

        final AuthInfo authInfo = authCallbacks.getSecurityToken();

        assertNotNull(authInfo);
        assertEquals(AuthInfoType.SECURITY_TOKEN, authInfo.getAuthType());
        assertNotNull(authInfo.getData());
        assertTrue("Expiration should be in the future!", authInfo.getExpiration() > currentTimeMillis * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND);

        assertTrue(new String(authInfo.getData()).contains("accessKey"));
    }

    @Test
    public void testGetSecurityTokenReturnsNullDataWhenCredentialsNull() {
        final KinesisVideoCredentialsProvider nullReturningCredentialsProvider = new KinesisVideoCredentialsProvider() {
            @Nullable
            @Override
            public KinesisVideoCredentials getCredentials() throws KinesisVideoException {
                return null;
            }

            @Nullable
            @Override
            public KinesisVideoCredentials getUpdatedCredentials() throws KinesisVideoException {
                return null;
            }
        };
        final AuthCallbacks authCallbacks = new DefaultAuthCallbacks(nullReturningCredentialsProvider, executor);

        final AuthInfo authInfo = authCallbacks.getSecurityToken();

        assertNotNull(authInfo);
        assertNull(authInfo.getData());
        assertEquals(0, authInfo.getExpiration());
    }

    @Test
    public void testCredentialsTookTooLongToRefresh() {

        final int TIMEOUT_MILLIS = 50;

        final KinesisVideoCredentialsProvider slowCredentialsProvider = new KinesisVideoCredentialsProvider() {
            @Nullable
            @Override
            public KinesisVideoCredentials getCredentials() throws KinesisVideoException {
                try {
                    Thread.sleep(TIMEOUT_MILLIS + 3000);
                } catch (final Exception e) {
                    fail();
                }
                return new KinesisVideoCredentials("AKIA123", "secret", "token", new Date());
            }

            @Nullable
            @Override
            public KinesisVideoCredentials getUpdatedCredentials() throws KinesisVideoException {
                try {
                    Thread.sleep(TIMEOUT_MILLIS + 3000);
                } catch (final Exception e) {
                    fail();
                }
                return new KinesisVideoCredentials("AKIA123", "secret", "token", new Date());
            }
        };
        final AuthCallbacks authCallbacks = new DefaultAuthCallbacks(slowCredentialsProvider, executor, TIMEOUT_MILLIS);

        final AuthInfo authInfo = authCallbacks.getSecurityToken();

        assertNotNull(authInfo);
        assertNull(authInfo.getData());
        assertEquals(0, authInfo.getExpiration());
    }
}
