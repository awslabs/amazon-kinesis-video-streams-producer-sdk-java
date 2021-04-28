package com.amazonaws.kinesisvideo.producer;

import javax.annotation.Nonnull;
import java.util.Date;

/**
 * Helper class for the Kinesis Video producer SDK time.
 *
 * NOTE: Time in the producer SDK is defined as 100ns
 *
 */
public class Time {
    /**
     * Hundreds of nanos in a units
     */
    public static final long NANOS_IN_A_TIME_UNIT = 100;
    public static final long HUNDREDS_OF_NANOS_IN_A_MICROSECOND = 1000 / NANOS_IN_A_TIME_UNIT;
    public static final long HUNDREDS_OF_NANOS_IN_A_MILLISECOND = 1000 * HUNDREDS_OF_NANOS_IN_A_MICROSECOND;
    public static final long HUNDREDS_OF_NANOS_IN_A_SECOND = 1000 * HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
    public static final long HUNDREDS_OF_NANOS_IN_A_MINUTE = 60 * HUNDREDS_OF_NANOS_IN_A_SECOND;
    public static final long HUNDREDS_OF_NANOS_IN_AN_HOUR = 60 * HUNDREDS_OF_NANOS_IN_A_MINUTE;
    public static final long NANOS_IN_A_MILLISECOND = 1000000;

    /**
     * Gets the current time in producer time units
     * @return Current system time in Kinesis Video time units
     */
    public static final long getCurrentTime() {
	return System.currentTimeMillis() * HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
    };

    /**
     * Converts {@link Date} to Kinesis Video time
     * @param date - Java date object
     * @return Time in Kinesis Video time units
     */
    public static final long getTime(final @Nonnull Date date) {
        return date.getTime() * HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
    }
}
