package com.amazonaws.kinesisvideo.demoapp;


import com.amazonaws.kinesisvideo.client.KinesisVideoClient;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.demoapp.auth.AuthHelper;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSource;
import com.amazonaws.kinesisvideo.java.client.KinesisVideoJavaClientFactory;
import com.amazonaws.kinesisvideo.java.mediasource.file.AudioFileMediaSource;
import com.amazonaws.kinesisvideo.java.mediasource.file.AudioFileMediaSourceConfiguration;
import com.amazonaws.regions.Regions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sample audio streamer from file media source
 */
public class KinesisVideoAudioMultiStreamer implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(KinesisVideoAudioMultiStreamer.class);

    private static final int DEFAULT_THREAD_POOL_SIZE = 5;
    private static final long DEFAULT_SHUTDOWN_TIMEOUT_SECONDS = 30;
    private static final long DEFAULT_STARTUP_TIMEOUT_MINUTES = 5;

    private final Map<String, MediaSource> mediaSourceMap = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final KinesisVideoClient kinesisVideoClient;
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final StreamConfiguration streamConfig;

    public static class StreamConfiguration {
        private final String audioDir;
        private final String filenameFormat;
        private final int startFileIndex;
        private final int endFileIndex;
        private final int fps;
        private final long threadStartDelayMs;

        private StreamConfiguration(Builder builder) {
            this.audioDir = builder.audioDir;
            this.filenameFormat = builder.filenameFormat;
            this.startFileIndex = builder.startFileIndex;
            this.endFileIndex = builder.endFileIndex;
            this.fps = builder.fps;
            this.threadStartDelayMs = builder.threadStartDelayMs;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String audioDir = "src/main/resources/data/pcm_chunks/";
            private String filenameFormat = "chunk_%d.pcm";
            private int startFileIndex = 1;
            private int endFileIndex = 99;
            private int fps = 25;
            private long threadStartDelayMs = 5000;

            public Builder audioDir(String audioDir) {
                this.audioDir = audioDir;
                return this;
            }

            // Add other builder methods

            public StreamConfiguration build() {
                return new StreamConfiguration(this);
            }
        }
    }

    public KinesisVideoAudioMultiStreamer(Regions region, StreamConfiguration config) throws KinesisVideoException {
        this.streamConfig = config;
        this.executor = createExecutorService();
        this.kinesisVideoClient = createKinesisVideoClient(region);
        addShutdownHook();
    }

    private ExecutorService createExecutorService() {
        return new ThreadPoolExecutor(
                DEFAULT_THREAD_POOL_SIZE,
                DEFAULT_THREAD_POOL_SIZE,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadFactory() {
                    private final AtomicInteger threadNumber = new AtomicInteger(1);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "KVS-Stream-" + threadNumber.getAndIncrement());
                        t.setDaemon(true);
                        return t;
                    }
                }
        );
    }

    private KinesisVideoClient createKinesisVideoClient(Regions region) throws KinesisVideoException {
        return KinesisVideoJavaClientFactory.createKinesisVideoClient(
                region,
                AuthHelper.getSystemPropertiesCredentialsProvider()
        );
    }

    public CompletableFuture<Void> startStream(String streamName) {
        return CompletableFuture.runAsync(() -> {
            try {
                MediaSource mediaSource = createAndConfigureMediaSource(streamName);
                mediaSourceMap.put(streamName, mediaSource);
                kinesisVideoClient.registerMediaSource(mediaSource);
                mediaSource.start();
                log.info("Stream {} started successfully", streamName);
            } catch (Exception e) {
                errorCount.incrementAndGet();
                log.error("Failed to start stream {}", streamName, e);
                throw new CompletionException(e);
            }
        }, executor);
    }

    private static final long THREAD_START_DELAY_MS = 5000;

    private CompletableFuture<Void> startStreamsWithDelay(List<StreamConfig> streamConfigs) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < streamConfigs.size(); i++) {
            StreamConfig config = streamConfigs.get(i);
            long delay = i == 0 ? 0 : THREAD_START_DELAY_MS;

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    if (delay > 0) {
                        Thread.sleep(delay);
                    }
                    startStream(config.streamName).get();
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, executor);

            futures.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private MediaSource createAndConfigureMediaSource(String streamName) {
        AudioFileMediaSourceConfiguration configuration =
                new AudioFileMediaSourceConfiguration.Builder()
                        .fps(streamConfig.fps)
                        .dir(streamConfig.audioDir)
                        .filenameFormat(streamConfig.filenameFormat)
                        .startFileIndex(streamConfig.startFileIndex)
                        .endFileIndex(streamConfig.endFileIndex)
                        .allowStreamCreation(true)
                        .build();

        AudioFileMediaSource mediaSource = new AudioFileMediaSource(streamName);
        mediaSource.configure(configuration);
        return mediaSource;
    }

    public void stopStream(String streamName) {
        MediaSource mediaSource = mediaSourceMap.remove(streamName);
        if (mediaSource != null) {
            try {
                mediaSource.stop();
                log.info("Stream {} stopped successfully", streamName);
            } catch (Exception e) {
                log.error("Error stopping stream {}", streamName, e);
                errorCount.incrementAndGet();
            }
        }
    }

    @Override
    public void close() {
        shutdownExecutor();
        cleanupKinesisVideoClient();
        log.info("Streaming completed. Total errors: {}", errorCount.get());
    }

    private void shutdownExecutor() {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(DEFAULT_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("Executor did not terminate in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while shutting down executor", e);
        }
    }

    private void cleanupKinesisVideoClient() {
        try {
            kinesisVideoClient.stopAllMediaSources();
            kinesisVideoClient.free();
        } catch (KinesisVideoException e) {
            log.error("Error cleaning up KinesisVideoClient", e);
        }
    }

    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::close, "KVS-Shutdown-Hook"));
    }


    public static void main(String[] args) {

        StreamConfiguration config = StreamConfiguration.builder().build();

        List<StreamConfig> streamConfigs = Arrays.asList(
                new StreamConfig("test-stream-001", 0),
                new StreamConfig("test-stream-004", THREAD_START_DELAY_MS),
                new StreamConfig("test-stream-003", THREAD_START_DELAY_MS)
        );

        try (KinesisVideoAudioMultiStreamer streamer =
                     new KinesisVideoAudioMultiStreamer(Regions.US_WEST_2, config)) {

            try {
                streamer.startStreamsWithDelay(streamConfigs)
                        .get(DEFAULT_STARTUP_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                log.error("Timeout waiting for streams to start", e);
                throw new RuntimeException("Stream startup timeout", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for streams", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Error starting streams", e.getCause());
            }

            // Keep running until interrupted
            Thread.currentThread().join();
        } catch (Exception e) {
            log.error("Error in main thread", e);
            System.exit(1);
        }
    }
}

/**
 * Config for the Sample audio file media source streamer
 */
class StreamConfig {
    final String streamName;
    final long startDelay;

    StreamConfig(String streamName, long startDelay) {
        this.streamName = streamName;
        this.startDelay = startDelay;
    }
}

