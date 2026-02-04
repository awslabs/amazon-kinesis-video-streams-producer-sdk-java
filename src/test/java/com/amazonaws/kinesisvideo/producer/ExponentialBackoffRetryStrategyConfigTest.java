package com.amazonaws.kinesisvideo.producer;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import static com.amazonaws.kinesisvideo.producer.ExponentialBackoffRetryStrategyConfig.JitterType.FIXED_JITTER;
import static com.amazonaws.kinesisvideo.producer.ExponentialBackoffRetryStrategyConfig.LIMIT_KVS_JITTER_FACTOR_MILLISECONDS;
import static com.amazonaws.kinesisvideo.producer.ExponentialBackoffRetryStrategyConfig.LIMIT_KVS_MAX_WAIT_TIME_MILLISECONDS;
import static com.amazonaws.kinesisvideo.producer.ExponentialBackoffRetryStrategyConfig.LIMIT_KVS_MIN_TIME_TO_RESET_RETRY_STATE_MILLISECONDS;
import static com.amazonaws.kinesisvideo.producer.ExponentialBackoffRetryStrategyConfig.LIMIT_KVS_RETRY_TIME_FACTOR_MILLISECONDS;
import static com.amazonaws.kinesisvideo.producer.ExponentialBackoffRetryStrategyConfig.MIN_KVS_JITTER_FACTOR_MILLISECONDS;
import static com.amazonaws.kinesisvideo.producer.ExponentialBackoffRetryStrategyConfig.MIN_KVS_MAX_WAIT_TIME_MILLISECONDS;
import static com.amazonaws.kinesisvideo.producer.ExponentialBackoffRetryStrategyConfig.MIN_KVS_MIN_TIME_TO_RESET_RETRY_STATE_MILLISECONDS;
import static com.amazonaws.kinesisvideo.producer.ExponentialBackoffRetryStrategyConfig.MIN_KVS_RETRY_TIME_FACTOR_MILLISECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link ExponentialBackoffRetryStrategyConfig} class.
 * Tests cover all builder methods, validation, and configuration scenarios.
 */
public class ExponentialBackoffRetryStrategyConfigTest {

    private static final Logger log = LogManager.getLogger(ExponentialBackoffRetryStrategyConfigTest.class);

    @Test
    public void givenDefaultBuilder_whenBuildingConfig_thenAllFieldsUsePicDefaults() {
        // Given/When
        final ExponentialBackoffRetryStrategyConfig config =
                ExponentialBackoffRetryStrategyConfig.ExponentialBackoffRetryStrategyConfigBuilder.with()
                        .maxRetryCount(ExponentialBackoffRetryStrategyConfig.USE_PIC_DEFAULT)
                        .maxRetryWaitTimeMs(ExponentialBackoffRetryStrategyConfig.USE_PIC_DEFAULT)
                        .retryFactorTimeMs(ExponentialBackoffRetryStrategyConfig.USE_PIC_DEFAULT)
                        .minTimeToResetRetryStateMs(ExponentialBackoffRetryStrategyConfig.USE_PIC_DEFAULT)
                        .noJitter()
                        .build();

        // Then
        assertEquals(ExponentialBackoffRetryStrategyConfig.USE_PIC_DEFAULT, config.getMaxRetryCount());
        assertEquals(ExponentialBackoffRetryStrategyConfig.USE_PIC_DEFAULT, config.getMaxRetryWaitTimeMs());
        assertEquals(ExponentialBackoffRetryStrategyConfig.USE_PIC_DEFAULT, config.getRetryFactorTimeMs());
        assertEquals(ExponentialBackoffRetryStrategyConfig.USE_PIC_DEFAULT, config.getMinTimeToResetRetryStateMs());
        assertEquals(ExponentialBackoffRetryStrategyConfig.JitterType.NO_JITTER, config.getJitterType());
        assertEquals(ExponentialBackoffRetryStrategyConfig.USE_PIC_DEFAULT, config.getJitterFactor());
    }

    @Test
    public void givenStaticDefaultsMethod_whenCalled_thenReturnsConfigWithDefaults() {
        // Given/When
        final ExponentialBackoffRetryStrategyConfig config =
                ExponentialBackoffRetryStrategyConfig.ExponentialBackoffRetryStrategyConfigBuilder.defaults();

        // Then
        assertEquals(ExponentialBackoffRetryStrategyConfig.USE_PIC_DEFAULT, config.getMaxRetryCount());
        assertEquals(ExponentialBackoffRetryStrategyConfig.USE_PIC_DEFAULT, config.getMaxRetryWaitTimeMs());
        assertEquals(ExponentialBackoffRetryStrategyConfig.USE_PIC_DEFAULT, config.getRetryFactorTimeMs());
        assertEquals(ExponentialBackoffRetryStrategyConfig.USE_PIC_DEFAULT, config.getMinTimeToResetRetryStateMs());
        assertNull(config.getJitterType());
        assertEquals(ExponentialBackoffRetryStrategyConfig.USE_PIC_DEFAULT, config.getJitterFactor());
    }

    @Test
    public void givenValidCustomValues_whenBuildingConfig_thenAllFieldsAreSetCorrectly() {
        // Given
        final long maxRetryCount = 5L;
        final long maxRetryWaitTimeMs = 15000L;
        final long retryFactorTimeMs = 500L;
        final long minTimeToResetRetryStateMs = 100000L;
        final long jitterFactor = 300L;

        // When
        final ExponentialBackoffRetryStrategyConfig config =
                ExponentialBackoffRetryStrategyConfig.ExponentialBackoffRetryStrategyConfigBuilder.with()
                        .maxRetryCount(maxRetryCount)
                        .maxRetryWaitTimeMs(maxRetryWaitTimeMs)
                        .retryFactorTimeMs(retryFactorTimeMs)
                        .minTimeToResetRetryStateMs(minTimeToResetRetryStateMs)
                        .fixedJitter()
                        .jitterFactorMillis(jitterFactor)
                        .build();

        // Then
        assertEquals(maxRetryCount, config.getMaxRetryCount());
        assertEquals(maxRetryWaitTimeMs, config.getMaxRetryWaitTimeMs());
        assertEquals(retryFactorTimeMs, config.getRetryFactorTimeMs());
        assertEquals(minTimeToResetRetryStateMs, config.getMinTimeToResetRetryStateMs());
        assertEquals(FIXED_JITTER, config.getJitterType());
        assertEquals(jitterFactor, config.getJitterFactor());
    }

    @Test
    public void givenFullJitterType_whenBuildingConfig_thenJitterTypeIsSetCorrectly() {
        // Given/When
        final ExponentialBackoffRetryStrategyConfig config =
                ExponentialBackoffRetryStrategyConfig.ExponentialBackoffRetryStrategyConfigBuilder.with()
                        .maxRetryCount(1L)
                        .maxRetryWaitTimeMs(MIN_KVS_MAX_WAIT_TIME_MILLISECONDS)
                        .retryFactorTimeMs(MIN_KVS_RETRY_TIME_FACTOR_MILLISECONDS)
                        .minTimeToResetRetryStateMs(MIN_KVS_MIN_TIME_TO_RESET_RETRY_STATE_MILLISECONDS)
                        .fullJitter()
                        .build();

        // Then
        assertEquals(ExponentialBackoffRetryStrategyConfig.JitterType.FULL_JITTER, config.getJitterType());
        assertEquals(ExponentialBackoffRetryStrategyConfig.USE_PIC_DEFAULT, config.getJitterFactor());
    }

    @Test
    public void givenNoJitterType_whenBuildingConfig_thenJitterTypeIsSetCorrectly() {
        // Given/When
        final ExponentialBackoffRetryStrategyConfig config =
                ExponentialBackoffRetryStrategyConfig.ExponentialBackoffRetryStrategyConfigBuilder.with()
                        .maxRetryCount(1L)
                        .maxRetryWaitTimeMs(MIN_KVS_MAX_WAIT_TIME_MILLISECONDS)
                        .retryFactorTimeMs(MIN_KVS_RETRY_TIME_FACTOR_MILLISECONDS)
                        .minTimeToResetRetryStateMs(MIN_KVS_MIN_TIME_TO_RESET_RETRY_STATE_MILLISECONDS)
                        .noJitter()
                        .build();

        // Then
        assertEquals(ExponentialBackoffRetryStrategyConfig.JitterType.NO_JITTER, config.getJitterType());
        assertEquals(ExponentialBackoffRetryStrategyConfig.USE_PIC_DEFAULT, config.getJitterFactor());
    }

    @Test
    public void givenNegativeMaxRetryCount_whenBuilding_thenThrowsIllegalArgumentException() {
        // Given
        final long negativeMaxRetryCount = -1L;

        try {
            // When
            ExponentialBackoffRetryStrategyConfig.ExponentialBackoffRetryStrategyConfigBuilder.with()
                    .maxRetryCount(negativeMaxRetryCount);

            // Then
            fail("Expected IllegalArgumentException for negative maxRetryCount");
        } catch (final IllegalArgumentException e) {
            assertEquals("maxRetryCount cannot be negative", e.getMessage());
        }
    }

    @Test
    public void givenMaxRetryWaitTimeBelowMinimum_whenBuilding_thenThrowsIllegalArgumentException() {
        // Given
        final long belowMinimumWaitTime = MIN_KVS_MAX_WAIT_TIME_MILLISECONDS - 1;

        try {
            // When
            ExponentialBackoffRetryStrategyConfig.ExponentialBackoffRetryStrategyConfigBuilder.with()
                    .maxRetryCount(1L)
                    .maxRetryWaitTimeMs(belowMinimumWaitTime);

            // Then
            fail("Expected IllegalArgumentException for maxRetryWaitTimeMs below minimum");
        } catch (final IllegalArgumentException e) {
            // Happy path
            log.trace("Received expected exception", e);
        }
    }

    @Test
    public void givenMaxRetryWaitTimeAboveLimit_whenBuilding_thenThrowsIllegalArgumentException() {
        // Given
        final long aboveLimitWaitTime = LIMIT_KVS_MAX_WAIT_TIME_MILLISECONDS + 1;

        try {
            // When
            ExponentialBackoffRetryStrategyConfig.ExponentialBackoffRetryStrategyConfigBuilder.with()
                    .maxRetryCount(1L)
                    .maxRetryWaitTimeMs(aboveLimitWaitTime);

            // Then
            fail("Expected IllegalArgumentException for maxRetryWaitTimeMs above limit");
        } catch (final IllegalArgumentException e) {
            // Happy path
            log.trace("Received expected exception", e);
        }
    }

    @Test
    public void givenRetryFactorTimeBelowMinimum_whenBuilding_thenThrowsIllegalArgumentException() {
        // Given
        final long belowMinimumFactorTime = MIN_KVS_RETRY_TIME_FACTOR_MILLISECONDS - 1;

        try {
            // When
            ExponentialBackoffRetryStrategyConfig.ExponentialBackoffRetryStrategyConfigBuilder.with()
                    .maxRetryCount(1L)
                    .maxRetryWaitTimeMs(MIN_KVS_MAX_WAIT_TIME_MILLISECONDS)
                    .retryFactorTimeMs(belowMinimumFactorTime);

            // Then
            fail("Expected IllegalArgumentException for retryFactorTimeMs below minimum");
        } catch (final IllegalArgumentException e) {
            // Happy path
            log.trace("Received expected exception", e);
        }
    }

    @Test
    public void givenRetryFactorTimeAboveLimit_whenBuilding_thenThrowsIllegalArgumentException() {
        // Given
        final long aboveLimitFactorTime = LIMIT_KVS_RETRY_TIME_FACTOR_MILLISECONDS + 1;

        try {
            // When
            ExponentialBackoffRetryStrategyConfig.ExponentialBackoffRetryStrategyConfigBuilder.with()
                    .maxRetryCount(1L)
                    .maxRetryWaitTimeMs(MIN_KVS_MAX_WAIT_TIME_MILLISECONDS)
                    .retryFactorTimeMs(aboveLimitFactorTime);

            // Then
            fail("Expected IllegalArgumentException for retryFactorTimeMs above limit");
        } catch (final IllegalArgumentException e) {
            // Happy path
            log.trace("Received expected exception", e);
        }
    }

    @Test
    public void givenMinTimeToResetRetryStateBelowMinimum_whenBuilding_thenThrowsIllegalArgumentException() {
        // Given
        final long belowMinimumResetTime = MIN_KVS_MIN_TIME_TO_RESET_RETRY_STATE_MILLISECONDS - 1;

        try {
            // When
            ExponentialBackoffRetryStrategyConfig.ExponentialBackoffRetryStrategyConfigBuilder.with()
                    .maxRetryCount(1L)
                    .maxRetryWaitTimeMs(MIN_KVS_MAX_WAIT_TIME_MILLISECONDS)
                    .retryFactorTimeMs(MIN_KVS_RETRY_TIME_FACTOR_MILLISECONDS)
                    .minTimeToResetRetryStateMs(belowMinimumResetTime);

            // Then
            fail("Expected IllegalArgumentException for minTimeToResetRetryStateMs below minimum");
        } catch (final IllegalArgumentException e) {
            // Happy path
            log.trace("Received expected exception", e);
        }
    }

    @Test
    public void givenMinTimeToResetRetryStateAboveLimit_whenBuilding_thenThrowsIllegalArgumentException() {
        // Given
        final long aboveLimitResetTime = LIMIT_KVS_MIN_TIME_TO_RESET_RETRY_STATE_MILLISECONDS + 1;

        try {
            // When
            ExponentialBackoffRetryStrategyConfig.ExponentialBackoffRetryStrategyConfigBuilder.with()
                    .maxRetryCount(1L)
                    .maxRetryWaitTimeMs(MIN_KVS_MAX_WAIT_TIME_MILLISECONDS)
                    .retryFactorTimeMs(MIN_KVS_RETRY_TIME_FACTOR_MILLISECONDS)
                    .minTimeToResetRetryStateMs(aboveLimitResetTime);

            // Then
            fail("Expected IllegalArgumentException for minTimeToResetRetryStateMs above limit");
        } catch (final IllegalArgumentException e) {
            // Happy path
            log.trace("Received expected exception", e);
        }
    }

    @Test
    public void givenJitterFactorBelowMinimum_whenBuilding_thenThrowsIllegalArgumentException() {
        // Given
        final long belowMinimumJitterFactor = MIN_KVS_JITTER_FACTOR_MILLISECONDS - 1;

        try {
            // When
            ExponentialBackoffRetryStrategyConfig.ExponentialBackoffRetryStrategyConfigBuilder.with()
                    .maxRetryCount(1L)
                    .maxRetryWaitTimeMs(MIN_KVS_MAX_WAIT_TIME_MILLISECONDS)
                    .retryFactorTimeMs(MIN_KVS_RETRY_TIME_FACTOR_MILLISECONDS)
                    .minTimeToResetRetryStateMs(MIN_KVS_MIN_TIME_TO_RESET_RETRY_STATE_MILLISECONDS)
                    .fixedJitter()
                    .jitterFactorMillis(belowMinimumJitterFactor);

            // Then
            fail("Expected IllegalArgumentException for jitterFactor below minimum");
        } catch (final IllegalArgumentException e) {
            // Happy path
            log.trace("Received expected exception", e);
        }
    }

    @Test
    public void givenJitterFactorAboveLimit_whenBuilding_thenThrowsIllegalArgumentException() {
        // Given
        final long aboveLimitJitterFactor = LIMIT_KVS_JITTER_FACTOR_MILLISECONDS + 1;

        try {
            // When
            ExponentialBackoffRetryStrategyConfig.ExponentialBackoffRetryStrategyConfigBuilder.with()
                    .maxRetryCount(1L)
                    .maxRetryWaitTimeMs(MIN_KVS_MAX_WAIT_TIME_MILLISECONDS)
                    .retryFactorTimeMs(MIN_KVS_RETRY_TIME_FACTOR_MILLISECONDS)
                    .minTimeToResetRetryStateMs(MIN_KVS_MIN_TIME_TO_RESET_RETRY_STATE_MILLISECONDS)
                    .fixedJitter()
                    .jitterFactorMillis(aboveLimitJitterFactor);

            // Then
            fail("Expected IllegalArgumentException for jitterFactor above limit");
        } catch (final IllegalArgumentException e) {
            // Happy path
            log.trace("Received expected exception", e);
        }
    }

    @Test
    public void givenBoundaryValues_whenBuilding_thenConfigIsCreatedSuccessfully() {
        // Given - Test minimum boundary values
        final ExponentialBackoffRetryStrategyConfig minConfig =
                ExponentialBackoffRetryStrategyConfig.ExponentialBackoffRetryStrategyConfigBuilder.with()
                        .maxRetryCount(0L) // Minimum allowed
                        .maxRetryWaitTimeMs(MIN_KVS_MAX_WAIT_TIME_MILLISECONDS)
                        .retryFactorTimeMs(MIN_KVS_RETRY_TIME_FACTOR_MILLISECONDS)
                        .minTimeToResetRetryStateMs(MIN_KVS_MIN_TIME_TO_RESET_RETRY_STATE_MILLISECONDS)
                        .fixedJitter()
                        .jitterFactorMillis(MIN_KVS_JITTER_FACTOR_MILLISECONDS)
                        .build();

        // When - Test maximum boundary values
        final ExponentialBackoffRetryStrategyConfig maxConfig =
                ExponentialBackoffRetryStrategyConfig.ExponentialBackoffRetryStrategyConfigBuilder.with()
                        .maxRetryCount(Long.MAX_VALUE) // Any positive value allowed
                        .maxRetryWaitTimeMs(LIMIT_KVS_MAX_WAIT_TIME_MILLISECONDS)
                        .retryFactorTimeMs(LIMIT_KVS_RETRY_TIME_FACTOR_MILLISECONDS)
                        .minTimeToResetRetryStateMs(LIMIT_KVS_MIN_TIME_TO_RESET_RETRY_STATE_MILLISECONDS)
                        .fixedJitter()
                        .jitterFactorMillis(LIMIT_KVS_JITTER_FACTOR_MILLISECONDS)
                        .build();

        // Then
        assertEquals(0L, minConfig.getMaxRetryCount());
        assertEquals(MIN_KVS_MAX_WAIT_TIME_MILLISECONDS, minConfig.getMaxRetryWaitTimeMs());
        assertEquals(MIN_KVS_RETRY_TIME_FACTOR_MILLISECONDS, minConfig.getRetryFactorTimeMs());
        assertEquals(MIN_KVS_MIN_TIME_TO_RESET_RETRY_STATE_MILLISECONDS, minConfig.getMinTimeToResetRetryStateMs());
        assertEquals(MIN_KVS_JITTER_FACTOR_MILLISECONDS, minConfig.getJitterFactor());

        assertEquals(Long.MAX_VALUE, maxConfig.getMaxRetryCount());
        assertEquals(LIMIT_KVS_MAX_WAIT_TIME_MILLISECONDS, maxConfig.getMaxRetryWaitTimeMs());
        assertEquals(LIMIT_KVS_RETRY_TIME_FACTOR_MILLISECONDS, maxConfig.getRetryFactorTimeMs());
        assertEquals(LIMIT_KVS_MIN_TIME_TO_RESET_RETRY_STATE_MILLISECONDS, maxConfig.getMinTimeToResetRetryStateMs());
        assertEquals(LIMIT_KVS_JITTER_FACTOR_MILLISECONDS, maxConfig.getJitterFactor());
    }

    @Test
    public void givenUsePicDefaultValues_whenBuilding_thenValidationIsSkipped() {
        // Given/When - USE_PIC_DEFAULT should bypass validation
        final ExponentialBackoffRetryStrategyConfig config =
                ExponentialBackoffRetryStrategyConfig.ExponentialBackoffRetryStrategyConfigBuilder.with()
                        .maxRetryCount(ExponentialBackoffRetryStrategyConfig.USE_PIC_DEFAULT)
                        .maxRetryWaitTimeMs(ExponentialBackoffRetryStrategyConfig.USE_PIC_DEFAULT)
                        .retryFactorTimeMs(ExponentialBackoffRetryStrategyConfig.USE_PIC_DEFAULT)
                        .minTimeToResetRetryStateMs(ExponentialBackoffRetryStrategyConfig.USE_PIC_DEFAULT)
                        .fixedJitter()
                        .jitterFactorMillis(ExponentialBackoffRetryStrategyConfig.USE_PIC_DEFAULT)
                        .build();

        // Then
        assertEquals(ExponentialBackoffRetryStrategyConfig.USE_PIC_DEFAULT, config.getMaxRetryCount());
        assertEquals(ExponentialBackoffRetryStrategyConfig.USE_PIC_DEFAULT, config.getMaxRetryWaitTimeMs());
        assertEquals(ExponentialBackoffRetryStrategyConfig.USE_PIC_DEFAULT, config.getRetryFactorTimeMs());
        assertEquals(ExponentialBackoffRetryStrategyConfig.USE_PIC_DEFAULT, config.getMinTimeToResetRetryStateMs());
        assertEquals(ExponentialBackoffRetryStrategyConfig.USE_PIC_DEFAULT, config.getJitterFactor());
    }

    @Test
    public void givenNullBuilder_whenCreatingConfig_thenThrowsIllegalArgumentException() {
        try {
            // Given/When
            new ExponentialBackoffRetryStrategyConfig(null);

            // Then
            fail("Expected IllegalArgumentException for null builder");
        } catch (final IllegalArgumentException e) {
            // Happy path
            log.trace("Received expected exception", e);
        }
    }

    @Test
    public void givenJitterTypeValues_whenGettingJitterTypeValue_thenReturnsCorrectIntegerValues() {
        // Given
        final ExponentialBackoffRetryStrategyConfig noJitterConfig =
                ExponentialBackoffRetryStrategyConfig.ExponentialBackoffRetryStrategyConfigBuilder.with()
                        .maxRetryCount(1L)
                        .maxRetryWaitTimeMs(MIN_KVS_MAX_WAIT_TIME_MILLISECONDS)
                        .retryFactorTimeMs(MIN_KVS_RETRY_TIME_FACTOR_MILLISECONDS)
                        .minTimeToResetRetryStateMs(MIN_KVS_MIN_TIME_TO_RESET_RETRY_STATE_MILLISECONDS)
                        .noJitter()
                        .build();

        final ExponentialBackoffRetryStrategyConfig fullJitterConfig =
                ExponentialBackoffRetryStrategyConfig.ExponentialBackoffRetryStrategyConfigBuilder.with()
                        .maxRetryCount(1L)
                        .maxRetryWaitTimeMs(MIN_KVS_MAX_WAIT_TIME_MILLISECONDS)
                        .retryFactorTimeMs(MIN_KVS_RETRY_TIME_FACTOR_MILLISECONDS)
                        .minTimeToResetRetryStateMs(MIN_KVS_MIN_TIME_TO_RESET_RETRY_STATE_MILLISECONDS)
                        .fullJitter()
                        .build();

        final ExponentialBackoffRetryStrategyConfig fixedJitterConfig =
                ExponentialBackoffRetryStrategyConfig.ExponentialBackoffRetryStrategyConfigBuilder.with()
                        .maxRetryCount(1L)
                        .maxRetryWaitTimeMs(MIN_KVS_MAX_WAIT_TIME_MILLISECONDS)
                        .retryFactorTimeMs(MIN_KVS_RETRY_TIME_FACTOR_MILLISECONDS)
                        .minTimeToResetRetryStateMs(MIN_KVS_MIN_TIME_TO_RESET_RETRY_STATE_MILLISECONDS)
                        .fixedJitter()
                        .jitterFactorMillis(MIN_KVS_JITTER_FACTOR_MILLISECONDS)
                        .build();

        final ExponentialBackoffRetryStrategyConfig defaultsConfig =
                ExponentialBackoffRetryStrategyConfig.ExponentialBackoffRetryStrategyConfigBuilder.defaults();

        // When/Then
        assertEquals(ExponentialBackoffRetryStrategyConfig.JitterType.NO_JITTER.getValue(),
                noJitterConfig.getJitterTypeValue());
        assertEquals(ExponentialBackoffRetryStrategyConfig.JitterType.FULL_JITTER.getValue(),
                fullJitterConfig.getJitterTypeValue());
        assertEquals(FIXED_JITTER.getValue(),
                fixedJitterConfig.getJitterTypeValue());
        assertEquals((int) ExponentialBackoffRetryStrategyConfig.USE_PIC_DEFAULT,
                defaultsConfig.getJitterTypeValue());
    }

    @Test
    public void givenJitterTypeEnumValues_whenCallingFromValue_thenReturnsCorrectEnumValues() {
        // Given/When/Then
        assertEquals(ExponentialBackoffRetryStrategyConfig.JitterType.FULL_JITTER,
                ExponentialBackoffRetryStrategyConfig.JitterType.fromValue(0x01));
        assertEquals(FIXED_JITTER,
                ExponentialBackoffRetryStrategyConfig.JitterType.fromValue(0x02));
        assertEquals(ExponentialBackoffRetryStrategyConfig.JitterType.NO_JITTER,
                ExponentialBackoffRetryStrategyConfig.JitterType.fromValue(0x03));
    }

    @Test
    public void givenInvalidJitterTypeValue_whenCallingFromValue_thenThrowsIllegalArgumentException() {
        // Given
        final int invalidValue = 999;

        try {
            // When
            ExponentialBackoffRetryStrategyConfig.JitterType.fromValue(invalidValue);

            // Then
            fail("Expected IllegalArgumentException for invalid jitter type value");
        } catch (final IllegalArgumentException e) {
            // Happy path
            log.trace("Received expected exception", e);
        }
    }

    @Test
    public void givenConfigWithAllValues_whenCallingToString_thenReturnsFormattedString() {
        // Given
        final long maxRetryCount = 9L;
        final long maxRetryWaitTimeMs = 15000L;
        final long retryFactorTimeMs = 501L;
        final long minTimeToResetRetryStateMs = 100000L;
        final long jitterFactor = 300L;

        final ExponentialBackoffRetryStrategyConfig config =
                ExponentialBackoffRetryStrategyConfig.ExponentialBackoffRetryStrategyConfigBuilder.with()
                        .maxRetryCount(maxRetryCount)
                        .maxRetryWaitTimeMs(maxRetryWaitTimeMs)
                        .retryFactorTimeMs(retryFactorTimeMs)
                        .minTimeToResetRetryStateMs(minTimeToResetRetryStateMs)
                        .fixedJitter()
                        .jitterFactorMillis(jitterFactor)
                        .build();

        // When
        final String result = config.toString();

        // Then
        assertTrue("It should contain the class name ExponentialBackoffRetryStrategyConfig", result.contains("ExponentialBackoffRetryStrategyConfig"));
        assertTrue("It should contain maxRetryCount", result.contains("maxRetryCount"));
        assertTrue("It should contain maxRetryWaitTime", result.contains("maxRetryWaitTime"));
        assertTrue("It should contain retryFactorTime", result.contains("retryFactorTime"));
        assertTrue("It should contain minTimeToResetRetryStateMs", result.contains("minTimeToResetRetryStateMs"));
        assertTrue("It should contain jitterType", result.contains("jitterType"));
        assertTrue("It should contain jitterFactor", result.contains("jitterFactor"));

        assertTrue("It should contain maxRetryCount value", result.contains(Long.toString(maxRetryCount)));
        assertTrue("It should contain maxRetryWaitTime value", result.contains(Long.toString(maxRetryWaitTimeMs)));
        assertTrue("It should contain retryFactorTime value", result.contains(Long.toString(retryFactorTimeMs)));
        assertTrue("It should contain minTimeToResetRetryStateMs value", result.contains(Long.toString(minTimeToResetRetryStateMs)));
        assertTrue("It should contain jitterType value", result.contains(FIXED_JITTER.toString()));
        assertTrue("It should contain jitterFactor value", result.contains(Long.toString(jitterFactor)));
    }

    @Test
    public void givenConfigWithPicDefaults_whenCallingToString_thenShowsPicDefaultLabels() {
        // Given
        final int NUM_FIELDS = 6;
        final ExponentialBackoffRetryStrategyConfig config =
                ExponentialBackoffRetryStrategyConfig.ExponentialBackoffRetryStrategyConfigBuilder.defaults();

        // When
        final String result = config.toString();

        // Then
        final int occurrancesOfDefaultString = StringUtils.countMatches(result, "PIC_DEFAULT");
        assertEquals("It should say PIC_DEFAULT " + NUM_FIELDS + " times, but only found it " +
                occurrancesOfDefaultString + " times in " + result, NUM_FIELDS, occurrancesOfDefaultString);
    }
}
