package com.amazonaws.kinesisvideo.java.service;

import com.amazonaws.AmazonClientException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentials;
import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentialsProvider;
import com.amazonaws.kinesisvideo.client.KinesisVideoClientConfiguration;
import com.amazonaws.kinesisvideo.client.PutMediaClient;
import com.amazonaws.kinesisvideo.client.signing.KinesisVideoAWS4Signer;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.common.function.Consumer;
import com.amazonaws.kinesisvideo.common.logging.Log;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.producer.StreamDescription;
import com.amazonaws.kinesisvideo.producer.StreamStatus;
import com.amazonaws.kinesisvideo.internal.producer.client.KinesisVideoServiceClient;
import com.amazonaws.kinesisvideo.util.VersionUtil;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClient;
import com.amazonaws.services.kinesisvideo.model.CreateStreamRequest;
import com.amazonaws.services.kinesisvideo.model.CreateStreamResult;
import com.amazonaws.services.kinesisvideo.model.DeleteStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DeleteStreamResult;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamResult;
import com.amazonaws.services.kinesisvideo.model.GetDataEndpointRequest;
import com.amazonaws.services.kinesisvideo.model.GetDataEndpointResult;
import com.amazonaws.services.kinesisvideo.model.TagStreamRequest;
import com.amazonaws.services.kinesisvideo.model.TagStreamResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStream;
import java.net.URI;
import java.util.Date;
import java.util.Map;

import static com.amazonaws.ClientConfiguration.DEFAULT_MAX_CONNECTIONS;
import static com.amazonaws.kinesisvideo.producer.Time.HUNDREDS_OF_NANOS_IN_AN_HOUR;
import static com.amazonaws.util.StringUtils.isNullOrEmpty;

public final class JavaKinesisVideoServiceClient implements KinesisVideoServiceClient {
    private static final int RECEIVE_TIMEOUT_1HR = 60 * 60 * 1000;
    private static final String ABSOLUTE_TIMECODE = "ABSOLUTE";
    private static final String RELATIVE_TIMECODE = "RELATIVE";

    private final Log log;
    private KinesisVideoClientConfiguration configuration;

    private static AmazonKinesisVideo createAmazonKinesisVideoClient(
            final KinesisVideoCredentialsProvider credentialsProvider,
            final Region region,
            final String endpoint,
            final int timeoutInMillis)
            throws KinesisVideoException {

        final AWSCredentials credentials = createAwsCredentials(credentialsProvider);
        return createAwsKinesisVideoClient(credentials, region, endpoint, timeoutInMillis);
    }

    private static AmazonKinesisVideo createAmazonKinesisVideoClient(
            final AWSCredentialsProvider awsCredentialsProvider,
            final Region region,
            final String endpoint,
            final int timeoutInMillis)
            throws KinesisVideoException {

        final AWSCredentials credentials = awsCredentialsProvider.getCredentials();
        return createAwsKinesisVideoClient(credentials, region, endpoint, timeoutInMillis);
    }

    private static AmazonKinesisVideo createAwsKinesisVideoClient(final AWSCredentials credentials,
            final Region region,
            final String endpoint,
            final int timeoutInMillis)
            throws KinesisVideoException {

        final ClientConfiguration clientConfiguration = createClientConfiguration(timeoutInMillis);
        final AmazonKinesisVideo amazonKinesisVideoClient = AmazonKinesisVideoClient.builder()
                .withClientConfiguration(clientConfiguration)
                .withCredentials(new AWSCredentialsProvider() {

                    @Override
                    public void refresh() {
                        // Do nothing
                    }

                    @Override
                    public AWSCredentials getCredentials() {
                        // TODO Auto-generated method stub
                        return credentials;
                    }
                })
                // .withRegion(region.getName())
                .withEndpointConfiguration(new EndpointConfiguration(endpoint, region.getName()))
                .build();

        return amazonKinesisVideoClient;
    }

    private static AWSCredentials createAwsCredentials(
            @Nullable final KinesisVideoCredentialsProvider credentialsProvider)
            throws KinesisVideoException {
        if (null == credentialsProvider) {
            return null;
        }

        final KinesisVideoCredentials kinesisVideoCredentials = credentialsProvider.getCredentials();

        AWSCredentials credentials = null;

        if (kinesisVideoCredentials.getSessionToken() == null) {
            credentials = new AWSCredentials() {
                @Override
                public String getAWSAccessKeyId() {
                    return kinesisVideoCredentials.getAccessKey();
                }

                @Override
                public String getAWSSecretKey() {
                    return kinesisVideoCredentials.getSecretKey();
                }
            };
        } else {
            credentials = new AWSSessionCredentials() {
                @Override
                public String getSessionToken() {
                    return kinesisVideoCredentials.getSessionToken();
                }

                @Override
                public String getAWSAccessKeyId() {
                    return kinesisVideoCredentials.getAccessKey();
                }

                @Override
                public String getAWSSecretKey() {
                    return kinesisVideoCredentials.getSecretKey();
                }
            };
        }

        return credentials;
    }

    private static AWSCredentialsProvider createAwsCredentialsProvider(
            @Nullable final KinesisVideoCredentialsProvider credentialsProvider,
            @Nonnull final Log log)
            throws KinesisVideoException {

        if (null == credentialsProvider) {
            return null;
        }

        return new AWSCredentialsProvider() {
            @Override
            public AWSCredentials getCredentials() {
                AWSCredentials awsCredentials = null;
                try {
                    final KinesisVideoCredentials kinesisVideoCredentials = credentialsProvider.getCredentials();

                    if (kinesisVideoCredentials.getSessionToken() == null) {
                        awsCredentials = new AWSCredentials() {
                            @Override
                            public String getAWSAccessKeyId() {
                                return kinesisVideoCredentials.getAccessKey();
                            }

                            @Override
                            public String getAWSSecretKey() {
                                return kinesisVideoCredentials.getSecretKey();
                            }
                        };
                    } else {
                        awsCredentials = new AWSSessionCredentials() {
                            @Override
                            public String getSessionToken() {
                                return kinesisVideoCredentials.getSessionToken();
                            }

                            @Override
                            public String getAWSAccessKeyId() {
                                return kinesisVideoCredentials.getAccessKey();
                            }

                            @Override
                            public String getAWSSecretKey() {
                                return kinesisVideoCredentials.getSecretKey();
                            }
                        };
                    }
                } catch (final KinesisVideoException e) {
                    log.exception(e, "Getting credentials threw an exception.");
                    awsCredentials = null;
                }

                return awsCredentials;
            }

            @Override
            public void refresh() {
                try {
                    credentialsProvider.getUpdatedCredentials();
                } catch (final KinesisVideoException e) {
                    // Do nothing
                    log.exception(e, "Refreshing credentials threw and exception.");
                }
            }
        };
    }

    private static ClientConfiguration createClientConfiguration(final int timeoutInMillis) {
        return new ClientConfiguration()
                .withProtocol(Protocol.HTTPS)
                .withConnectionTimeout(timeoutInMillis)
                .withMaxConnections(DEFAULT_MAX_CONNECTIONS)
                .withSocketTimeout(timeoutInMillis)
                .withUserAgentPrefix(VersionUtil.getUserAgent());
    }

    public JavaKinesisVideoServiceClient(@Nonnull final Log log) {
        this.log = Preconditions.checkNotNull(log);
    }

    @Nonnull
    public static AmazonKinesisVideo getAmazonKinesisVideoClient(
            @Nonnull final AWSCredentialsProvider credentialsProvider,
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
        final AmazonKinesisVideo serviceClient = createAmazonKinesisVideoClient(credentialsProvider,
                Region.getRegion(Regions.fromName(configuration.getRegion())),
                configuration.getEndpoint(),
                (int) timeoutInMillis);

        final CreateStreamRequest createStreamRequest = new CreateStreamRequest()
                .withStreamName(streamName)
                .withDeviceName(deviceName)
                .withMediaType(contentType)
                .withKmsKeyId(isNullOrEmpty(kmsKeyId) ? null : kmsKeyId)
                .withDataRetentionInHours((int) retentionPeriodInHours)
                .withTags(null);

        log.debug("calling create stream: " + createStreamRequest.toString());

        final CreateStreamResult createStreamResult;
        try {
            createStreamResult = serviceClient.createStream(createStreamRequest);
        } catch (final AmazonClientException e) {
            // Wrap into an KinesisVideoException object
            log.exception(e, "Service call failed.");
            throw new KinesisVideoException(e);
        }

        log.debug("create stream result: " + createStreamResult.toString());

        return createStreamResult.getStreamARN();
    }

    @Override
    public StreamDescription describeStream(@Nonnull final String streamName,
            final long timeoutInMillis,
            @Nullable final KinesisVideoCredentialsProvider credentialsProvider)
            throws KinesisVideoException {
        final AmazonKinesisVideo serviceClient = createAmazonKinesisVideoClient(credentialsProvider,
                Region.getRegion(Regions.fromName(configuration.getRegion())),
                configuration.getEndpoint(),
                (int) timeoutInMillis);

        final DescribeStreamRequest describeStreamRequest = new DescribeStreamRequest()
                .withStreamName(streamName);

        log.debug("calling describe stream: " + describeStreamRequest.toString());

        final DescribeStreamResult describeStreamResult;
        try {
            describeStreamResult = serviceClient.describeStream(describeStreamRequest);
        } catch (final AmazonClientException e) {
            log.exception(e, "Service call failed.");
            throw new KinesisVideoException(e);
        }

        if (null == describeStreamResult) {
            log.debug("describe stream returned null");
            return null;
        }

        log.debug("describe stream result: " + describeStreamResult.toString());
        return toStreamDescription(describeStreamResult);
    }

    @Override
    public void deleteStream(@Nonnull final String streamName,
            @Nonnull final String version,
            final Date creationTime,
            final long timeoutInMillis,
            @Nullable final KinesisVideoCredentialsProvider credentialsProvider) throws KinesisVideoException {
        final AmazonKinesisVideo serviceClient = createAmazonKinesisVideoClient(credentialsProvider,
                Region.getRegion(Regions.fromName(configuration.getRegion())),
                configuration.getEndpoint(),
                (int) timeoutInMillis);

        final StreamDescription streamDescription = describeStream(streamName, timeoutInMillis, credentialsProvider);

        final DeleteStreamRequest deleteStreamRequest = new DeleteStreamRequest()
                .withStreamARN(streamDescription.getStreamArn())
                .withCurrentVersion(streamDescription.getUpdateVersion());

        log.debug("calling delete stream: " + deleteStreamRequest.toString());

        final DeleteStreamResult deleteStreamResult;
        try {
            deleteStreamResult = serviceClient.deleteStream(deleteStreamRequest);
        } catch (final AmazonClientException e) {
            log.exception(e, "Service call failed.");
            throw new KinesisVideoException(e);
        }

        log.debug("delete stream result: " + deleteStreamResult.toString());
    }

    @Override
    public void tagStream(@Nonnull final String streamArn,
            @Nullable final Map<String, String> tags,
            final long timeoutInMillis,
            @Nullable final KinesisVideoCredentialsProvider credentialsProvider)
            throws KinesisVideoException {
        final AmazonKinesisVideo serviceClient = createAmazonKinesisVideoClient(credentialsProvider,
                Region.getRegion(Regions.fromName(configuration.getRegion())),
                configuration.getEndpoint(),
                (int) timeoutInMillis);

        final TagStreamRequest tagStreamRequest = new TagStreamRequest()
                .withStreamARN(streamArn)
                .withTags(tags);

        log.debug("calling tag resource: " + tagStreamRequest.toString());

        final TagStreamResult tagStreamResult;
        try {
            tagStreamResult = serviceClient.tagStream(tagStreamRequest);
        } catch (final AmazonClientException e) {
            log.exception(e, "Service call failed.");
            throw new KinesisVideoException(e);
        }

        log.debug("tag resource result: " + tagStreamResult.toString());
    }

    @Override
    public String getDataEndpoint(@Nonnull final String streamName,
            @Nonnull final String apiName,
            final long timeoutInMillis,
            @Nullable final KinesisVideoCredentialsProvider credentialsProvider)
            throws KinesisVideoException {
        final AmazonKinesisVideo serviceClient = createAmazonKinesisVideoClient(credentialsProvider,
                Region.getRegion(Regions.fromName(configuration.getRegion())),
                configuration.getEndpoint(),
                (int) timeoutInMillis);

        final GetDataEndpointRequest getDataEndpointRequest = new GetDataEndpointRequest()
                .withStreamName(streamName)
                .withAPIName(apiName);

        log.debug("calling get data endpoint: " + getDataEndpointRequest.toString());

        final GetDataEndpointResult getDataEndpointResult;

        try {
            getDataEndpointResult = serviceClient.getDataEndpoint(getDataEndpointRequest);
        } catch (final AmazonClientException e) {
            log.exception(e, "Service call failed.");
            throw new KinesisVideoException(e);
        }

        log.debug("get data endpoint result: " + getDataEndpointResult.toString());

        return getDataEndpointResult.getDataEndpoint();
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
        final AWSCredentialsProvider awsCredentialsProvider = createAwsCredentialsProvider(credentialsProvider, log);
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
                .log(log)
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

    private static StreamDescription toStreamDescription(@Nonnull final DescribeStreamResult result) {
        Preconditions.checkNotNull(result);
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
