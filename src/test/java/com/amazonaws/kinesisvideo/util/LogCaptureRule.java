package com.amazonaws.kinesisvideo.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.message.Message;
import org.junit.rules.ExternalResource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JUnit rule for capturing log messages during test execution.
 * This rule automatically sets up and tears down log capture, making it easy to
 * verify that expected log messages are generated during tests.
 *
 * <p>Usage example:
 * <pre>
 * public class SomeTest {
 *     &#64;Rule
 *     public LogCaptureRule logCapture = new LogCaptureRule();
 *
 *     &#64;Test
 *     public void testSomething() {
 *         // ... test code that generates logs ...
 *
 *         List&lt;String&gt; errorMessages = logCapture.getLogMessagesAtLevel(Level.ERROR);
 *         assertTrue("Expected error message not found",
 *                   errorMessages.stream().anyMatch(msg -> msg.contains("expected error")));
 *     }
 * }
 * </pre>
 */
public class LogCaptureRule extends ExternalResource {

    private TestLogAppender testLogAppender;

    /**
     * Custom Log4j2 appender to capture log messages for testing
     */
    private static class TestLogAppender extends AbstractAppender {
        private final List<LogEvent> logEvents = Collections.synchronizedList(new ArrayList<>());

        protected TestLogAppender() {
            super("TestLogAppender", null, PatternLayout.createDefaultLayout(), true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(final LogEvent event) {
            this.logEvents.add(event.toImmutable());
        }

        public List<LogEvent> getLogEvents() {
            synchronized (this.logEvents) {
                return new ArrayList<>(this.logEvents);
            }
        }

        public void clearLogs() {
            synchronized (this.logEvents) {
                this.logEvents.clear();
            }
        }

        public List<String> getLogMessages() {
            synchronized (this.logEvents) {
                return this.logEvents.stream()
                        .map(LogEvent::getMessage)
                        .map(Message::getFormattedMessage)
                        .collect(Collectors.toList());
            }
        }

        public List<String> getLogMessagesAtLevel(final Level level) {
            synchronized (this.logEvents) {
                return this.logEvents.stream()
                        .filter(event -> event.getLevel().equals(level))
                        .map(LogEvent::getMessage)
                        .map(Message::getFormattedMessage)
                        .collect(Collectors.toList());
            }
        }
    }

    @Override
    protected void before() throws Throwable {
        setupLogCapture();
    }

    @Override
    protected void after() {
        cleanupLogCapture();
    }

    /**
     * Sets up log capture for testing
     */
    private void setupLogCapture() {
        // Clean up any existing appender first
        cleanupLogCapture();
        
        this.testLogAppender = new TestLogAppender();
        this.testLogAppender.start();

        // Get the root logger and add our test appender
        final Logger rootLogger = (Logger) LogManager.getRootLogger();
        rootLogger.addAppender(this.testLogAppender);
    }

    /**
     * Cleans up log capture after testing
     */
    private void cleanupLogCapture() {
        if (this.testLogAppender != null) {
            final Logger rootLogger = (Logger) LogManager.getRootLogger();
            rootLogger.removeAppender(this.testLogAppender);
            this.testLogAppender.stop();
            this.testLogAppender = null;
        }
    }

    /**
     * Gets all captured log messages as strings
     *
     * @return List of log messages as strings
     */
    public List<String> getLogMessages() {
        return this.testLogAppender != null ? this.testLogAppender.getLogMessages() : Collections.emptyList();
    }

    /**
     * Gets the captured log messages at a specific level
     *
     * @param level The log level to filter by
     * @return List of log messages at the specified level
     */
    public List<String> getLogMessagesAtLevel(final Level level) {
        return this.testLogAppender != null ? this.testLogAppender.getLogMessagesAtLevel(level) : Collections.emptyList();
    }

    /**
     * Clears all captured log messages
     */
    public void clearLogs() {
        if (this.testLogAppender != null) {
            this.testLogAppender.clearLogs();
        }
    }

    /**
     * Gets all captured log events (for advanced use cases)
     *
     * @return List of LogEvent objects
     */
    public List<LogEvent> getLogEvents() {
        return this.testLogAppender != null ? this.testLogAppender.getLogEvents() : Collections.emptyList();
    }
}
