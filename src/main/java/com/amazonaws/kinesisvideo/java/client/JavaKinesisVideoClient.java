package com.amazonaws.kinesisvideo.java.client;

import com.amazonaws.kinesisvideo.auth.DefaultAuthCallbacks;
import com.amazonaws.kinesisvideo.client.KinesisVideoClientConfiguration;
import com.amazonaws.kinesisvideo.internal.client.NativeKinesisVideoClient;
import com.amazonaws.kinesisvideo.common.logging.Log;
import com.amazonaws.kinesisvideo.internal.producer.ServiceCallbacks;
import com.amazonaws.kinesisvideo.producer.StreamCallbacks;
import com.amazonaws.kinesisvideo.internal.producer.client.KinesisVideoServiceClient;
import com.amazonaws.kinesisvideo.internal.service.DefaultServiceCallbacksImpl;

import javax.annotation.Nonnull;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Implement Kinesis Video Client interface for Java.
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
public final class JavaKinesisVideoClient extends NativeKinesisVideoClient {

    public JavaKinesisVideoClient(
            @Nonnull final Log log,
            @Nonnull final KinesisVideoClientConfiguration configuration,
            @Nonnull final KinesisVideoServiceClient serviceClient,
            @Nonnull final ScheduledExecutorService executor) {
        super(log,
                configuration,
                serviceClient,
                executor);
    }

    public JavaKinesisVideoClient(
            @Nonnull final Log log,
            @Nonnull final KinesisVideoClientConfiguration configuration,
            @Nonnull final KinesisVideoServiceClient serviceClient,
            @Nonnull final ScheduledExecutorService executor,
            @Nonnull final StreamCallbacks streamCallbacks) {
        this(log,
                configuration,
                new DefaultServiceCallbacksImpl(log, executor, configuration, serviceClient),
                executor,
                streamCallbacks);
    }

    public JavaKinesisVideoClient(
            @Nonnull final Log log,
            @Nonnull final KinesisVideoClientConfiguration configuration,
            @Nonnull final ServiceCallbacks serviceCallbacks,
            @Nonnull final ScheduledExecutorService executor,
            @Nonnull final StreamCallbacks streamCallbacks) {
        super(log,
                new DefaultAuthCallbacks(configuration.getCredentialsProvider(),
                        executor,
                        log),
                configuration.getStorageCallbacks(),
                serviceCallbacks,
                streamCallbacks);
    }
}
