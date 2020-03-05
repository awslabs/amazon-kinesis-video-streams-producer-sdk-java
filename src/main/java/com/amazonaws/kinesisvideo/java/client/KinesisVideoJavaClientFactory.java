package com.amazonaws.kinesisvideo.java.client;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentialsProvider;
import com.amazonaws.kinesisvideo.client.KinesisVideoClient;
import com.amazonaws.kinesisvideo.client.KinesisVideoClientConfiguration;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.common.logging.Log;
import com.amazonaws.kinesisvideo.common.logging.LogLevel;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.internal.producer.ServiceCallbacks;
import com.amazonaws.kinesisvideo.internal.service.DefaultServiceCallbacksImpl;
import com.amazonaws.kinesisvideo.java.auth.JavaCredentialsProviderImpl;
import com.amazonaws.kinesisvideo.java.logging.SysOutLogChannel;
import com.amazonaws.kinesisvideo.java.service.JavaKinesisVideoServiceClient;
import com.amazonaws.kinesisvideo.producer.DeviceInfo;
import com.amazonaws.kinesisvideo.producer.StorageInfo;
import com.amazonaws.kinesisvideo.producer.StreamCallbacks;
import com.amazonaws.kinesisvideo.producer.Tag;
import com.amazonaws.kinesisvideo.storage.DefaultStorageCallbacks;
import com.amazonaws.kinesisvideo.streaming.DefaultStreamCallbacks;
import com.amazonaws.regions.Regions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public final class KinesisVideoJavaClientFactory {
    private static final int DEVICE_VERSION = 0;
    private static final int TEN_STREAMS = 10;
    private static final int SPILL_RATIO_90_PERCENT = 90;
    private static final int STORAGE_SIZE_64_MEGS = 1024 * 1024 * 1024;
    private static final String DEVICE_NAME = "java-demo-application";
    private static final String STORAGE_PATH = "/tmp";
    private static final int NUMBER_OF_THREADS_IN_POOL = 2;

    private KinesisVideoJavaClientFactory() {
        throw new UnsupportedOperationException();
    }

    /**
     * Create Kinesis Video client.
     *
     * @param credentialsProvider Credentials provider
     * @return
     * @throws KinesisVideoException
     */
    @Nonnull
    public static KinesisVideoClient createKinesisVideoClient(@Nonnull final AWSCredentialsProvider credentialsProvider)
            throws KinesisVideoException {
        Preconditions.checkNotNull(credentialsProvider);
        return createKinesisVideoClient(Regions.DEFAULT_REGION, credentialsProvider);
    }

    /**
     * Create Kinesis Video client.
     *
     * @param regions Regions object
     * @param awsCredentialsProvider Credentials provider
     * @return
     * @throws KinesisVideoException
     */
    @Nonnull
    public static KinesisVideoClient createKinesisVideoClient(
            @Nonnull final Regions regions,
            @Nonnull final AWSCredentialsProvider awsCredentialsProvider)
            throws KinesisVideoException {
        Preconditions.checkNotNull(regions);
        Preconditions.checkNotNull(awsCredentialsProvider);

        final KinesisVideoCredentialsProvider kinesisVideoCredentialsProvider =
                new JavaCredentialsProviderImpl(awsCredentialsProvider);

        final KinesisVideoClientConfiguration configuration = KinesisVideoClientConfiguration.builder()
                .withRegion(regions.getName())
                .withCredentialsProvider(kinesisVideoCredentialsProvider)
                .withLogChannel(new SysOutLogChannel())
                .withStorageCallbacks(new DefaultStorageCallbacks())
                .build();

        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(NUMBER_OF_THREADS_IN_POOL,
                new ThreadFactoryBuilder().setNameFormat("KVS-JavaClientExecutor-%d").build());

        return createKinesisVideoClient(configuration,
                getDeviceInfo(),
                executor);
    }

    /**
     * Create Kinesis Video client.
     */
    @Nonnull
    public static KinesisVideoClient createKinesisVideoClient(
            @Nonnull final KinesisVideoClientConfiguration configuration,
            @Nonnull final DeviceInfo deviceInfo,
            @Nonnull final ScheduledExecutorService executor)
            throws KinesisVideoException {
        Preconditions.checkNotNull(configuration);
        Preconditions.checkNotNull(deviceInfo);
        Preconditions.checkNotNull(executor);

        final Log log = new Log(configuration.getLogChannel(), LogLevel.DEBUG, "KinesisVideo");

        final JavaKinesisVideoServiceClient serviceClient = new JavaKinesisVideoServiceClient(log);

        final KinesisVideoClient kinesisVideoClient = new JavaKinesisVideoClient(log,
                configuration,
                serviceClient,
                executor);

        kinesisVideoClient.initialize(deviceInfo);

        return kinesisVideoClient;
    }

    /**
     * Create Kinesis Video client.
     */
    @Nonnull
    public static KinesisVideoClient createKinesisVideoClient(
            @Nonnull final KinesisVideoClientConfiguration configuration,
            @Nonnull final DeviceInfo deviceInfo,
            @Nonnull final ScheduledExecutorService executor,
            @Nonnull final StreamCallbacks streamCallbacks)
            throws KinesisVideoException {
        Preconditions.checkNotNull(configuration);
        Preconditions.checkNotNull(deviceInfo);
        Preconditions.checkNotNull(executor);

        final Log log = new Log(configuration.getLogChannel(), LogLevel.DEBUG, "KinesisVideo");

        final JavaKinesisVideoServiceClient serviceClient = new JavaKinesisVideoServiceClient(log);

        final KinesisVideoClient kinesisVideoClient = new JavaKinesisVideoClient(log,
                configuration,
                serviceClient,
                executor,
                streamCallbacks);

        kinesisVideoClient.initialize(deviceInfo);

        return kinesisVideoClient;
    }

    @Nonnull
    public static KinesisVideoClient createKinesisVideoClient(
            @Nonnull final Log log,
            @Nonnull final KinesisVideoClientConfiguration configuration,
            @Nonnull final ScheduledExecutorService executor,
            @Nullable final StreamCallbacks streamCallbacks,
            @Nullable final ServiceCallbacks serviceCallbacks)
            throws KinesisVideoException {
        Preconditions.checkNotNull(configuration);
        Preconditions.checkNotNull(executor);

        final KinesisVideoClient kinesisVideoClient = new JavaKinesisVideoClient(log,
                configuration,
                serviceCallbacks == null ? new DefaultServiceCallbacksImpl(log, executor, configuration,
                        new JavaKinesisVideoServiceClient(log)) : serviceCallbacks,
                executor,
                streamCallbacks == null ? new DefaultStreamCallbacks() : streamCallbacks);

        kinesisVideoClient.initialize(getDeviceInfo());

        return kinesisVideoClient;
    }

    private static DeviceInfo getDeviceInfo() {
        return new DeviceInfo(
                DEVICE_VERSION,
                DEVICE_NAME,
                getStorageInfo(),
                TEN_STREAMS,
                getDeviceTags());
    }

    private static StorageInfo getStorageInfo() {
        return new StorageInfo(0,
                StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM,
                STORAGE_SIZE_64_MEGS,
                SPILL_RATIO_90_PERCENT,
                STORAGE_PATH);
    }

    private static Tag[] getDeviceTags() {
        // Tags for devices are not supported yet

        return null;
    }
}
