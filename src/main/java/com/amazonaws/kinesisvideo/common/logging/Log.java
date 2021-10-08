package com.amazonaws.kinesisvideo.common.logging;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;

import java.io.File;

/**
 *
 * Kinesis Video Streams common codebase.
 *
 * Logging class for Kinesis Video Streams codebase. The underlying logger can be different for
 * different platforms - i.e. LogCat for Android.
 *
 */
public class Log {
    /**
     * Default tag for the logs
     */
    private static final String BASE_TAG = "KinesisVideoStreams";

    /**
     * Used to delimit the tag from the message
     */
    private static final String TAG_DELIMITER = ".";

    /**
     * Used to delimit the message pieces
     */
    private static final String MESSAGE_DELIMITER = ": ";

    /**
     * Current tag value
     */
    private static String mTag;

    /**
     * Logger for log4j2
     */
    private static Logger mLogger;

    /**
     * LoggerContext for log4j2
     */
    private static LoggerContext mLoggerContext;

    /**
     * ConfigFile(XML) for log4j2
     */
    private static File mConfigFile;

    /**
     * Logger configuration
     */
    private static Configuration mConfiguration;

    /**
     * Log object (Static to maintain a single instance of the class throughout
     */
    private static Log mLog;

    /**
     * Creates a new instance of the class
     *
     * @param filePath path to the log4j2 config file
     */
    private Log(final @Nonnull String filePath) {
        mLogger = LogManager.getLogger();
        mLoggerContext = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
        mConfigFile = new File(filePath);
        mLoggerContext.setConfigLocation(mConfigFile.toURI());
        mTag = BASE_TAG;
        setCurrentLogLevel(Level.INFO);
    }

    /**
     * Obtains an existing instance of the class if it exists, else creates a new instance
     *
     * @param tag for the log (can be set to null if it does not need an update)
     */
    public static Log getLogInstance(@Nullable String tag) {
        if (mLog == null) {
            mLog = new Log("log4j2.xml");
        }
        if (tag != null) {
            mTag = tag;
        }
        return mLog;
    }

    /**
     * Sets the current logging level
     *
     * @param logLevel Log level to filter
     */
    public void setCurrentLogLevel(final Level logLevel) {
        mConfiguration = mLoggerContext.getConfiguration();
        LoggerConfig loggerConfig = mConfiguration.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
        loggerConfig.setLevel(logLevel);
        mLoggerContext.updateLoggers();
    }

    /**
     * Sets the tag with the package name prefix
     */
    public void setPackagePrefix() {
        final StackTraceElement[] stack = new Throwable().getStackTrace();
        final String[] parts = stack[1].getClassName().split("\\.", 0);
        final String packageName = parts[parts.length - 1];
        mTag = String.format("%s%s%s", BASE_TAG, TAG_DELIMITER, packageName);
    }

    /**
     * Verbose logging
     *
     * @param message Message to log
     */
    public void verbose(final String message) {
        mLogger.log(Level.getLevel("VERBOSE"), "{}(): {}", mTag, message);
    }

    /**
     * Verbose level logging in a parameterized string
     *
     * @param template Parameterized string
     * @param args     Arguments
     */
    public void verbose(final String template, final Object... args) {
        mLogger.log(Level.getLevel("VERBOSE"), "{}(): {}", mTag, String.format(template, args));
    }

    /**
     * Debug logging
     *
     * @param message Message to log
     */
    public void debug(final String message) {
        mLogger.log(Level.DEBUG, "{}(): {}", mTag, message);
    }

    /**
     * Debug level logging in a parameterized string
     *
     * @param template Parameterized string
     * @param args     Arguments
     */
    public void debug(final String template, final Object... args) {
        mLogger.log(Level.DEBUG, "{}(): {}", mTag, String.format(template, args));
    }

    /**
     * Information level logging
     *
     * @param message Message to log
     */
    public void info(final String message) {
        mLogger.log(Level.INFO, "{}(): {}", mTag, message);
    }

    /**
     * Information level logging in a parameterized string
     *
     * @param template Parameterized string
     * @param args     Arguments
     */
    public void info(final String template, final Object... args) {
        mLogger.log(Level.INFO, "{}(): {}", mTag, String.format(template, args));
    }

    /**
     * Warning level logging
     *
     * @param message Message to log
     */
    public void warn(final String message) {
        mLogger.log(Level.WARN, "{}(): {}", mTag, message);
    }

    /**
     * Warning level logging in a parameterized string
     *
     * @param template Parameterized string
     * @param args     Arguments
     */
    public void warn(final String template, final Object... args) {
        mLogger.log(Level.WARN, "{}(): {}", mTag, String.format(template, args));
    }

    /**
     * Error level logging
     *
     * @param message Message to log
     */
    public void error(final String message) {
        mLogger.log(Level.ERROR, "{}(): {}", mTag, message);
    }

    /**
     * Error level logging in a parameterized string
     *
     * @param template Parameterized string
     * @param args     Arguments
     */
    public void error(final String template, final Object... args) {
        mLogger.log(Level.ERROR, "{}(): {}", mTag, String.format(template, args));
    }

    /**
     * Assert level logging
     *
     * @param message Message to log
     */
    public void assrt(final String message) {
        mLogger.log(Level.getLevel("ASSERT"), "{}(): {}", mTag, message);
    }

    /**
     * Assert level logging in a parameterized string
     *
     * @param template Parameterized string
     * @param args     Arguments
     */
    public void assrt(final String template, final Object... args) {
        mLogger.log(Level.getLevel("ASSERT"), "{}(): {}", mTag, String.format(template, args));
    }

    /**
     * Logs an exception
     *
     * @param e Exception to log
     */
    public void exception(final Throwable e) {
        mLogger.log(Level.getLevel("EXCEPTION"), e.getClass().getSimpleName() + MESSAGE_DELIMITER + e.getMessage(), e);
    }

    /**
     * Logs an exception
     *
     * @param e Exception to log
     * @param template Parameterized string
     * @param args     Arguments
     */
    public void exception(final Throwable e, final String template, final Object... args) {
        mLogger.log(Level.getLevel("EXCEPTION"), e.getClass().getSimpleName() + MESSAGE_DELIMITER + String.format(template, args) + MESSAGE_DELIMITER + e.getMessage(), e);
    }
}
