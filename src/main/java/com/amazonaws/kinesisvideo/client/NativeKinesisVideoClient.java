package com.amazonaws.kinesisvideo.client;

import static com.amazonaws.kinesisvideo.common.preconditions.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import javax.annotation.Nonnull;

import com.amazonaws.kinesisvideo.auth.DefaultAuthCallbacks;
import com.amazonaws.kinesisvideo.client.mediasource.MediaSource;
import com.amazonaws.kinesisvideo.client.mediasource.MediaSourceConfiguration;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.common.logging.Log;
import com.amazonaws.kinesisvideo.common.logging.LogLevel;
import com.amazonaws.kinesisvideo.mediasource.ProducerStreamSink;
import com.amazonaws.kinesisvideo.producer.AuthCallbacks;
import com.amazonaws.kinesisvideo.producer.DeviceInfo;
import com.amazonaws.kinesisvideo.producer.KinesisVideoProducer;
import com.amazonaws.kinesisvideo.producer.KinesisVideoProducerStream;
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
     * Kinesis Video producer callbacks
     */
    private final AuthCallbacks authCallbacks;
    private final StorageCallbacks storageCallbacks;
    private final StreamCallbacks streamCallbacks;
    private final DefaultServiceCallbacksImpl defaultServiceCallbacks;
    private final List<MediaSource> mediaSources;

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

        this.authCallbacks = checkNotNull(authCallbacks);
        this.storageCallbacks = checkNotNull(storageCallbacks);
        defaultServiceCallbacks = (DefaultServiceCallbacksImpl) checkNotNull(serviceCallbacks);
        this.streamCallbacks = checkNotNull(streamCallbacks);

        mediaSources = new ArrayList<MediaSource>();
    }

    /**
     * Initializes the client object.
     */
    @Override
    public void initialize(@Nonnull final DeviceInfo deviceInfo) throws KinesisVideoException {
        // Create the producer object
        kinesisVideoProducer = new NativeKinesisVideoProducerJni(
                authCallbacks,
                storageCallbacks,
                defaultServiceCallbacks,
                mLog);

        kinesisVideoProducer.createSync(deviceInfo);

        super.initialize(deviceInfo);
    }

    @Override
    public void registerMediaSource(final String streamName,
                                    final MediaSource mediaSource) throws KinesisVideoException {
        final KinesisVideoProducerStream producerStream = kinesisVideoProducer.createStreamSync(mediaSource.getStreamInfo(streamName), streamCallbacks);
        mediaSources.add(mediaSource);
        mediaSource.initialize(new ProducerStreamSink(producerStream));
        defaultServiceCallbacks.addStream(producerStream);
    }

    @Override
    public void stopAllMediaSources() throws KinesisVideoException {
        super.stopAllMediaSources();
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

            defaultServiceCallbacks.free();
            kinesisVideoProducer.stopStreams();
            kinesisVideoProducer.free();

            mIsInitialized = false;
        }
    }
}
