package com.amazonaws.kinesisvideo.client;

import static com.amazonaws.kinesisvideo.common.preconditions.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import javax.annotation.Nonnull;

import com.amazonaws.kinesisvideo.auth.DefaultAuthCallbacks;
import com.amazonaws.kinesisvideo.client.mediasource.MediaSource;
import com.amazonaws.kinesisvideo.client.mediasource.MediaSourceConfiguration;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.common.logging.Log;
import com.amazonaws.kinesisvideo.common.logging.LogLevel;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.mediasource.ProducerStreamSink;
import com.amazonaws.kinesisvideo.producer.AuthCallbacks;
import com.amazonaws.kinesisvideo.producer.DeviceInfo;
import com.amazonaws.kinesisvideo.producer.KinesisVideoProducer;
import com.amazonaws.kinesisvideo.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.ServiceCallbacks;
import com.amazonaws.kinesisvideo.producer.StorageCallbacks;
import com.amazonaws.kinesisvideo.producer.StreamCallbacks;
import com.amazonaws.kinesisvideo.producer.client.KinesisVideoServiceClient;
import com.amazonaws.kinesisvideo.producer.jni.NativeKinesisVideoProducerJni;
import com.amazonaws.kinesisvideo.service.DefaultServiceCallbacksImpl;
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
     * Underlying Kinesis Video producer object.
     */
    private KinesisVideoProducer kinesisVideoProducer;

    public NativeKinesisVideoClient(
            @Nonnull final KinesisVideoClientConfiguration configuration,
            @Nonnull final KinesisVideoServiceClient serviceClient,
            @Nonnull final ScheduledExecutorService executor) {
        this(new Log(configuration.getLogChannel(), LogLevel.VERBOSE, TAG),
                configuration,
                serviceClient,
                executor);
    }

    public NativeKinesisVideoClient(
            @Nonnull final Log log,
            @Nonnull final KinesisVideoClientConfiguration configuration,
            @Nonnull final KinesisVideoServiceClient serviceClient,
            @Nonnull final ScheduledExecutorService executor) {
        this(log,
                new DefaultAuthCallbacks(configuration.getCredentialsProvider(),
                        executor,
                        log),
                configuration.getStorageCallbacks(),
                new DefaultServiceCallbacksImpl(log, executor, configuration, serviceClient),
                new DefaultStreamCallbacks());
    }

    public NativeKinesisVideoClient(
            @Nonnull final Log log,
            @Nonnull final AuthCallbacks authCallbacks,
            @Nonnull final StorageCallbacks storageCallbacks,
            @Nonnull final ServiceCallbacks serviceCallbacks,
            @Nonnull final StreamCallbacks streamCallbacks) {

        super(log);

        mAuthCallbacks = checkNotNull(authCallbacks);
        mStorageCallbacks = checkNotNull(storageCallbacks);
        mServiceCallbacks = checkNotNull(serviceCallbacks);
        mStreamCallbacks = checkNotNull(streamCallbacks);

        mMediaSourceToStreamMap = new HashMap<MediaSource, KinesisVideoProducerStream>();
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
    public void registerMediaSource(final MediaSource mediaSource) throws KinesisVideoException {
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
    public void unregisterMediaSource(final MediaSource mediaSource) throws KinesisVideoException {
        Preconditions.checkNotNull(mediaSource);
        super.unregisterMediaSource(mediaSource);

        final KinesisVideoProducerStream producerStream = mMediaSourceToStreamMap.get(mediaSource);

        // The following call will not block for the stopped event
        producerStream.stopStream();

        kinesisVideoProducer.freeStream(producerStream);
        mServiceCallbacks.removeStream(producerStream);
    }

    @Override
    public void stopAllMediaSources() throws KinesisVideoException {
        super.stopAllMediaSources();
        for (final MediaSource mediaSource : mMediaSources) {
            final KinesisVideoProducerStream producerStream = mMediaSourceToStreamMap.get(mediaSource);
            producerStream.stopStreamSync();
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
     * Initialize a new native {@link com.amazonaws.kinesisvideo.producer.KinesisVideoProducer}.
     * Used internally by {@link #initialize} and visible for testing.
     */
    @Nonnull
    KinesisVideoProducer initializeNewKinesisVideoProducer(final DeviceInfo deviceInfo) throws ProducerException {
        final KinesisVideoProducer kinesisVideoProducer = new NativeKinesisVideoProducerJni(
                mAuthCallbacks,
                mStorageCallbacks,
                mServiceCallbacks,
                mLog);
        kinesisVideoProducer.createSync(deviceInfo);
        return kinesisVideoProducer;
    }
}
