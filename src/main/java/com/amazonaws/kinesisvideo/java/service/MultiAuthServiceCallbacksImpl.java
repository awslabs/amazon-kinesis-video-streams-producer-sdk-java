package com.amazonaws.kinesisvideo.java.service;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentials;
import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentialsProvider;
import com.amazonaws.kinesisvideo.client.KinesisVideoClientConfiguration;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.internal.producer.client.KinesisVideoServiceClient;
import com.amazonaws.kinesisvideo.internal.service.DefaultServiceCallbacksImpl;
import com.amazonaws.kinesisvideo.java.auth.JavaCredentialsFactory;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.StreamDescription;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.kinesisvideo.producer.Tag;
import com.amazonaws.kinesisvideo.producer.Time;
import com.amazonaws.kinesisvideo.util.LoggedExitRunnable;
import org.apache.logging.log4j.LogManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.HTTP_NOT_SET;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.HTTP_OK;

public class MultiAuthServiceCallbacksImpl extends DefaultServiceCallbacksImpl {

    /**
     * Given a StreamInfo object, determine which credentials to use.
     */
    protected Function<StreamInfo, AWSCredentialsProvider> credentialsProviderFn;

    /**
     * @param credentialsProviderFn Used to determine which AWS credentials to use per stream. You can extend StreamInfo
     *                              and add additional metadata for your business logic.
     */
    @SuppressWarnings({"ConstantConditions"})
    public MultiAuthServiceCallbacksImpl(@Nonnull final ScheduledExecutorService executor,
                                         @Nonnull final KinesisVideoClientConfiguration configuration,
                                         @Nonnull final KinesisVideoServiceClient kinesisVideoServiceClient,
                                         @Nonnull final Function<StreamInfo, AWSCredentialsProvider> credentialsProviderFn) {
        super(LogManager.getLogger(MultiAuthServiceCallbacksImpl.class), executor, configuration, kinesisVideoServiceClient);

        Preconditions.checkArgument(credentialsProviderFn != null, "credentialsProviderFn can't be null");
        this.credentialsProviderFn = credentialsProviderFn;
    }

    @Override
    public void createStream(@Nonnull final String deviceName,
                             @Nonnull final String streamName,
                             @Nonnull final String contentType,
                             @Nullable final String kmsKeyId,
                             final long retentionPeriod,
                             final long callAfter,
                             final long timeout,
                             @Nullable final byte[] authData,
                             final int authType,
                             final KinesisVideoProducerStream stream)
            throws ProducerException {

        Preconditions.checkState(isInitialized(), "Service callbacks object should be initialized first");

        final String methodName = "CreateStream";
        final String taskName = String.format("%s-%s", methodName, stream.getStreamInfo().getSummary());

        this.executor.schedule(new LoggedExitRunnable(taskName) {
            @Override
            public void execute() {
                final StreamInfo streamInfo = stream.getStreamInfo();

                final AWSCredentialsProvider awsCredentialsProvider;
                try {
                    awsCredentialsProvider = MultiAuthServiceCallbacksImpl.this.credentialsProviderFn.apply(streamInfo);
                } catch (final Throwable credentialsProviderFnThrowable) {
                    MultiAuthServiceCallbacksImpl.this.log.error("[{}] {}'s credentialsProviderFn threw an error", streamInfo.getSummary(), methodName, credentialsProviderFnThrowable);
                    try {
                        MultiAuthServiceCallbacksImpl.this.kinesisVideoProducer.createStreamResult(stream, null, HTTP_NOT_SET);
                    } catch (final ProducerException ex) {
                        notifyCallResult(stream, methodName, ex);
                    }
                    return;
                }

                final KinesisVideoCredentialsProvider kvsCredentialsProvider = JavaCredentialsFactory.createKinesisVideoCredentialsProvider(awsCredentialsProvider);

                String streamArn = null;
                int httpStatusCode = HTTP_NOT_SET;
                try {
                    MultiAuthServiceCallbacksImpl.this.log.info("[{}] Creating stream", streamInfo.getSummary());
                    streamArn = MultiAuthServiceCallbacksImpl.this.kinesisVideoServiceClient.createStream(streamName, deviceName, contentType, kmsKeyId,
                            retentionPeriod, timeout, kvsCredentialsProvider);
                    httpStatusCode = HTTP_OK;
                } catch (final Throwable createStreamThrowable) {
                    MultiAuthServiceCallbacksImpl.this.log.error("[{}] An error occurred while creating stream", streamInfo.getSummary(), createStreamThrowable);
                    httpStatusCode = getStatusCodeFromException(createStreamThrowable);
                } finally {
                    try {
                        MultiAuthServiceCallbacksImpl.this.kinesisVideoProducer.createStreamResult(stream, streamArn, httpStatusCode);
                    } catch (final ProducerException ex) {
                        notifyCallResult(stream, methodName, ex);
                    }
                }
            }
        }, calculateRelativeServiceCallAfter(callAfter), TimeUnit.NANOSECONDS);
    }

    @Override
    public void describeStream(
            @Nonnull final String streamName,
            final long callAfter,
            final long timeout,
            @Nullable final byte[] authData,
            final int authType,
            final long streamHandle,
            final KinesisVideoProducerStream stream) throws ProducerException {

        Preconditions.checkState(isInitialized(), "Service callbacks object should be initialized first");

        final String methodName = "DescribeStream";
        final String taskName = String.format("%s-%s", methodName, stream.getStreamInfo().getSummary());

        this.executor.schedule(new LoggedExitRunnable(taskName) {
            @Override
            public void execute() {
                final StreamInfo streamInfo = stream.getStreamInfo();

                final AWSCredentialsProvider awsCredentialsProvider;
                try {
                    awsCredentialsProvider = MultiAuthServiceCallbacksImpl.this.credentialsProviderFn.apply(streamInfo);
                } catch (final Throwable credentialsProviderFnThrowable) {
                    MultiAuthServiceCallbacksImpl.this.log.error("[{}] {}'s credentialsProviderFn threw an error", streamInfo.getSummary(), methodName, credentialsProviderFnThrowable);
                    try {
                        MultiAuthServiceCallbacksImpl.this.kinesisVideoProducer.describeStreamResult(stream, streamHandle, null, HTTP_NOT_SET);
                    } catch (final ProducerException ex) {
                        notifyCallResult(stream, methodName, ex);
                    }
                    return;
                }

                final KinesisVideoCredentialsProvider kvsCredentialsProvider = JavaCredentialsFactory.createKinesisVideoCredentialsProvider(awsCredentialsProvider);

                StreamDescription streamDescription = null;
                int httpStatusCode = HTTP_NOT_SET;
                try {
                    MultiAuthServiceCallbacksImpl.this.log.info("[{}] Describe stream", streamInfo.getSummary());
                    streamDescription = MultiAuthServiceCallbacksImpl.this.kinesisVideoServiceClient.describeStream(streamName, timeout, kvsCredentialsProvider);
                    httpStatusCode = HTTP_OK;
                } catch (final Throwable describeStreamThrowable) {
                    MultiAuthServiceCallbacksImpl.this.log.error("[{}] An error occurred while describing stream", streamInfo.getSummary(), describeStreamThrowable);
                    httpStatusCode = getStatusCodeFromException(describeStreamThrowable);
                } finally {
                    try {
                        MultiAuthServiceCallbacksImpl.this.kinesisVideoProducer.describeStreamResult(stream, streamHandle, streamDescription, httpStatusCode);
                    } catch (final ProducerException ex) {
                        notifyCallResult(stream, methodName, ex);
                    }
                }
            }
        }, calculateRelativeServiceCallAfter(callAfter), TimeUnit.NANOSECONDS);
    }

    @Override
    public void getStreamingEndpoint(
            @Nonnull final String streamName,
            @Nonnull final String apiName,
            final long callAfter,
            final long timeout,
            @Nullable final byte[] authData,
            final int authType,
            final long streamHandle,
            final KinesisVideoProducerStream stream) throws ProducerException {

        Preconditions.checkState(isInitialized(), "Service callbacks object should be initialized first");

        final String methodName = "GetDataEndpoint";
        final String taskName = String.format("%s-%s", methodName, stream.getStreamInfo().getSummary());

        this.executor.schedule(new LoggedExitRunnable(taskName) {
            @Override
            public void execute() {
                final StreamInfo streamInfo = stream.getStreamInfo();

                final AWSCredentialsProvider awsCredentialsProvider;
                try {
                    awsCredentialsProvider = MultiAuthServiceCallbacksImpl.this.credentialsProviderFn.apply(streamInfo);
                } catch (final Throwable credentialsProviderFnThrowable) {
                    MultiAuthServiceCallbacksImpl.this.log.error("[{}] {}'s credentialsProviderFn threw an error", streamInfo.getSummary(), methodName, credentialsProviderFnThrowable);
                    try {
                        MultiAuthServiceCallbacksImpl.this.kinesisVideoProducer.getStreamingEndpointResult(stream, streamHandle, null, HTTP_NOT_SET);
                    } catch (final ProducerException ex) {
                        notifyCallResult(stream, methodName, ex);
                    }
                    return;
                }

                final KinesisVideoCredentialsProvider kvsCredentialsProvider = JavaCredentialsFactory.createKinesisVideoCredentialsProvider(awsCredentialsProvider);

                String endpoint = null;
                int httpStatusCode = HTTP_NOT_SET;
                try {
                    MultiAuthServiceCallbacksImpl.this.log.info("[{}] Get {} endpoint", apiName, streamInfo.getSummary());
                    endpoint = MultiAuthServiceCallbacksImpl.this.kinesisVideoServiceClient.getDataEndpoint(streamName, apiName, timeout, kvsCredentialsProvider);
                    httpStatusCode = HTTP_OK;
                } catch (final Throwable getStreamingEndpointThrowable) {
                    MultiAuthServiceCallbacksImpl.this.log.error("[{}] An error occurred while getting data endpoint", streamInfo.getSummary(), getStreamingEndpointThrowable);
                    httpStatusCode = getStatusCodeFromException(getStreamingEndpointThrowable);
                } finally {
                    try {
                        MultiAuthServiceCallbacksImpl.this.kinesisVideoProducer.getStreamingEndpointResult(stream, streamHandle, endpoint, httpStatusCode);
                    } catch (final ProducerException ex) {
                        notifyCallResult(stream, methodName, ex);
                    }
                }
            }
        }, calculateRelativeServiceCallAfter(callAfter), TimeUnit.NANOSECONDS);
    }

    @Override
    public void getStreamingToken(
            @Nonnull final String streamName,
            final long callAfter,
            final long timeout,
            @Nullable final byte[] authData,
            final int authType,
            final long streamHandle,
            final KinesisVideoProducerStream stream) throws ProducerException {

        Preconditions.checkState(isInitialized(), "Service callbacks object should be initialized first");

        final String methodName = "GetStreamingToken";
        final StreamInfo streamInfo = stream.getStreamInfo();

        final AWSCredentialsProvider awsCredentialsProvider;
        try {
            awsCredentialsProvider = MultiAuthServiceCallbacksImpl.this.credentialsProviderFn.apply(streamInfo);
        } catch (final Throwable credentialsProviderFnThrowable) {
            this.log.error("[{}] {}'s credentialsProviderFn threw an error", streamInfo.getSummary(), methodName, credentialsProviderFnThrowable);
            try {
                MultiAuthServiceCallbacksImpl.this.kinesisVideoProducer.getStreamingEndpointResult(stream, streamHandle, null, HTTP_NOT_SET);
            } catch (final ProducerException ex) {
                notifyCallResult(stream, methodName, ex);
            }
            return;
        }

        final KinesisVideoCredentialsProvider kvsCredentialsProvider = JavaCredentialsFactory.createKinesisVideoCredentialsProvider(awsCredentialsProvider);

        int httpStatusCode = HTTP_NOT_SET;
        byte[] serializedCredentials = null;
        long expirationHundredsOfNanos = 0;
        try {
            final KinesisVideoCredentials credentials = kvsCredentialsProvider.getUpdatedCredentials();
            Preconditions.checkNotNull(credentials, String.format("[%s] %s's getUpdatedCredentials returned null credentials!", streamInfo.getSummary(), methodName));

            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (final ObjectOutput outputStream = new ObjectOutputStream(baos)) {
                outputStream.writeObject(credentials);
                outputStream.flush();
                serializedCredentials = baos.toByteArray();
            }
            baos.close();

            if (credentials.isTemporary()) {
                expirationHundredsOfNanos = credentials.getExpiration().getTime() * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
            } else {
                expirationHundredsOfNanos = credentials.getExpiration().getTime(); // Already max long value
            }
            httpStatusCode = HTTP_OK;
        } catch (final Throwable t) {
            this.log.error("[{}] {} An error occurred while processing the credentials",
                    streamInfo.getSummary(), methodName, t);
            httpStatusCode = getStatusCodeFromException(t);
        } finally {
            try {
                MultiAuthServiceCallbacksImpl.this.kinesisVideoProducer.getStreamingTokenResult(stream, streamHandle,
                        serializedCredentials, expirationHundredsOfNanos, httpStatusCode);
            } catch (final ProducerException ex) {
                notifyCallResult(stream, methodName, ex);
            }
        }
    }

    @Override
    public void tagResource(@Nonnull final String resourceArn,
                            @Nullable final Tag[] tags,
                            final long callAfter,
                            final long timeout,
                            @Nullable final byte[] authData,
                            final int authType,
                            final long streamHandle,
                            final KinesisVideoProducerStream stream) throws ProducerException {

        Preconditions.checkState(isInitialized(), "Service callbacks object should be initialized first");

        final String methodName = "TagStream";

        // No-op if nothing to tag
        if (tags == null) {
            try {
                MultiAuthServiceCallbacksImpl.this.kinesisVideoProducer.tagResourceResult(stream, streamHandle, HTTP_OK);
            } catch (final ProducerException ex) {
                notifyCallResult(stream, methodName, ex);
            }
            return;
        }

        final String taskName = String.format("%s-%s", methodName, stream.getStreamInfo().getSummary());
        this.executor.schedule(new LoggedExitRunnable(taskName) {
            @Override
            public void execute() {
                final StreamInfo streamInfo = stream.getStreamInfo();

                final AWSCredentialsProvider awsCredentialsProvider;
                try {
                    awsCredentialsProvider = MultiAuthServiceCallbacksImpl.this.credentialsProviderFn.apply(streamInfo);
                } catch (final Throwable credentialsProviderFnThrowable) {
                    MultiAuthServiceCallbacksImpl.this.log.error("[{}] {}'s credentialsProviderFn threw an error", streamInfo.getSummary(), methodName, credentialsProviderFnThrowable);
                    try {
                        MultiAuthServiceCallbacksImpl.this.kinesisVideoProducer.tagResourceResult(stream, streamHandle, HTTP_NOT_SET);
                    } catch (final ProducerException ex) {
                        notifyCallResult(stream, methodName, ex);
                    }
                    return;
                }

                final KinesisVideoCredentialsProvider kvsCredentialsProvider = JavaCredentialsFactory.createKinesisVideoCredentialsProvider(awsCredentialsProvider);
                final Map<String, String> tagsMap = Arrays.stream(tags)
                        .collect(Collectors.toMap(Tag::getName, Tag::getValue));

                int httpStatusCode = HTTP_NOT_SET;
                try {
                    MultiAuthServiceCallbacksImpl.this.log.info("[{}] {} number of tags: {}", streamInfo.getSummary(), methodName, tagsMap.size());
                    MultiAuthServiceCallbacksImpl.this.kinesisVideoServiceClient.tagStream(resourceArn, tagsMap, timeout, kvsCredentialsProvider);
                    httpStatusCode = HTTP_OK;
                } catch (final Throwable tagStreamThrowable) {
                    MultiAuthServiceCallbacksImpl.this.log.error("[{}] An error occurred while tagging", streamInfo.getSummary(), tagStreamThrowable);
                    httpStatusCode = getStatusCodeFromException(tagStreamThrowable);
                } finally {
                    try {
                        MultiAuthServiceCallbacksImpl.this.kinesisVideoProducer.tagResourceResult(stream, streamHandle, httpStatusCode);
                    } catch (final ProducerException ex) {
                        notifyCallResult(stream, methodName, ex);
                    }
                }
            }
        }, calculateRelativeServiceCallAfter(callAfter), TimeUnit.NANOSECONDS);
    }

    private long calculateRelativeServiceCallAfter(final long absoluteCallAfter) {
        return Math.max(0, absoluteCallAfter * Time.NANOS_IN_A_TIME_UNIT
                - System.currentTimeMillis() * Time.NANOS_IN_A_MILLISECOND);
    }
}
