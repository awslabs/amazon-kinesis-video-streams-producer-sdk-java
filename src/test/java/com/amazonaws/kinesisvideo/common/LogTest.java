package com.amazonaws.kinesisvideo.common;

import static org.junit.Assert.*;

import javax.annotation.Nonnull;

import org.apache.logging.log4j.Level;
import org.junit.Before;
import org.junit.Test;

import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.common.logging.Log;

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

    @Before
    public void setupLog() {
        mLogLevel = TEST_UNDEFINED_LOG_LEVEL;
        mTag = null;
        mMessage = null;

        mLog = Log.getLogInstance(TEST_DEFAULT_TAG_NAME);
    }

    @Test
    public void basicLogTest() {
        mLog.info(TEST_LOG_MESSAGE + 1);
        assertEquals(TEST_LOG_MESSAGE + 1, mMessage);
        assertEquals(TEST_DEFAULT_TAG_NAME, mTag);
        assertEquals(Level.getLevel("INFO"), mLogLevel);

        mLog.info(TEST_LOG_MESSAGE + 2);
        assertEquals(TEST_LOG_MESSAGE + 2, mMessage);
        assertEquals(Level.getLevel("INFO"), mLogLevel);

        mLog.warn(TEST_LOG_MESSAGE + 3);
        assertEquals(TEST_LOG_MESSAGE + 3, mMessage);
        assertEquals(Level.getLevel("WARN"), mLogLevel);

        mLog.error(TEST_LOG_MESSAGE + 4);
        assertEquals(TEST_LOG_MESSAGE + 4, mMessage);
        assertEquals(Level.getLevel("ERROR"), mLogLevel);

        mLog.assrt(TEST_LOG_MESSAGE + 5);
        assertEquals(TEST_LOG_MESSAGE + 5, mMessage);
        assertEquals(Level.getLevel("ASSERT"), mLogLevel);

        // Ensure we are not logging lower pri messages
        mLog.assrt(TEST_LOG_MESSAGE + 6);
        assertEquals(TEST_LOG_MESSAGE + 5, mMessage);
        assertEquals(Level.getLevel("ASSERT"), mLogLevel);

        mLog.verbose(TEST_LOG_MESSAGE + 7);
        assertEquals(TEST_LOG_MESSAGE + 5, mMessage);
        assertEquals(Level.getLevel("ASSERT"), mLogLevel);

        // Set the new level to debug
        mLog.setCurrentLogLevel(Level.DEBUG);

        // Ensure debug is logged and verbose isn't
        mLog.debug(TEST_LOG_MESSAGE + 6);
        assertEquals(TEST_LOG_MESSAGE + 6, mMessage);
        assertEquals(Level.getLevel("DEBUG"), mLogLevel);

        mLog.verbose(TEST_LOG_MESSAGE + 7);
        assertEquals(TEST_LOG_MESSAGE + 6, mMessage);
        assertEquals(Level.getLevel("DEBUG"), mLogLevel);
    }

    @Test
    public void basicParameterizedLogTest() {
        mLog.info(TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 1);
        assertEquals(TEST_LOGGED_MESSAGE + 1, mMessage);
        assertEquals(TEST_DEFAULT_TAG_NAME, mTag);
        assertEquals(Level.getLevel("INFO"), mLogLevel);

        mLog.info(TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 2);
        assertEquals(TEST_LOGGED_MESSAGE + 2, mMessage);
        assertEquals(Level.getLevel("INFO"), mLogLevel);

        mLog.warn(TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 3);
        assertEquals(TEST_LOGGED_MESSAGE + 3, mMessage);
        assertEquals(Level.getLevel("WARN"), mLogLevel);

        mLog.error(TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 4);
        assertEquals(TEST_LOGGED_MESSAGE + 4, mMessage);
        assertEquals(Level.getLevel("ERROR"), mLogLevel);

        mLog.assrt(TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 5);
        assertEquals(TEST_LOGGED_MESSAGE + 5, mMessage);
        assertEquals(Level.getLevel("ASSERT"), mLogLevel);

        // Ensure we are not logging lower pri messages
        mLog.debug(TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 6);
        assertEquals(TEST_LOGGED_MESSAGE + 5, mMessage);
        assertEquals(Level.getLevel("ASSERT"), mLogLevel);

        mLog.verbose(TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 7);
        assertEquals(TEST_LOGGED_MESSAGE + 5, mMessage);
        assertEquals(Level.getLevel("ASSERT"), mLogLevel);

        // Set the new level to debug
        mLog.setCurrentLogLevel(Level.DEBUG);

        // Ensure debug is logged and verbose isn't
        mLog.debug(TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 6);
        assertEquals(TEST_LOGGED_MESSAGE + 6, mMessage);
        assertEquals(Level.getLevel("DEBUG"), mLogLevel);

        mLog.verbose(TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 7);
        assertEquals(TEST_LOGGED_MESSAGE + 6, mMessage);
        assertEquals(Level.getLevel("DEBUG"), mLogLevel);
    }

    @Test
    public void basicNamedLogTest() {
        mLog.info(TEST_LOG_MESSAGE + 1);
        assertEquals(TEST_LOG_MESSAGE + 1, mMessage);
        assertEquals(TEST_DEFAULT_TAG_NAME, mTag);
        assertEquals(Level.getLevel("INFO"), mLogLevel);

        mLog.info(TEST_LOG_MESSAGE + 2);
        assertEquals(TEST_LOG_MESSAGE + 2, mMessage);
        assertEquals(Level.getLevel("INFO"), mLogLevel);

        mLog.warn(TEST_LOG_MESSAGE + 3);
        assertEquals(TEST_LOG_MESSAGE + 3, mMessage);
        assertEquals(Level.getLevel("WARN"), mLogLevel);

        mLog.error(TEST_LOG_MESSAGE + 4);
        assertEquals(TEST_LOG_MESSAGE + 4, mMessage);
        assertEquals(Level.getLevel("ERROR"), mLogLevel);

        mLog.assrt(TEST_LOG_MESSAGE + 5);
        assertEquals(TEST_LOG_MESSAGE + 5, mMessage);
        assertEquals(Level.getLevel("ASSERT"), mLogLevel);

        // Ensure we are not logging lower pri messages
        mLog.debug(TEST_LOG_MESSAGE + 6);
        assertEquals(TEST_LOG_MESSAGE + 5, mMessage);
        assertEquals(Level.getLevel("ASSERT"), mLogLevel);

        mLog.verbose(TEST_LOG_MESSAGE + 7);
        assertEquals(TEST_LOG_MESSAGE + 5, mMessage);
        assertEquals(Level.getLevel("ASSERT"), mLogLevel);

        // Set the new level to debug
        mLog.setCurrentLogLevel(Level.DEBUG);

        // Ensure debug is logged and verbose isn't
        mLog.debug(TEST_LOG_MESSAGE + 6);
        assertEquals(TEST_LOG_MESSAGE + 6, mMessage);
        assertEquals(Level.getLevel("DEBUG"), mLogLevel);

        mLog.verbose(TEST_LOG_MESSAGE + 7);
        assertEquals(TEST_LOG_MESSAGE + 6, mMessage);
        assertEquals(Level.getLevel("DEBUG"), mLogLevel);
    }

    @Test
    public void basicParameterizedNamedLogTest() {
        mLog.info(TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 1);
        assertEquals(TEST_LOGGED_MESSAGE + 1, mMessage);
        assertEquals(TEST_DEFAULT_TAG_NAME, mTag);
        assertEquals(Level.getLevel("INFO"), mLogLevel);

        mLog.info(TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 2);
        assertEquals(TEST_LOGGED_MESSAGE + 2, mMessage);
        assertEquals(Level.getLevel("INFO"), mLogLevel);

        mLog.warn(TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 3);
        assertEquals(TEST_LOGGED_MESSAGE + 3, mMessage);
        assertEquals(Level.getLevel("WARN"), mLogLevel);

        mLog.error(TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 4);
        assertEquals(TEST_LOGGED_MESSAGE + 4, mMessage);
        assertEquals(Level.getLevel("ERROR"), mLogLevel);

        mLog.assrt(TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 5);
        assertEquals(TEST_LOGGED_MESSAGE + 5, mMessage);
        assertEquals(Level.getLevel("ASSERT"), mLogLevel);

        // Ensure we are not logging lower pri messages
        mLog.debug(TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 6);
        assertEquals(TEST_LOGGED_MESSAGE + 5, mMessage);
        assertEquals(Level.getLevel("ASSERT"), mLogLevel);

        mLog.verbose(TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 7);
        assertEquals(TEST_LOGGED_MESSAGE + 5, mMessage);
        assertEquals(Level.getLevel("ASSERT"), mLogLevel);

        // Set the new level to debug
        mLog.setCurrentLogLevel(Level.DEBUG);

        // Ensure debug is logged and verbose isn't
        mLog.debug(TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 6);
        assertEquals(TEST_LOGGED_MESSAGE + 6, mMessage);
        assertEquals(Level.getLevel("DEBUG"), mLogLevel);

        mLog.verbose(TEST_PARAMETERIZED_LOG_MESSAGE, TEST_DEFAULT_TAG_NAME, 7);
        assertEquals(TEST_LOGGED_MESSAGE + 6, mMessage);
        assertEquals(Level.getLevel("DEBUG"), mLogLevel);
    }

    @Test
    public void basicExceptionTest() {
        mLog.exception(new KinesisVideoException(TEST_LOG_MESSAGE));
        assertNotEquals(null, mMessage);
        assertEquals(TEST_DEFAULT_TAG_NAME, mTag);
        assertEquals(Level.getLevel("ERROR"), mLogLevel);

        // Set the new level to assert
        mLog.setCurrentLogLevel(Level.getLevel("ASSERT"));

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
        assertEquals(Level.getLevel("INFO"), mLogLevel);
    }
}
