package com.amazonaws.kinesisvideo.common;

import static org.junit.Assert.*;

import javax.annotation.Nonnull;

import org.junit.Before;
import org.junit.Test;

import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.common.logging.Log;
import com.amazonaws.kinesisvideo.common.logging.LogLevel;
import com.amazonaws.kinesisvideo.common.logging.OutputChannel;

/**
 * Log class tests
 */
public class LogTest {
    private static final String TEST_DEFAULT_TAG_NAME = "KinesisVideoStreams";
    private static final String TEST_LOG_MESSAGE = "Test Message";
    private static final String TEST_PARAMETERIZED_LOG_MESSAGE = "Test %s Message %d";
    private static final String TEST_LOGGED_MESSAGE = "Test KinesisVideoStreams Message ";
    private static final int TEST_UNDEFINED_LOG_LEVEL = 100;

    private int mLogLevel;
    private String mTag;
    private String mMessage;
    private Log mLog;

    private final OutputChannel mOutputChannel = new OutputChannel() {
        @Override
        public void print(final int level, @Nonnull final String tag, @Nonnull final String message) {
            mLogLevel = level;
            mTag = tag;
            mMessage = message;
        }
    };

    @Before
    public void setupLog() {
        mLogLevel = TEST_UNDEFINED_LOG_LEVEL;
        mTag = null;
        mMessage = null;

        mLog = new Log(mOutputChannel);
    }

    @Test
    public void basicLogTest() {
        mLog.log(LogLevel.INFO, TEST_LOG_MESSAGE + 1);
        assertEquals(TEST_LOG_MESSAGE + 1, mMessage);
        assertEquals(TEST_DEFAULT_TAG_NAME, mTag);
        assertEquals(LogLevel.INFO.getLogLevel(), mLogLevel);

        mLog.log(LogLevel.INFO, TEST_LOG_MESSAGE + 2);
        assertEquals(TEST_LOG_MESSAGE + 2, mMessage);
        assertEquals(LogLevel.INFO.getLogLevel(), mLogLevel);

        mLog.log(LogLevel.WARN, TEST_LOG_MESSAGE + 3);
        assertEquals(TEST_LOG_MESSAGE + 3, mMessage);
        assertEquals(LogLevel.WARN.getLogLevel(), mLogLevel);

        mLog.log(LogLevel.ERROR, TEST_LOG_MESSAGE + 4);
        assertEquals(TEST_LOG_MESSAGE + 4, mMessage);
        assertEquals(LogLevel.ERROR.getLogLevel(), mLogLevel);

        mLog.log(LogLevel.ASSERT, TEST_LOG_MESSAGE + 5);
        assertEquals(TEST_LOG_MESSAGE + 5, mMessage);
        assertEquals(LogLevel.ASSERT.getLogLevel(), mLogLevel);

        // Ensure we are not logging lower pri messages
        mLog.log(LogLevel.DEBUG, TEST_LOG_MESSAGE + 6);
        assertEquals(TEST_LOG_MESSAGE + 5, mMessage);
        assertEquals(LogLevel.ASSERT.getLogLevel(), mLogLevel);

        mLog.log(LogLevel.VERBOSE, TEST_LOG_MESSAGE + 7);
        assertEquals(TEST_LOG_MESSAGE + 5, mMessage);
        assertEquals(LogLevel.ASSERT.getLogLevel(), mLogLevel);

        // Set the new level to debug
        mLog.setCurrentLogLevel(LogLevel.DEBUG);

        // Ensure debug is logged and verbose isn't
        mLog.log(LogLevel.DEBUG, TEST_LOG_MESSAGE + 6);
        assertEquals(TEST_LOG_MESSAGE + 6, mMessage);
        assertEquals(LogLevel.DEBUG.getLogLevel(), mLogLevel);

        mLog.log(LogLevel.VERBOSE, TEST_LOG_MESSAGE + 7);
        assertEquals(TEST_LOG_MESSAGE + 6, mMessage);
        assertEquals(LogLevel.DEBUG.getLogLevel(), mLogLevel);
    }

    @Test
    public void basicParameterizedLogTest() {
        mLog.log(LogLevel.INFO, TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 1);
        assertEquals(TEST_LOGGED_MESSAGE + 1, mMessage);
        assertEquals(TEST_DEFAULT_TAG_NAME, mTag);
        assertEquals(LogLevel.INFO.getLogLevel(), mLogLevel);

        mLog.log(LogLevel.INFO, TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 2);
        assertEquals(TEST_LOGGED_MESSAGE + 2, mMessage);
        assertEquals(LogLevel.INFO.getLogLevel(), mLogLevel);

        mLog.log(LogLevel.WARN, TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 3);
        assertEquals(TEST_LOGGED_MESSAGE + 3, mMessage);
        assertEquals(LogLevel.WARN.getLogLevel(), mLogLevel);

        mLog.log(LogLevel.ERROR, TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 4);
        assertEquals(TEST_LOGGED_MESSAGE + 4, mMessage);
        assertEquals(LogLevel.ERROR.getLogLevel(), mLogLevel);

        mLog.log(LogLevel.ASSERT, TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 5);
        assertEquals(TEST_LOGGED_MESSAGE + 5, mMessage);
        assertEquals(LogLevel.ASSERT.getLogLevel(), mLogLevel);

        // Ensure we are not logging lower pri messages
        mLog.log(LogLevel.DEBUG, TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 6);
        assertEquals(TEST_LOGGED_MESSAGE + 5, mMessage);
        assertEquals(LogLevel.ASSERT.getLogLevel(), mLogLevel);

        mLog.log(LogLevel.VERBOSE, TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 7);
        assertEquals(TEST_LOGGED_MESSAGE + 5, mMessage);
        assertEquals(LogLevel.ASSERT.getLogLevel(), mLogLevel);

        // Set the new level to debug
        mLog.setCurrentLogLevel(LogLevel.DEBUG);

        // Ensure debug is logged and verbose isn't
        mLog.log(LogLevel.DEBUG, TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 6);
        assertEquals(TEST_LOGGED_MESSAGE + 6, mMessage);
        assertEquals(LogLevel.DEBUG.getLogLevel(), mLogLevel);

        mLog.log(LogLevel.VERBOSE, TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 7);
        assertEquals(TEST_LOGGED_MESSAGE + 6, mMessage);
        assertEquals(LogLevel.DEBUG.getLogLevel(), mLogLevel);
    }

    @Test
    public void basicNamedLogTest() {
        mLog.info(TEST_LOG_MESSAGE + 1);
        assertEquals(TEST_LOG_MESSAGE + 1, mMessage);
        assertEquals(TEST_DEFAULT_TAG_NAME, mTag);
        assertEquals(LogLevel.INFO.getLogLevel(), mLogLevel);

        mLog.info(TEST_LOG_MESSAGE + 2);
        assertEquals(TEST_LOG_MESSAGE + 2, mMessage);
        assertEquals(LogLevel.INFO.getLogLevel(), mLogLevel);

        mLog.warn(TEST_LOG_MESSAGE + 3);
        assertEquals(TEST_LOG_MESSAGE + 3, mMessage);
        assertEquals(LogLevel.WARN.getLogLevel(), mLogLevel);

        mLog.error(TEST_LOG_MESSAGE + 4);
        assertEquals(TEST_LOG_MESSAGE + 4, mMessage);
        assertEquals(LogLevel.ERROR.getLogLevel(), mLogLevel);

        mLog.assrt(TEST_LOG_MESSAGE + 5);
        assertEquals(TEST_LOG_MESSAGE + 5, mMessage);
        assertEquals(LogLevel.ASSERT.getLogLevel(), mLogLevel);

        // Ensure we are not logging lower pri messages
        mLog.debug(TEST_LOG_MESSAGE + 6);
        assertEquals(TEST_LOG_MESSAGE + 5, mMessage);
        assertEquals(LogLevel.ASSERT.getLogLevel(), mLogLevel);

        mLog.verbose(TEST_LOG_MESSAGE + 7);
        assertEquals(TEST_LOG_MESSAGE + 5, mMessage);
        assertEquals(LogLevel.ASSERT.getLogLevel(), mLogLevel);

        // Set the new level to debug
        mLog.setCurrentLogLevel(LogLevel.DEBUG);

        // Ensure debug is logged and verbose isn't
        mLog.debug(TEST_LOG_MESSAGE + 6);
        assertEquals(TEST_LOG_MESSAGE + 6, mMessage);
        assertEquals(LogLevel.DEBUG.getLogLevel(), mLogLevel);

        mLog.verbose(TEST_LOG_MESSAGE + 7);
        assertEquals(TEST_LOG_MESSAGE + 6, mMessage);
        assertEquals(LogLevel.DEBUG.getLogLevel(), mLogLevel);
    }

    @Test
    public void basicParameterizedNamedLogTest() {
        mLog.info(TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 1);
        assertEquals(TEST_LOGGED_MESSAGE + 1, mMessage);
        assertEquals(TEST_DEFAULT_TAG_NAME, mTag);
        assertEquals(LogLevel.INFO.getLogLevel(), mLogLevel);

        mLog.info(TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 2);
        assertEquals(TEST_LOGGED_MESSAGE + 2, mMessage);
        assertEquals(LogLevel.INFO.getLogLevel(), mLogLevel);

        mLog.warn(TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 3);
        assertEquals(TEST_LOGGED_MESSAGE + 3, mMessage);
        assertEquals(LogLevel.WARN.getLogLevel(), mLogLevel);

        mLog.error(TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 4);
        assertEquals(TEST_LOGGED_MESSAGE + 4, mMessage);
        assertEquals(LogLevel.ERROR.getLogLevel(), mLogLevel);

        mLog.assrt(TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 5);
        assertEquals(TEST_LOGGED_MESSAGE + 5, mMessage);
        assertEquals(LogLevel.ASSERT.getLogLevel(), mLogLevel);

        // Ensure we are not logging lower pri messages
        mLog.debug(TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 6);
        assertEquals(TEST_LOGGED_MESSAGE + 5, mMessage);
        assertEquals(LogLevel.ASSERT.getLogLevel(), mLogLevel);

        mLog.verbose(TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 7);
        assertEquals(TEST_LOGGED_MESSAGE + 5, mMessage);
        assertEquals(LogLevel.ASSERT.getLogLevel(), mLogLevel);

        // Set the new level to debug
        mLog.setCurrentLogLevel(LogLevel.DEBUG);

        // Ensure debug is logged and verbose isn't
        mLog.debug(TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 6);
        assertEquals(TEST_LOGGED_MESSAGE + 6, mMessage);
        assertEquals(LogLevel.DEBUG.getLogLevel(), mLogLevel);

        mLog.verbose(TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 7);
        assertEquals(TEST_LOGGED_MESSAGE + 6, mMessage);
        assertEquals(LogLevel.DEBUG.getLogLevel(), mLogLevel);
    }

    @Test
    public void basicExceptionTest() {
        mLog.exception(new KinesisVideoException(TEST_LOG_MESSAGE));
        assertNotEquals(null, mMessage);
        assertEquals(TEST_DEFAULT_TAG_NAME, mTag);
        assertEquals(LogLevel.ERROR.getLogLevel(), mLogLevel);

        // Set the new level to assert
        mLog.setCurrentLogLevel(LogLevel.ASSERT);

        // Ensure exception isn't logged
        mMessage = null;
        mLog.exception(new KinesisVideoException(TEST_LOG_MESSAGE));
        assertEquals(null, mMessage);
    }

    @Test
    public void customTagTest() {
        mLog.setPackagePrefix();
        mLog.info(TEST_LOG_MESSAGE);
        assertEquals(TEST_LOG_MESSAGE, mMessage);
        assertEquals(TEST_DEFAULT_TAG_NAME + ".LogTest", mTag);
        assertEquals(LogLevel.INFO.getLogLevel(), mLogLevel);
    }
}
