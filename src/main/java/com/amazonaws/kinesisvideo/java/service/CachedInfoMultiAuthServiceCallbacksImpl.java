package com.amazonaws.kinesisvideo.java.service;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentials;
import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentialsProvider;
import com.amazonaws.kinesisvideo.client.KinesisVideoClientConfiguration;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.common.logging.Log;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducer;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.internal.producer.client.KinesisVideoServiceClient;
import com.amazonaws.kinesisvideo.internal.service.DefaultServiceCallbacksImpl;
import com.amazonaws.kinesisvideo.java.auth.JavaCredentialsProviderImpl;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.StreamDescription;
import com.amazonaws.kinesisvideo.producer.StreamStatus;
import com.amazonaws.kinesisvideo.producer.Tag;
import com.amazonaws.kinesisvideo.producer.Time;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamResult;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.amazonaws.kinesisvideo.common.preconditions.Preconditions.checkNotNull;
import static com.amazonaws.kinesisvideo.producer.Time.HUNDREDS_OF_NANOS_IN_AN_HOUR;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.HTTP_BAD_REQUEST;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.HTTP_OK;

public class CachedInfoMultiAuthServiceCallbacksImpl extends DefaultServiceCallbacksImpl {
    public CachedInfoMultiAuthServiceCallbacksImpl(@Nonnull Log log, @Nonnull ScheduledExecutorService executor,
                                                   @Nonnull KinesisVideoClientConfiguration configuration,
                                                   @Nonnull KinesisVideoServiceClient kinesisVideoServiceClient) {
        super(log, executor, configuration, kinesisVideoServiceClient);
    }

    /**
     * StreamArn -> Credentials Provider
     */
    private Map<String, KinesisVideoCredentialsProvider> credentialsProviderMap = new HashMap<>();

    /**
     * StreamArn -> StreamInfo
     */
    private Map<String, DescribeStreamResult> streamInfoMap = new HashMap<>();

    /**
     * StreamArn -> Data Endpoint
     */
    private Map<String, String> endpointMap = new HashMap<>();

    /**
     * StreamArn -> Tags for the stream
     */
    private Map<String, Tag[]> tagInfoMap = new HashMap<>();


    /**
     * Initializes the object
     *
     * @param kinesisVideoProducer Reference to {@link KinesisVideoProducer} for the eventing.
     */
    @Override
    public void initialize(@Nonnull final KinesisVideoProducer kinesisVideoProducer) {
        Preconditions.checkState(!isInitialized(), "Service callback object has already been initialized");
        this.kinesisVideoProducer = Preconditions.checkNotNull(kinesisVideoProducer);
    }

    @Override
    public boolean isInitialized() {
        return kinesisVideoProducer != null;
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
                             final long customData)
            throws ProducerException {
        throw new ProducerException(
                "Stream need to be pre-existing if using CachedInfoMultiAuthServiceCallbacksImpl.", 0);
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
        final DescribeStreamResult streamInfo = streamInfoMap.get(streamName);
        if (streamInfo == null) {
            throw new ProducerException("Stream Description is not given for stream " + streamName, 0);
        }
        final StreamDescription streamDescription = toStreamDescription(streamInfo);
        try {
            kinesisVideoProducer.describeStreamResult(stream, streamHandle, streamDescription, HTTP_OK);
        } catch (final ProducerException e) {
            throw new RuntimeException(e);
        }
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

        String endpoint = endpointMap.get(streamName);

        if (endpoint == null) {
            throw new ProducerException("Streaming Endpoint is not given for stream " + streamName, 0);
        }

        kinesisVideoProducer.getStreamingEndpointResult(stream, streamHandle, endpoint, HTTP_OK);
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

        final KinesisVideoCredentialsProvider kvsCredentialsProvider = credentialsProviderMap.get(streamName);

        // Stores the serialized credentials as a streaming token
        byte[] serializedCredentials = null;
        long expiration = 0;

        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            final KinesisVideoCredentials credentials = kvsCredentialsProvider.getUpdatedCredentials();

            // Serialize the credentials
            expiration = credentials.getExpiration().getTime() * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;

            // Serialize the credentials as streaming token
            final ObjectOutput outputStream = new ObjectOutputStream(byteArrayOutputStream);
            outputStream.writeObject(credentials);
            outputStream.flush();
            serializedCredentials = byteArrayOutputStream.toByteArray();
            outputStream.close();
        } catch (final IOException e) {
            log.exception(e);
        } catch (final KinesisVideoException e) {
            log.exception(e);
        } finally {
            try {
                byteArrayOutputStream.close();
            } catch (final IOException ex) {
                // Do nothing
            }
        }

        final int statusCode = HTTP_OK;

        try {
            kinesisVideoProducer.getStreamingTokenResult(
                    stream,
                    streamHandle,
                    serializedCredentials,
                    expiration,
                    statusCode);
        } catch (final ProducerException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void tagResource(@Nonnull final String resourceArn,
                            @Nullable final Tag[] tagsUnused,
                            final long callAfter,
                            final long timeout,
                            @Nullable final byte[] authData,
                            final int authType,
                            final long streamHandle,
                            final KinesisVideoProducerStream stream) throws ProducerException {

        Preconditions.checkState(isInitialized(), "Service callbacks object should be initialized first");
        final long delay = calculateRelativeServiceCallAfter(callAfter);
        // arn:aws:kinesisvideo:us-west-2:xxxxxxxxxxx:stream/streamName/xxxxxxxxxxxxx
        // stream object is not ready if tagStream is in createStreamSync() process, so stream.getStreamName() cannot
        // be used
        final String[] arns = resourceArn.split("/");
        Tag[] tagsOfStream = null;
        if (arns.length > 2) {
            tagsOfStream = tagInfoMap.get(arns[1]);
        }
        if (tagsOfStream == null || tagsOfStream.length == 0) {
            try {
                kinesisVideoProducer.tagResourceResult(stream, streamHandle, HTTP_OK);
            } catch (final ProducerException e) {
                throw new RuntimeException(e);
            }
            return;
        }
        final Tag[] tags = tagsOfStream;

        final Runnable task = new Runnable() {
            @Override
            public void run() {
                final KinesisVideoCredentialsProvider credentialsProvider = getCredentialsProvider(authData, log);
                final long timeoutInMillis = timeout / Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
                int statusCode = HTTP_OK;

                Map<String, String> tagsMap = null;
                if (null != tags) {
                    // Convert the tags to map
                    tagsMap = new HashMap<String, String>(tags.length);

                    for (final Tag tag : tags) {
                        tagsMap.put(tag.getName(), tag.getValue());
                    }
                }
                try {
                    kinesisVideoServiceClient.tagStream(resourceArn,
                            tagsMap,
                            timeoutInMillis,
                            credentialsProvider);
                } catch (final KinesisVideoException e) {
                    log.error("Kinesis Video service client returned an error " + e.getMessage()
                            + ". Reporting to Kinesis Video PIC.");
                    statusCode = getStatusCodeFromException(e);
                }

                if (statusCode != HTTP_OK) {
                    // TODO: more URI validation
                    statusCode = HTTP_BAD_REQUEST;
                }

                try {
                    kinesisVideoProducer.tagResourceResult(stream, streamHandle, statusCode);
                } catch (final ProducerException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        executor.schedule(task, delay, TimeUnit.NANOSECONDS);
    }

    public void addStreamInfoToCache(String streamName, DescribeStreamResult streamInfo) {
        Preconditions.checkArgument(!streamInfoMap.containsKey(streamName));
        streamInfoMap.put(streamName, streamInfo);
    }

    public void addTagInfoToCache(String streamName, Tag[] tags) {
        Preconditions.checkArgument(!tagInfoMap.containsKey(streamName));
        tagInfoMap.put(streamName, tags);
    }

    public void addStreamingEndpointToCache(String streamName, String endpoint) {
        Preconditions.checkArgument(!endpointMap.containsKey(streamName));
        endpointMap.put(streamName, endpoint);
    }

    public void addCredentialsProviderToCache(String streamName, AWSCredentialsProvider credentialsProvider) {
        Preconditions.checkArgument(!credentialsProviderMap.containsKey(streamName));
        final KinesisVideoCredentialsProvider kvsCredentialsProvider =
                new JavaCredentialsProviderImpl(credentialsProvider);
        credentialsProviderMap.put(streamName, kvsCredentialsProvider);
    }

    public void removeStreamFromCache(String streamName) {
        credentialsProviderMap.remove(streamName);
        streamInfoMap.remove(streamName);
        endpointMap.remove(streamName);
        tagInfoMap.remove(streamName);
    }

    private long calculateRelativeServiceCallAfter(final long absoluteCallAfter) {
        return Math.max(0, absoluteCallAfter * Time.NANOS_IN_A_TIME_UNIT
                - System.currentTimeMillis() * Time.NANOS_IN_A_MILLISECOND);
    }

    private static StreamDescription toStreamDescription(final @NonNull DescribeStreamResult result) {
        checkNotNull(result);
        return new StreamDescription(
                StreamDescription.STREAM_DESCRIPTION_CURRENT_VERSION,
                result.getStreamInfo().getDeviceName(),
                result.getStreamInfo().getStreamName(),
                result.getStreamInfo().getMediaType(),
                result.getStreamInfo().getVersion(),
                result.getStreamInfo().getStreamARN(),
                StreamStatus.valueOf(result.getStreamInfo().getStatus()),
                result.getStreamInfo().getCreationTime().getTime(),
                result.getStreamInfo().getDataRetentionInHours() * HUNDREDS_OF_NANOS_IN_AN_HOUR,
                result.getStreamInfo().getKmsKeyId());
    }
}
