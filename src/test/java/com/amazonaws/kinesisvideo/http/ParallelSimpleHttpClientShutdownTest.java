package com.amazonaws.kinesisvideo.http;

import com.amazonaws.kinesisvideo.common.function.Consumer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class ParallelSimpleHttpClientShutdownTest {

    private static final String TEST_URI = "http://example.com";
    private static final long LONG_RUNNING_TASK_MILLIS = 10_000L;
    private static final long FAST_OPERATION_THRESHOLD_MILLIS = 1_000L;
    private static final long THREAD_SHUTDOWN_WAIT_MILLIS = 100L;
    private static final int TASK_START_TIMEOUT_SECONDS = 2;
    private static final int TASK_END_TIMEOUT_SECONDS = 2;

    @Rule
    public Timeout globalTimeout = new Timeout(5, TimeUnit.SECONDS);

    @Test
    public void givenClientWithoutBackgroundTasks_whenClose_thenNoExceptionThrown() throws IOException {
        // Given
        final ParallelSimpleHttpClient client = createBasicClient();

        // When & Then
        client.close();
    }

    @Test
    public void givenClientWithCompletionCallback_whenCloseSuccessfully_thenCallbackReceivesNull() throws IOException {
        // Given
        final AtomicReference<Exception> completionException = new AtomicReference<>();
        final ParallelSimpleHttpClient client = createClientWithCompletionCallback(completionException::set);

        // When
        client.close();

        // Then
        assertNull("Completion callback should receive null on successful close",
                completionException.get());
    }

    /**
     * Close is idempotent
     */
    @Test
    public void givenClient_whenCloseCalledMultipleTimes_thenNoExceptionThrown() throws IOException {
        // Given
        final ParallelSimpleHttpClient client = createBasicClient();

        // When & Then
        client.close();
        client.close();
        client.close();
    }

    @Test
    public void givenClientWithoutExecutors_whenClose_thenCompletesQuickly() throws IOException {
        // Given
        final AtomicBoolean completionCalled = new AtomicBoolean(false);
        final ParallelSimpleHttpClient client = createClientWithCompletionCallback(exception -> completionCalled.set(true));

        // When
        final long startTime = System.currentTimeMillis();
        client.close();
        final long duration = System.currentTimeMillis() - startTime;

        // Then
        assertTrue("Completion callback should be called", completionCalled.get());
        assertTrue("Close should complete quickly without executors",
                duration < FAST_OPERATION_THRESHOLD_MILLIS);
    }

    @Test
    public void givenClientWithCompletionCallback_whenCloseWithoutErrors_thenCallbackReceivesNull() throws IOException {
        // Given
        final AtomicReference<Exception> completionException = new AtomicReference<>();
        final ParallelSimpleHttpClient client = createClientWithCompletionCallback(completionException::set);

        // When
        client.close();

        // Then
        assertNull("Completion callback should receive null when no errors occur",
                completionException.get());
    }

    @Test
    public void givenClientWithRunningExecutors_whenClose_thenThreadsAreCleanedUp() throws Exception {
        // Given
        final int initialThreadCount = Thread.activeCount();
        final CountDownLatch taskStarted = new CountDownLatch(1);
        final AtomicBoolean taskInterrupted = new AtomicBoolean(false);

        final ParallelSimpleHttpClient client = createClientWithCallbacks(
                createLongRunningOutputStreamConsumer(taskStarted, taskInterrupted),
                createNoOpInputStreamConsumer()
        );

        final ExecutorService[] executors = injectExecutorsAndStartTask(client, taskStarted, taskInterrupted);

        // When
        assertTrue("Task should start within timeout", taskStarted.await(TASK_START_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        final int runningThreadCount = Thread.activeCount();

        closeClientIgnoringSocketErrors(client);
        Thread.sleep(THREAD_SHUTDOWN_WAIT_MILLIS);

        final int finalThreadCount = Thread.activeCount();

        // Then
        assertThreadCleanupSuccessful(initialThreadCount, runningThreadCount, finalThreadCount,
                taskInterrupted, executors);
    }

    @Test
    public void givenClientWithRunningExecutors_whenCloseTimesOut_thenTimeoutExceptionPostedToCallback() throws Exception {
        // Given
        final AtomicReference<Exception> completionException = new AtomicReference<>();
        final CountDownLatch taskStarted = new CountDownLatch(1);
        final CountDownLatch completionCalled = new CountDownLatch(1);

        final ParallelSimpleHttpClient client = ParallelSimpleHttpClient.builder()
                .uri(URI.create(TEST_URI))
                .method(HttpMethodName.POST)
                .closeTimeout(Duration.ofMillis(10)) // Very short timeout to force timeout
                .completionCallback(exception -> {
                    completionException.set(exception);
                    completionCalled.countDown();
                })
                .build();

        // Manually inject executors with running tasks that ignore interruption
        final ExecutorService payloadSender = Executors.newSingleThreadExecutor();
        final ExecutorService responseReceiver = Executors.newSingleThreadExecutor();

        getAccessibleField("payloadSender").set(client, payloadSender);
        getAccessibleField("responseReceiver").set(client, responseReceiver);

        // Submit tasks that ignore interruption to both executors
        payloadSender.submit(() -> {
            taskStarted.countDown();
            long endTime = System.currentTimeMillis() + LONG_RUNNING_TASK_MILLIS;
            while (System.currentTimeMillis() < endTime) {
                Thread.yield();
            }
        });

        responseReceiver.submit(() -> {
            long endTime = System.currentTimeMillis() + LONG_RUNNING_TASK_MILLIS;
            while (System.currentTimeMillis() < endTime) {
                Thread.yield();
            }
        });

        // When
        assertTrue("Task should start within timeout", taskStarted.await(TASK_START_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        closeClientIgnoringSocketErrors(client);
        
        // Wait for completion callback to be called
        assertTrue("Completion callback should be called", completionCalled.await(TASK_END_TIMEOUT_SECONDS, TimeUnit.SECONDS));

        // Then
        assertNotNull("Completion callback should receive an exception", completionException.get());
        assertTrue("Exception should be TimeoutException", completionException.get() instanceof TimeoutException);
        assertTrue("Exception message should mention timeout", 
                completionException.get().getMessage().contains("Timeout while waiting for executor shutdown"));
    }

    private ParallelSimpleHttpClient createBasicClient() {
        return ParallelSimpleHttpClient.builder()
                .uri(URI.create(TEST_URI))
                .method(HttpMethodName.POST)
                .build();
    }

    private ParallelSimpleHttpClient createClientWithCompletionCallback(final Consumer<Exception> completionCallback) {
        return ParallelSimpleHttpClient.builder()
                .uri(URI.create(TEST_URI))
                .method(HttpMethodName.POST)
                .completionCallback(completionCallback)
                .build();
    }

    private ParallelSimpleHttpClient createClientWithCallbacks(final Consumer<OutputStream> sender,
                                                               final Consumer<InputStream> receiver) {
        return ParallelSimpleHttpClient.builder()
                .uri(URI.create(TEST_URI))
                .method(HttpMethodName.POST)
                .setSenderCallback(sender)
                .setReceiverCallback(receiver)
                .build();
    }

    private Consumer<OutputStream> createLongRunningOutputStreamConsumer(final CountDownLatch taskStarted,
                                                                         final AtomicBoolean taskInterrupted) {
        return outputStream -> {
            try {
                taskStarted.countDown();
                Thread.sleep(LONG_RUNNING_TASK_MILLIS);
            } catch (final InterruptedException e) {
                taskInterrupted.set(true);
                Thread.currentThread().interrupt();
            }
        };
    }

    private Consumer<InputStream> createNoOpInputStreamConsumer() {
        return inputStream -> { /* No-op */ };
    }

    private ExecutorService[] injectExecutorsAndStartTask(final ParallelSimpleHttpClient client,
                                                          final CountDownLatch taskStarted,
                                                          final AtomicBoolean taskInterrupted) throws Exception {
        final Field payloadSenderField = getAccessibleField("payloadSender");
        final Field responseReceiverField = getAccessibleField("responseReceiver");

        final ExecutorService payloadSender = Executors.newSingleThreadExecutor();
        final ExecutorService responseReceiver = Executors.newSingleThreadExecutor();

        payloadSenderField.set(client, payloadSender);
        responseReceiverField.set(client, responseReceiver);

        payloadSender.submit(createLongRunningTask(taskStarted, taskInterrupted));

        return new ExecutorService[]{payloadSender, responseReceiver};
    }

    private Field getAccessibleField(final String fieldName) throws NoSuchFieldException {
        final Field field = ParallelSimpleHttpClient.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }

    private Runnable createLongRunningTask(final CountDownLatch taskStarted,
                                           final AtomicBoolean taskInterrupted) {
        return () -> {
            try {
                taskStarted.countDown();
                Thread.sleep(LONG_RUNNING_TASK_MILLIS);
            } catch (final InterruptedException e) {
                taskInterrupted.set(true);
                Thread.currentThread().interrupt();
            }
        };
    }

    private void closeClientIgnoringSocketErrors(final ParallelSimpleHttpClient client) {
        try {
            client.close();
        } catch (final IOException e) {
            // Expected due to null socket in test environment
        }
    }

    private void assertThreadCleanupSuccessful(final int initialThreadCount, final int runningThreadCount,
                                               final int finalThreadCount, final AtomicBoolean taskInterrupted,
                                               final ExecutorService[] executors) {
        assertTrue("Thread count should increase with running executors",
                runningThreadCount > initialThreadCount);
        assertTrue("Task should have been interrupted", taskInterrupted.get());

        for (final ExecutorService executor : executors) {
            assertTrue("Executor should be shut down", executor.isShutdown());
        }

        assertTrue(String.format("Thread count should decrease after cleanup (initial: %d, running: %d, final: %d)",
                        initialThreadCount, runningThreadCount, finalThreadCount),
                finalThreadCount <= runningThreadCount);
    }
}
