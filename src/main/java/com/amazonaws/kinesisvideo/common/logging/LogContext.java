package com.amazonaws.kinesisvideo.common.logging;

import org.apache.logging.log4j.ThreadContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tags the current thread's log4j2 ThreadContext with the Kinesis Video client and stream it is working for, so that a
 * log4j2 RoutingAppender can write each client's and each stream's lines, Java and PIC alike, to files of their own.
 *
 * PIC has one log function for the whole process, and its lines do not say which client or stream they belong to. PIC
 * logs on the thread that is inside the native call, though, so the tag on that thread identifies them.
 *
 * Keys:
 * <ul>
 * <li>{@code kvsLogFile}: {@code client_001} for client-level work, {@code stream_002} for a stream</li>
 * <li>{@code kvsClient}: {@code 001}, numbered in creation order across the process</li>
 * <li>{@code kvsStreamName}: the stream's name, for stream-level work</li>
 * </ul>
 * See log4j2-per-stream.xml for a configuration that uses them. Disable with {@code -Dkvs.logContext=false}.
 */
public final class LogContext implements AutoCloseable
{
    public static final String FILE = "kvsLogFile";
    public static final String CLIENT = "kvsClient";
    public static final String STREAM_NAME = "kvsStreamName";

    private static final List<String> KEYS = Arrays.asList(FILE, CLIENT, STREAM_NAME);
    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("kvs.logContext"));

    /**
     * The values of the keys for one client or stream, built once and reused for every call
     */
    public static final class Tag
    {
        final String mFile;
        final String mClient;
        final String mStreamName;
        final Map<String, String> mValues;

        private Tag(final String file, final String client, final String streamName)
        {
            mFile = file;
            mClient = client;
            mStreamName = streamName;
            final Map<String, String> values = new HashMap<String, String>();
            if (client != null) {
                values.put(CLIENT, client);
            }
            if (streamName != null) {
                values.put(STREAM_NAME, streamName);
            }
            values.put(FILE, file);
            mValues = Collections.unmodifiableMap(values);
        }

        public static Tag client(final String clientId)
        {
            return new Tag("client_" + clientId, clientId, null);
        }

        public static Tag stream(final String clientId, final String streamId, final String streamName)
        {
            return new Tag("stream_" + streamId, clientId, streamName);
        }
    }

    private static final LogContext UNCHANGED = new LogContext(null);

    /**
     * Closing it removes the tag from a thread that had none
     */
    private static final LogContext UNTAG = new LogContext(null);

    /**
     * The tag before this scope, for a scope entered over another one
     */
    private final Tag mPrevious;

    private LogContext(final Tag previous)
    {
        mPrevious = previous;
    }

    /**
     * Tags the current thread until the scope is closed. One ThreadContext read when the thread already has the tag,
     * as a thread making call after call for the same stream does; one map copy each way when it has none.
     */
    public static LogContext enter(final Tag tag)
    {
        if (!ENABLED) {
            return UNCHANGED;
        }

        final String file = ThreadContext.get(FILE);
        if (file == null) {
            ThreadContext.putAll(tag.mValues);
            return UNTAG;
        }
        if (file.equals(tag.mFile)) {
            return UNCHANGED;
        }

        final Tag previous = new Tag(file, ThreadContext.get(CLIENT), ThreadContext.get(STREAM_NAME));
        apply(tag);
        return new LogContext(previous);
    }

    public static LogContext client(final String clientId)
    {
        return enter(Tag.client(clientId));
    }

    public static LogContext stream(final String clientId, final String streamId, final String streamName)
    {
        return enter(Tag.stream(clientId, streamId, streamName));
    }

    /**
     * A scope that leaves the current tags alone
     */
    public static LogContext unchanged()
    {
        return UNCHANGED;
    }

    /**
     * The client the current thread is working for, or null
     */
    public static String currentClient()
    {
        return ENABLED ? ThreadContext.get(CLIENT) : null;
    }

    @Override
    public void close()
    {
        if (this == UNTAG) {
            ThreadContext.removeAll(KEYS);
        } else if (mPrevious != null) {
            apply(mPrevious);
        }
    }

    private static void apply(final Tag tag)
    {
        ThreadContext.putAll(tag.mValues);
        if (tag.mClient == null && ThreadContext.containsKey(CLIENT)) {
            ThreadContext.remove(CLIENT);
        }
        if (tag.mStreamName == null && ThreadContext.containsKey(STREAM_NAME)) {
            ThreadContext.remove(STREAM_NAME);
        }
    }

    /**
     * The current thread's tag, or null
     */
    private static Tag current()
    {
        final String file = ThreadContext.get(FILE);
        return file == null ? null : new Tag(file, ThreadContext.get(CLIENT), ThreadContext.get(STREAM_NAME));
    }

    /**
     * Runs the task with the tags the current thread has now: for work handed to another thread
     */
    public static Runnable wrap(final Runnable task)
    {
        final Tag captured = ENABLED ? current() : null;
        if (captured == null) {
            return task;
        }
        return () -> {
            try (LogContext ignored = enter(captured)) {
                task.run();
            }
        };
    }

    public static <T> Callable<T> wrap(final Callable<T> task)
    {
        final Tag captured = ENABLED ? current() : null;
        if (captured == null) {
            return task;
        }
        return () -> {
            try (LogContext ignored = enter(captured)) {
                return task.call();
            }
        };
    }

    /**
     * An executor that runs each task with the tags of the thread that submitted it
     */
    public static ScheduledExecutorService propagating(final ScheduledExecutorService executor)
    {
        return ENABLED && !(executor instanceof Propagating) ? new Propagating(executor) : executor;
    }

    private static final class Propagating implements ScheduledExecutorService
    {
        private final ScheduledExecutorService mDelegate;

        Propagating(final ScheduledExecutorService delegate)
        {
            mDelegate = delegate;
        }

        private static <T> List<Callable<T>> wrapAll(final Collection<? extends Callable<T>> tasks)
        {
            final List<Callable<T>> wrapped = new ArrayList<Callable<T>>(tasks.size());
            for (final Callable<T> task : tasks) {
                wrapped.add(wrap(task));
            }
            return wrapped;
        }

        @Override
        public ScheduledFuture<?> schedule(final Runnable command, final long delay, final TimeUnit unit)
        {
            return mDelegate.schedule(wrap(command), delay, unit);
        }

        @Override
        public <V> ScheduledFuture<V> schedule(final Callable<V> callable, final long delay, final TimeUnit unit)
        {
            return mDelegate.schedule(wrap(callable), delay, unit);
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(final Runnable command, final long initialDelay, final long period,
                                                      final TimeUnit unit)
        {
            return mDelegate.scheduleAtFixedRate(wrap(command), initialDelay, period, unit);
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(final Runnable command, final long initialDelay, final long delay,
                                                         final TimeUnit unit)
        {
            return mDelegate.scheduleWithFixedDelay(wrap(command), initialDelay, delay, unit);
        }

        @Override
        public void shutdown()
        {
            mDelegate.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow()
        {
            return mDelegate.shutdownNow();
        }

        @Override
        public boolean isShutdown()
        {
            return mDelegate.isShutdown();
        }

        @Override
        public boolean isTerminated()
        {
            return mDelegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException
        {
            return mDelegate.awaitTermination(timeout, unit);
        }

        @Override
        public <T> Future<T> submit(final Callable<T> task)
        {
            return mDelegate.submit(wrap(task));
        }

        @Override
        public <T> Future<T> submit(final Runnable task, final T result)
        {
            return mDelegate.submit(wrap(task), result);
        }

        @Override
        public Future<?> submit(final Runnable task)
        {
            return mDelegate.submit(wrap(task));
        }

        @Override
        public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks) throws InterruptedException
        {
            return mDelegate.invokeAll(wrapAll(tasks));
        }

        @Override
        public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks, final long timeout,
                                             final TimeUnit unit) throws InterruptedException
        {
            return mDelegate.invokeAll(wrapAll(tasks), timeout, unit);
        }

        @Override
        public <T> T invokeAny(final Collection<? extends Callable<T>> tasks)
                throws InterruptedException, ExecutionException
        {
            return mDelegate.invokeAny(wrapAll(tasks));
        }

        @Override
        public <T> T invokeAny(final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException
        {
            return mDelegate.invokeAny(wrapAll(tasks), timeout, unit);
        }

        @Override
        public void execute(final Runnable command)
        {
            mDelegate.execute(wrap(command));
        }
    }
}
