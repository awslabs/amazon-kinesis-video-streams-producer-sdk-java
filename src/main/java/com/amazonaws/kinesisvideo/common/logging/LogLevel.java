package com.amazonaws.kinesisvideo.common.logging;

/**
 * Specifies the severity level from lowest to highest.
 * <p>
 * This is modeled after Android log values.
 *
 */
public enum LogLevel {
    /**
     * Log level enums
     */
    VERBOSE(2), DEBUG(3), INFO(4), WARN(5), ERROR(6), ASSERT(7);

    private static final LogLevel[] LOG_LEVELS = new LogLevel[] {
            VERBOSE,    // 0
            VERBOSE,    // 1
            VERBOSE,    // 2
            DEBUG,      // 3
            INFO,       // 4
            WARN,       // 5
            ERROR,      // 6
            ASSERT      // 7
    };

    private final int mLogLevel;

    /**
     * Returns a LogLevel from an integer representation
     * @param logLevel Integer corresponding to the log level
     * @return LogLevel enum
     */
    public static LogLevel fromInt(final int logLevel) {
        if (logLevel < 0 || logLevel >= LOG_LEVELS.length) {
            return VERBOSE;
        } else {
            return LOG_LEVELS[logLevel];
        }
    }

    /**
     * Creates a new log level enum
     *
     * @param logLevel
     *         Log level int
     */
    LogLevel(final int logLevel) {
        mLogLevel = logLevel;
    }

    /**
     * Returns the int log level
     *
     * @return Log level as an int
     */
    public int getLogLevel() {
        return mLogLevel;
    }
}