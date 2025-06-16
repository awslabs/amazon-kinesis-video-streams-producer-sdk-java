package com.amazonaws.kinesisvideo.demoapp.debug;

import com.amazonaws.kinesisvideo.internal.client.NativeKinesisVideoClient;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoMetrics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Writes the current heap memory count to the file (bytes), along with a timestamp.
 * This class provides a way to monitor memory allocation of a KinesisVideoClient
 * by periodically writing the allocation size to a specified file in a csv-format.
 * <p>
 * This class will spawn a new thread to record the metrics, and terminate that thread once
 * {@link #close()} is called.
 */
public class StatsWriter implements AutoCloseable {
    private static final Logger log = LogManager.getLogger(StatsWriter.class);

    private final String filePath;
    private final long pollingIntervalMs;
    private final NativeKinesisVideoClient kinesisVideoClient;
    private final ScheduledExecutorService scheduler;
    private final BufferedWriter writer;
    private final List<String> streamNames;
    private ScheduledFuture<?> scheduledTask;

    /**
     * Creates a new HeapWriter instance.
     *
     * @param filePath           The path to the file where heap information will be written
     * @param pollingIntervalMs  The interval in milliseconds between heap measurements
     * @param kinesisVideoClient The KinesisVideoClient instance to monitor
     * @throws IOException If there's an error creating or opening the output file
     */
    public StatsWriter(@Nonnull final String filePath,
                       @Nonnull final Duration pollingIntervalMs,
                       @Nonnull final NativeKinesisVideoClient kinesisVideoClient) throws IOException {
        this.filePath = filePath;
        this.pollingIntervalMs = pollingIntervalMs.toMillis();
        this.kinesisVideoClient = kinesisVideoClient;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            final Thread thread = new Thread(r, "HeapWriter-Thread");
            thread.setDaemon(true);
            return thread;
        });

        final Path path = Paths.get(filePath);
        if (Files.isDirectory(path)) {
            throw new IllegalArgumentException("Path should be a file name: " + path);
        }
        try {
            Files.createDirectories(path);
        } catch (final FileAlreadyExistsException ex) {
            // Ignore
        }
        Files.deleteIfExists(path);
        this.writer = new BufferedWriter(new FileWriter(filePath, false));

        this.streamNames = kinesisVideoClient.getStreamNames();

        writeStatsHeader();
        startMonitoring();
    }

    /**
     * Starts the periodic monitoring of heap allocation.
     */
    private void startMonitoring() {
        scheduledTask = scheduler.scheduleAtFixedRate(
                this::writeHeapInfo,
                0,
                pollingIntervalMs,
                TimeUnit.MILLISECONDS
        );
        log.info("Started heap monitoring with interval of {}ms, writing to {}", pollingIntervalMs, filePath);
    }

    private void writeStatsHeader() {
        try {
            final long contentStoreSizeBytes = kinesisVideoClient.getClientMetrics()
                    .map(KinesisVideoMetrics::getContentStoreSize)
                    .orElse(0L);
            final String entry = String.format("Timestamp,Malloc Usage (Bytes),Content Store Used (Bytes out of %d)%n", contentStoreSizeBytes);
            writer.write(entry);
            writer.flush();
        } catch (final IOException e) {
            log.error("Failed to write heap information", e);
        } catch (final Exception e) {
            log.error("Unexpected error while writing heap information", e);
        }
    }

    /**
     * Writes the current heap information to the file.
     */
    private void writeHeapInfo() {
        try {
            final long allocationSize = kinesisVideoClient.getCurrentAllocationSizeBytes();
            final long contentStoreUsedBytes = kinesisVideoClient.getClientMetrics()
                    .map(KinesisVideoMetrics::getContentStoreAllocatedSize)
                    .orElse(0L);
            final String entry = String.format("%s,%d,%d%n", Instant.now().toString(), allocationSize, contentStoreUsedBytes);
            writer.write(entry);
            writer.flush();
        } catch (final IOException e) {
            log.error("Failed to write heap information", e);
        } catch (final Exception e) {
            log.error("Unexpected error while writing heap information", e);
        }
    }

    /**
     * Stops the monitoring and closes all resources.
     */
    @Override
    public void close() {
        try {
            if (scheduledTask != null) {
                scheduledTask.cancel(false);
            }
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2 * pollingIntervalMs + 500, TimeUnit.MILLISECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (final InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }

            writer.close();
            log.info("Heap monitoring stopped and resources cleaned up");
        } catch (final IOException e) {
            log.error("Error while closing HeapWriter", e);
        }
    }
}
