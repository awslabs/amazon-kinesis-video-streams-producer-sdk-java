package com.amazonaws.kinesisvideo.java.service;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentials;
import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentialsProvider;
import com.amazonaws.kinesisvideo.client.KinesisVideoClientConfiguration;
import com.amazonaws.kinesisvideo.client.PutMediaClient;
import com.amazonaws.kinesisvideo.client.signing.KinesisVideoAWS4Signer;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.common.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.producer.StreamDescription;
import com.amazonaws.kinesisvideo.producer.StreamStatus;
import com.amazonaws.kinesisvideo.internal.producer.client.KinesisVideoServiceClient;
import software.amazon.awssdk.services.kinesisvideo.KinesisVideoClient;
import software.amazon.awssdk.services.kinesisvideo.endpoints.KinesisVideoEndpointParams;
import software.amazon.awssdk.services.kinesisvideo.endpoints.KinesisVideoEndpointProvider;
import software.amazon.awssdk.services.kinesisvideo.model.CreateStreamRequest;
import software.amazon.awssdk.services.kinesisvideo.model.CreateStreamResponse;
import software.amazon.awssdk.services.kinesisvideo.model.DeleteStreamRequest;
import software.amazon.awssdk.services.kinesisvideo.model.DeleteStreamResponse;
import software.amazon.awssdk.services.kinesisvideo.model.DescribeStreamRequest;
import software.amazon.awssdk.services.kinesisvideo.model.DescribeStreamResponse;
import software.amazon.awssdk.services.kinesisvideo.model.GetDataEndpointRequest;
import software.amazon.awssdk.services.kinesisvideo.model.GetDataEndpointResponse;
import software.amazon.awssdk.services.kinesisvideo.model.TagStreamRequest;
import software.amazon.awssdk.services.kinesisvideo.model.TagStreamResponse;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

import static com.amazonaws.kinesisvideo.producer.Time.HUNDREDS_OF_NANOS_IN_AN_HOUR;
import static com.google.common.base.Strings.isNullOrEmpty;

public final class JavaKinesisVideoServiceClient implements KinesisVideoServiceClient {
    private static final int RECEIVE_TIMEOUT_1HR = 60 * 60 * 1000;
    private static final String ABSOLUTE_TIMECODE = "ABSOLUTE";
    private static final String RELATIVE_TIMECODE = "RELATIVE";
    private static final Logger log = LogManager.getLogger(JavaKinesisVideoServiceClient.class);
    private KinesisVideoClientConfiguration configuration;

    private static KinesisVideoClient createAmazonKinesisVideoClient(
            final KinesisVideoCredentialsProvider credentialsProvider,
            final Region region,
            final String endpoint,
            final int timeoutInMillis)
            throws KinesisVideoException {

        final AwsCredentials credentials = createAwsCredentials(credentialsProvider);
        return createAwsKinesisVideoClient(credentials, region, endpoint, timeoutInMillis);
    }

    private static KinesisVideoClient createAmazonKinesisVideoClient(
            final AwsCredentialsProvider awsCredentialsProvider,
            final Region region,
            final String endpoint,
            final int timeoutInMillis) {

        final AwsCredentials credentials = awsCredentialsProvider.resolveCredentials();
        return createAwsKinesisVideoClient(credentials, region, endpoint, timeoutInMillis);
    }

    private static KinesisVideoClient createAwsKinesisVideoClient(final AwsCredentials credentials,
            final Region region,
            final String endpoint,
            final int timeoutInMillis) {

        SdkHttpClient apacheHttpClient = createClientConfiguration(timeoutInMillis);

        final KinesisVideoEndpointParams kinesisVideoEndpointParams = KinesisVideoEndpointParams.builder()
                .endpoint(endpoint)
                .region(region)
                .useDualStack(false)
                .build();

        final KinesisVideoEndpointProvider kinesisVideoEndpointProvider = (KinesisVideoEndpointProvider) KinesisVideoEndpointProvider
                .defaultProvider()
                .resolveEndpoint(kinesisVideoEndpointParams);

        return KinesisVideoClient.builder()
                .httpClient(apacheHttpClient)
                .credentialsProvider(() -> {
                    // TODO Auto-generated method stub
                    return credentials;
                })
                .endpointProvider(kinesisVideoEndpointProvider)
                .build();
    }

    private static AwsCredentials createAwsCredentials(
            @Nullable final KinesisVideoCredentialsProvider credentialsProvider)
            throws KinesisVideoException {
        if (null == credentialsProvider) {
            return null;
        }

        final KinesisVideoCredentials kinesisVideoCredentials = credentialsProvider.getCredentials();

        if (kinesisVideoCredentials == null) {
            log.error("kinesisVideoCredentials is null");
            return null;
        }

        AwsCredentials credentials;

        if (kinesisVideoCredentials.getSessionToken() == null) {
            credentials = new AwsCredentials() {
                @Override
                public String accessKeyId() {
                    return kinesisVideoCredentials.getAccessKey();
                }

                @Override
                public String secretAccessKey() {
                    return kinesisVideoCredentials.getAccessKey();
                }
            };
        } else {
            credentials = AwsSessionCredentials.create(kinesisVideoCredentials.getAccessKey(),
                    kinesisVideoCredentials.getSecretKey(),
                    kinesisVideoCredentials.getSessionToken());
        }

        return credentials;
    }

    private static AwsCredentialsProvider createAwsCredentialsProvider(
            @Nullable final KinesisVideoCredentialsProvider credentialsProvider,
            @Nonnull final Logger log)
            throws KinesisVideoException {

        if (null == credentialsProvider) {
            return null;
        }

        return () -> {
            AwsCredentials awsCredentials = null;
            try {
                final KinesisVideoCredentials kinesisVideoCredentials = credentialsProvider.getCredentials();

                if (kinesisVideoCredentials == null) {
                    log.error("kinesisVideoCredentials must not be null while obtaining it from getCredentials");
                    throw new IllegalArgumentException();
                }

                if (kinesisVideoCredentials.getSessionToken() == null) {
                    awsCredentials = new AwsCredentials() {
                        @Override
                        public String accessKeyId() {
                            return kinesisVideoCredentials.getAccessKey();
                        }

                        @Override
                        public String secretAccessKey() {
                            return kinesisVideoCredentials.getSecretKey();
                        }
                    };
                } else {
                    awsCredentials = AwsSessionCredentials.create(kinesisVideoCredentials.getAccessKey(),
                            kinesisVideoCredentials.getSecretKey(),
                            kinesisVideoCredentials.getSessionToken());
                }
            } catch (final KinesisVideoException | IllegalArgumentException e) {
                log.error("Getting credentials threw an exception.", e);
            }

            return awsCredentials;
        };
    }

    private static SdkHttpClient createClientConfiguration(final int timeoutInMillis) {
        return ApacheHttpClient.builder()
                .proxyConfiguration(ProxyConfiguration.builder()
                        .useSystemPropertyValues(Boolean.FALSE)
                        .build())
                .connectionTimeout(Duration.of(timeoutInMillis, ChronoUnit.MILLIS))
                .socketTimeout(Duration.of(timeoutInMillis, ChronoUnit.MILLIS))
                .build();
    }

    @Nonnull
    public static KinesisVideoClient getAmazonKinesisVideoClient(
            @Nonnull final AwsCredentialsProvider credentialsProvider,
            @Nonnull final Region region,
            @Nonnull final String endpoint,
            final int timeoutInMillis) throws KinesisVideoException {
        return createAmazonKinesisVideoClient(credentialsProvider,
                region,
                endpoint,
                timeoutInMillis);
    }

    @Override
    public void initialize(@Nonnull final KinesisVideoClientConfiguration kinesisVideoClientConfiguration)
            throws KinesisVideoException {
        // We already got the configuration
        this.configuration = Preconditions.checkNotNull(kinesisVideoClientConfiguration);
    }

    @Override
    public String createStream(@Nonnull final String streamName,
            @Nonnull final String deviceName,
            @Nonnull final String contentType,
            @Nullable final String kmsKeyId,
            final long retentionPeriodInHours,
            final long timeoutInMillis,
            @Nullable final KinesisVideoCredentialsProvider credentialsProvider)
            throws KinesisVideoException {
        final KinesisVideoClient serviceClient = createAmazonKinesisVideoClient(credentialsProvider,
                Region.of(configuration.getRegion()),
                configuration.getEndpoint(),
                (int) timeoutInMillis);

        final CreateStreamRequest createStreamRequest = CreateStreamRequest.builder()
                .streamName(streamName)
                .deviceName(deviceName)
                .mediaType(contentType)
                .kmsKeyId(isNullOrEmpty(kmsKeyId) ? null : kmsKeyId)
                .dataRetentionInHours((int) retentionPeriodInHours)
                .tags(null).build();

        log.debug("calling create stream: {}", createStreamRequest.toString());

        final CreateStreamResponse createStreamResult;
        try {
            createStreamResult = serviceClient.createStream(createStreamRequest);
        } catch (final SdkException e) {
            // Wrap into an KinesisVideoException object
            log.error("Service call failed.", e);
            throw new KinesisVideoException(e);
        }

        log.debug("create stream result: {}", createStreamResult.toString());

        return createStreamResult.streamARN();
    }

    @Override
    public StreamDescription describeStream(@Nonnull final String streamName,
            final long timeoutInMillis,
            @Nullable final KinesisVideoCredentialsProvider credentialsProvider)
            throws KinesisVideoException {
        final KinesisVideoClient serviceClient = createAmazonKinesisVideoClient(credentialsProvider,
                Region.of(configuration.getRegion()),
                configuration.getEndpoint(),
                (int) timeoutInMillis);

        final DescribeStreamRequest describeStreamRequest = DescribeStreamRequest.builder()
                .streamName(streamName).build();

        log.debug("calling describe stream: {}", describeStreamRequest.toString());

        final DescribeStreamResponse describeStreamResult;
        try {
            describeStreamResult = serviceClient.describeStream(describeStreamRequest);
        } catch (final SdkException e) {
            log.error("Service call failed.", e);
            throw new KinesisVideoException(e);
        }

        if (null == describeStreamResult) {
            log.debug("describe stream returned null");
            return null;
        }

        log.debug("describe stream result: {}", describeStreamResult.toString());
        return toStreamDescription(describeStreamResult);
    }

    @Override
    public void deleteStream(@Nonnull final String streamName,
            @Nonnull final String version,
            final Date creationTime,
            final long timeoutInMillis,
            @Nullable final KinesisVideoCredentialsProvider credentialsProvider) throws KinesisVideoException {
        final KinesisVideoClient serviceClient = createAmazonKinesisVideoClient(credentialsProvider,
                Region.of(configuration.getRegion()),
                configuration.getEndpoint(),
                (int) timeoutInMillis);

        final StreamDescription streamDescription = describeStream(streamName, timeoutInMillis, credentialsProvider);

        final DeleteStreamResponse deleteStreamResult;

        try {
            if (streamDescription == null) {
                log.error("Stream description must not be null");
                throw new IllegalArgumentException();
            }

            final DeleteStreamRequest deleteStreamRequest = DeleteStreamRequest.builder()
                    .streamARN(streamDescription.getStreamArn())
                    .currentVersion(streamDescription.getUpdateVersion())
                    .build();

            log.debug("calling delete stream: {}", deleteStreamRequest.toString());
            deleteStreamResult = serviceClient.deleteStream(deleteStreamRequest);
            log.debug("delete stream result: {}", deleteStreamResult.toString());
        } catch (final SdkException e) {
            log.error("Service call failed.");
            throw new KinesisVideoException(e);
        } catch (final IllegalArgumentException e) {
            log.error("Stream description null.", e);
        }
    }

    @Override
    public void tagStream(@Nonnull final String streamArn,
            @Nullable final Map<String, String> tags,
            final long timeoutInMillis,
            @Nullable final KinesisVideoCredentialsProvider credentialsProvider)
            throws KinesisVideoException {
        final KinesisVideoClient serviceClient = createAmazonKinesisVideoClient(credentialsProvider,
                Region.of(configuration.getRegion()),
                configuration.getEndpoint(),
                (int) timeoutInMillis);

        final TagStreamRequest tagStreamRequest = TagStreamRequest.builder()
                .streamARN(streamArn)
                .tags(tags).build();

        log.debug("calling tag resource: {}", tagStreamRequest.toString());

        final TagStreamResponse tagStreamResult;
        try {
            tagStreamResult = serviceClient.tagStream(tagStreamRequest);
        } catch (final SdkException e) {
            log.error("Service call failed.", e);
            throw new KinesisVideoException(e);
        }

        log.debug("tag resource result: {}", tagStreamResult.toString());
    }

    @Override
    public String getDataEndpoint(@Nonnull final String streamName,
            @Nonnull final String apiName,
            final long timeoutInMillis,
            @Nullable final KinesisVideoCredentialsProvider credentialsProvider)
            throws KinesisVideoException {
        final KinesisVideoClient serviceClient = createAmazonKinesisVideoClient(credentialsProvider,
                Region.of(configuration.getRegion()),
                configuration.getEndpoint(),
                (int) timeoutInMillis);

        final GetDataEndpointRequest getDataEndpointRequest = GetDataEndpointRequest.builder()
                .streamName(streamName)
                .apiName(apiName).build();

        log.debug("calling get data endpoint: {}", getDataEndpointRequest.toString());

        final GetDataEndpointResponse getDataEndpointResult;

        try {
            getDataEndpointResult = serviceClient.getDataEndpoint(getDataEndpointRequest);
        } catch (final SdkException e) {
            log.error("Service call failed.", e);
            throw new KinesisVideoException(e);
        }

        log.debug("get data endpoint result: {}", getDataEndpointResult.toString());

        return getDataEndpointResult.dataEndpoint();
    }

    // CHECKSTYLE:SUPPRESS:ParameterNumber
    @Override
    public void putMedia(@Nonnull final String streamName,
            @Nonnull final String containerType,
            final long streamStartTimeInMillis,
            final boolean absoluteFragmentTimes,
            final boolean ackRequired,
            @Nonnull final String dataEndpoint,
            final long timeoutInMillis,
            @Nullable final KinesisVideoCredentialsProvider credentialsProvider,
            @Nonnull final InputStream dataInputStream,
            @Nonnull final Consumer<InputStream> acksConsumer,
            @Nullable final Consumer<Exception> completionCallback)
            throws KinesisVideoException {
        final AwsCredentialsProvider awsCredentialsProvider = createAwsCredentialsProvider(credentialsProvider, log);
        final com.amazonaws.kinesisvideo.config.ClientConfiguration clientConfiguration =
                com.amazonaws.kinesisvideo.config.ClientConfiguration
                .builder()
                .serviceName("kinesisvideo")
                .region(configuration.getRegion())
                .build();
        final KinesisVideoAWS4Signer signer = new KinesisVideoAWS4Signer(awsCredentialsProvider, clientConfiguration);
        final URI putMediaUri = URI.create(dataEndpoint + "/putMedia");
        final String timecodeType = absoluteFragmentTimes ? ABSOLUTE_TIMECODE : RELATIVE_TIMECODE;

        final PutMediaClient.Builder putMediaClientBuilder = PutMediaClient
                .builder()
                .receiveTimeout(RECEIVE_TIMEOUT_1HR)
                .timestamp(streamStartTimeInMillis)
                .signWith(signer)
                .receiveAcks(acksConsumer)
                .receiveCompletion(completionCallback)
                .streamName(streamName)
                .mkvStream(dataInputStream)
                .fragmentTimecodeType(timecodeType)
                .putMediaDestinationUri(putMediaUri);

        final PutMediaClient putMediaClient = putMediaClientBuilder.build();

        // Kick off execution
        putMediaClient.putMediaInBackground();
    }

    private StreamDescription toStreamDescription(@Nonnull final DescribeStreamResponse result) {
        Preconditions.checkNotNull(result);
        return new StreamDescription(
                StreamDescription.STREAM_DESCRIPTION_CURRENT_VERSION,
                result.streamInfo().deviceName(),
                result.streamInfo().streamName(),
                result.streamInfo().mediaType(),
                result.streamInfo().version(),
                result.streamInfo().streamARN(),
                StreamStatus.valueOf(String.valueOf(result.streamInfo().status())),
                result.streamInfo().creationTime().toEpochMilli(),
                result.streamInfo().dataRetentionInHours() * HUNDREDS_OF_NANOS_IN_AN_HOUR,
                result.streamInfo().kmsKeyId());
    }
}
