package com.amazonaws.kinesisvideo.util;

import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;

/**
 * An abstract implementation of {@link Runnable} that provides structured logging
 * for task execution. This class automatically logs entry and exit points of the task,
 * as well as any errors that occur during execution.
 *
 * <p>This class is designed to wrap task execution with consistent logging patterns,
 * making it easier to track and debug asynchronous operations. All exceptions are caught
 * and logged, preventing them from propagating up the call stack.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * LoggedExitRunnable task = new LoggedExitRunnable("MyTask") {
 *     {@literal @}Override
 *     public void execute() {
 *         // Task implementation here
 *     }
 * };
 * executorService.submit(task);
 * </pre>
 *
 * <p>The logging format follows the pattern:</p>
 * <ul>
 *   <li>On entry: [TaskName] Enter</li>
 *   <li>On error: [TaskName] Encountered error running task</li>
 *   <li>On exit: [TaskName] Leave</li>
 * </ul>
 *
 * @see Runnable
 */
public abstract class LoggedExitRunnable implements Runnable {

    private static final Logger log = LogManager.getLogger(LoggedExitRunnable.class);

    private final String runnableTaskName;

    /**
     * Creates a new LoggedExitRunnable with the specified task name.
     *
     * @param runnableTaskName A descriptive name for the task that will appear in log messages.
     *                         Should be unique enough to identify the specific task instance
     *                         in log output.
     * @throws NullPointerException if runnableTaskName is null
     */
    public LoggedExitRunnable(@Nonnull final String runnableTaskName) {
        Preconditions.checkNotNull(runnableTaskName);
        this.runnableTaskName = runnableTaskName;
    }

    /**
     * Implements the {@link Runnable#run()} method with structured logging.
     * This method handles the logging of entry and exit points, as well as any
     * errors that occur during execution.
     *
     * <p>This implementation ensures that all exceptions are caught and logged,
     * and that exit logging occurs even if an exception is thrown.</p>
     */
    @Override
    public void run() {
        log.debug("[{}] Enter", runnableTaskName);

        try {
            execute();
        } catch (final Throwable t) {
            log.error("[{}] Encountered error running task", runnableTaskName, t);
            throw t;
        } finally {
            log.debug("[{}] Leave", runnableTaskName);
        }
    }

    /**
     * To be implemented by concrete classes to define the actual task execution logic.
     *
     * <p>Any uncaught exceptions thrown by this method will be caught, logged, and re-thrown
     * (propagated) by the {@link #run()} method.</p>
     */
    public abstract void execute();
}
