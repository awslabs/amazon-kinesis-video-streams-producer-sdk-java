package com.amazonaws.kinesisvideo.internal.client;

import static com.amazonaws.kinesisvideo.common.preconditions.Preconditions.checkNotNull;
import static com.amazonaws.kinesisvideo.internal.producer.ReadResult.INVALID_UPLOAD_HANDLE_VALUE;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.amazonaws.kinesisvideo.auth.DefaultAuthCallbacks;
import com.amazonaws.kinesisvideo.client.KinesisVideoClientConfiguration;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSource;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSourceConfiguration;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoMetrics;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoStreamMetrics;
import com.amazonaws.util.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.internal.mediasource.ProducerStreamSink;
import com.amazonaws.kinesisvideo.producer.AuthCallbacks;
import com.amazonaws.kinesisvideo.producer.DeviceInfo;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducer;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.internal.producer.ServiceCallbacks;
import com.amazonaws.kinesisvideo.producer.StorageCallbacks;
import com.amazonaws.kinesisvideo.producer.StreamCallbacks;
import com.amazonaws.kinesisvideo.internal.producer.client.KinesisVideoServiceClient;
import com.amazonaws.kinesisvideo.internal.producer.jni.NativeKinesisVideoProducerJni;
import com.amazonaws.kinesisvideo.internal.service.DefaultServiceCallbacksImpl;
import com.amazonaws.kinesisvideo.streaming.DefaultStreamCallbacks;

/**
 * Implement Kinesis Video Client interface for Android.
 *
 * Main purpose of this class is to manage media sources and their configuration.
 *
 * Media source produces the stream of data which is uploaded into Kinesis Video using this and underlying
 * classes and producer SDK. Stream of data produced by the media source can be anything,
 * for example, video, sound, sensor data, logs, etc. Kinesis Video is agnostic to
 * the internal format of the data.
 *
 * This client wraps the calls to the back-end, managing the device and network configuration,
 * creating, registering, and controlling all streams at once
 */
public class NativeKinesisVideoClient extends AbstractKinesisVideoClient {
    /**
     * Logging tag
     */
    private static final String TAG = "NativeKinesisVideoClient";

    /**
     * Map of the media source to KVS producer stream
     */
    private final Map<MediaSource, KinesisVideoProducerStream> mMediaSourceToStreamMap;

    /**
     * Kinesis Video producer callbacks
     */
    private final AuthCallbacks mAuthCallbacks;
    private final StorageCallbacks mStorageCallbacks;
    private final StreamCallbacks mStreamCallbacks;
    private final ServiceCallbacks mServiceCallbacks;

    /**
     * Whether to use PIC instrumented allocators.
     */
    private final boolean mUseInstrumentedAllocators;

    /**
     * Underlying Kinesis Video producer object.
     */
    private KinesisVideoProducer kinesisVideoProducer;

    public NativeKinesisVideoClient(
            @Nonnull final KinesisVideoClientConfiguration configuration,
            @Nonnull final KinesisVideoServiceClient serviceClient,
            @Nonnull final ScheduledExecutorService executor) {
        this(LogManager.getLogger(NativeKinesisVideoClient.class),
                configuration,
                serviceClient,
                executor);
    }

    public NativeKinesisVideoClient(
            @Nonnull final Logger log,
            @Nonnull final KinesisVideoClientConfiguration configuration,
            @Nonnull final KinesisVideoServiceClient serviceClient,
            @Nonnull final ScheduledExecutorService executor) {
        this(log,
                new DefaultAuthCallbacks(configuration.getCredentialsProvider(),
                        executor),
                configuration.getStorageCallbacks(),
                new DefaultServiceCallbacksImpl(log, executor, configuration, serviceClient),
                new DefaultStreamCallbacks());
    }

    public NativeKinesisVideoClient(
            @Nonnull final Logger log,
            @Nonnull final AuthCallbacks authCallbacks,
            @Nonnull final StorageCallbacks storageCallbacks,
            @Nonnull final ServiceCallbacks serviceCallbacks,
            @Nonnull final StreamCallbacks streamCallbacks) {
        this(log, authCallbacks, storageCallbacks, serviceCallbacks, streamCallbacks, false);
    }

    public NativeKinesisVideoClient(
            @Nonnull final Logger log,
            @Nonnull final AuthCallbacks authCallbacks,
            @Nonnull final StorageCallbacks storageCallbacks,
            @Nonnull final ServiceCallbacks serviceCallbacks,
            @Nonnull final StreamCallbacks streamCallbacks,
            final boolean useInstrumentedAllocators) {

        super(log);

        mAuthCallbacks = checkNotNull(authCallbacks);
        mStorageCallbacks = checkNotNull(storageCallbacks);
        mServiceCallbacks = checkNotNull(serviceCallbacks);
        mStreamCallbacks = checkNotNull(streamCallbacks);

        mMediaSourceToStreamMap = new HashMap<MediaSource, KinesisVideoProducerStream>();
        mUseInstrumentedAllocators = useInstrumentedAllocators;
    }

    /**
     * Initializes the client object.
     */
    @Override
    public void initialize(@Nonnull final DeviceInfo deviceInfo) throws KinesisVideoException {
        kinesisVideoProducer = initializeNewKinesisVideoProducer(deviceInfo);
        super.initialize(deviceInfo);
    }

    @Override
    public void registerMediaSource(@Nonnull final MediaSource mediaSource) throws KinesisVideoException {
        Preconditions.checkNotNull(mediaSource);
        StreamCallbacks streamCallbacks = mediaSource.getStreamCallbacks();
        if (streamCallbacks == null) {
            streamCallbacks = mStreamCallbacks;
        }

        final KinesisVideoProducerStream producerStream = kinesisVideoProducer.createStreamSync(mediaSource.getStreamInfo(), streamCallbacks);
        mediaSource.initialize(new ProducerStreamSink(producerStream));
        mServiceCallbacks.addStream(producerStream);
        mMediaSourceToStreamMap.put(mediaSource, producerStream);
        super.registerMediaSource(mediaSource);
    }

    @Override
    public void registerMediaSourceAsync(@Nonnull final MediaSource mediaSource) throws KinesisVideoException {
        Preconditions.checkNotNull(mediaSource);
        StreamCallbacks streamCallbacks = mediaSource.getStreamCallbacks();
        if (streamCallbacks == null) {
            streamCallbacks = mStreamCallbacks;
        }

        final KinesisVideoProducerStream producerStream = kinesisVideoProducer.createStream(mediaSource.getStreamInfo(), streamCallbacks);
        mediaSource.initialize(new ProducerStreamSink(producerStream));
        mServiceCallbacks.addStream(producerStream);
        mMediaSourceToStreamMap.put(mediaSource, producerStream);
        super.registerMediaSource(mediaSource);
    }

    @Override
    public void unregisterMediaSource(@Nonnull final MediaSource mediaSource) throws KinesisVideoException {
        Preconditions.checkNotNull(mediaSource);
        mediaSource.stop();
        super.unregisterMediaSource(mediaSource);

        final KinesisVideoProducerStream producerStream = mMediaSourceToStreamMap.remove(mediaSource);
        try {
            // The following call will blocked till the stopped event completes
            producerStream.stopStreamSync();
        } finally {
            kinesisVideoProducer.freeStream(producerStream);
            mServiceCallbacks.removeStream(producerStream);
        }
    }

    @Override
    public void freeMediaSource(@Nonnull final MediaSource mediaSource) throws KinesisVideoException {
        Preconditions.checkNotNull(mediaSource);
        super.freeMediaSource(mediaSource);

        final KinesisVideoProducerStream producerStream = mMediaSourceToStreamMap.remove(mediaSource);
        try {
            // The following call will not blocked during the stopped event
            producerStream.streamClosed(INVALID_UPLOAD_HANDLE_VALUE);
        } finally {
            kinesisVideoProducer.freeStream(producerStream);
            mServiceCallbacks.removeStream(producerStream);
        }
    }

    @Override
    public void stopAllMediaSources() throws KinesisVideoException {
        super.stopAllMediaSources();
        try {
            for (final MediaSource mediaSource : mMediaSources) {
                final KinesisVideoProducerStream producerStream = mMediaSourceToStreamMap.get(mediaSource);
                try {
                    producerStream.stopStreamSync();
                } catch (final KinesisVideoException e) {
                    mLog.error("Failed to stop media source {} due to Exception.", mediaSource);
                    mLog.error(e);
                }
            }
        } finally {
            mMediaSourceToStreamMap.clear();
        }
    }

    @Override
    public MediaSource createMediaSource(final String streamName,
                                         final MediaSourceConfiguration mediaSourceConfiguration)
            throws KinesisVideoException {

        throw new KinesisVideoException("creating media sources is not implemented yet");
    }

    @Override
    public List<MediaSourceConfiguration.Builder<? extends MediaSourceConfiguration>> listSupportedConfigurations() {
        return Collections.emptyList();
    }

    @Override
    public void free() throws KinesisVideoException {
        if (isInitialized()) {
            super.free();

            mServiceCallbacks.free();
            kinesisVideoProducer.stopStreams();
            kinesisVideoProducer.free();

            mIsInitialized = false;
        }
    }

    /**
     * Initialize a new native {@link com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducer}.
     * Used internally by {@link #initialize} and visible for testing.
     */
    @Nonnull
    public KinesisVideoProducer initializeNewKinesisVideoProducer(final DeviceInfo deviceInfo) throws ProducerException {
        final KinesisVideoProducer kinesisVideoProducer = new NativeKinesisVideoProducerJni(
                mAuthCallbacks,
                mStorageCallbacks,
                mServiceCallbacks,
                mLog,
                new CountDownLatch(1),
                mUseInstrumentedAllocators);
        kinesisVideoProducer.createSync(deviceInfo);
        return kinesisVideoProducer;
    }

    /**
     * Query the PIC malloc tracker for the current allocated bytes.
     * Note this is shared for all streams.
     *
     * @return The current malloc'd memory for the KVS native codebase (global), in bytes.
     */
    public long getCurrentAllocationSizeBytes() {
        if (!mUseInstrumentedAllocators) {
            throw new IllegalStateException("Instrumented allocators from PIC are not enabled");
        }
        return ((NativeKinesisVideoProducerJni) kinesisVideoProducer).getCurrentAllocationBytes();
    }

    /**
     * Get metrics for the producer client.
     *
     * @return Client metrics
     */
    public Optional<KinesisVideoMetrics> getClientMetrics() {
        try {
            return Optional.of(kinesisVideoProducer.getMetrics());
        } catch (final KinesisVideoException ex) {
            mLog.error("getClientMetrics failed with exception", ex);
            return Optional.empty();
        }
    }

    /**
     * Get metrics for a particular stream.
     *
     * @param streamName name of the stream to fetch metrics of
     * @return The metrics
     * @throws IllegalArgumentException if the media source is not registered
     */
    public Optional<KinesisVideoStreamMetrics> getStreamMetrics(@Nonnull final String streamName) {

        final MediaSource mediaSource = mMediaSources.stream()
                .filter(ms -> {
                    try {
                        return streamName.equals(ms.getStreamInfo().getName());
                    } catch (final KinesisVideoException e) {
                        return false;
                    }
                })
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No stream found for media source " + streamName));

        final long streamHandle = mMediaSourceToStreamMap.get(mediaSource).getStreamHandle();
        final KinesisVideoStreamMetrics metrics = new KinesisVideoStreamMetrics();
        try {
            ((NativeKinesisVideoProducerJni) kinesisVideoProducer).getStreamMetrics(streamHandle, metrics);
            return Optional.of(metrics);
        } catch (final KinesisVideoException e) {
            mLog.error("Failed to get stream metrics for media source {}", mediaSource);
            return Optional.empty();
        }
    }

    /**
     * Returns a copy of the current registered streams.
     *
     * @return A list of the stream names currently registered.
     */
    public List<String> getStreamNames() {
        return mMediaSources.stream()
                .map(mediaSource -> {
                    try {
                        return mediaSource.getStreamInfo().getName();
                    } catch (final KinesisVideoException e) {
                        return null;
                    }
                })
                .filter(s -> !StringUtils.isNullOrEmpty(s))
                .collect(Collectors.toList());
    }
}
