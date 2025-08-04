package com.amazonaws.kinesisvideo.common;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.kinesisvideo.client.IPVersionFilter;
import com.amazonaws.kinesisvideo.client.KinesisVideoClient;
import com.amazonaws.kinesisvideo.client.KinesisVideoClientConfiguration;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSource;
import com.amazonaws.kinesisvideo.internal.producer.ServiceCallbacks;
import com.amazonaws.kinesisvideo.java.auth.JavaCredentialsFactory;
import com.amazonaws.kinesisvideo.java.client.KinesisVideoJavaClientFactory;
import com.amazonaws.kinesisvideo.java.mediasource.file.ImageFileMediaSource;
import com.amazonaws.kinesisvideo.java.mediasource.file.ImageFileMediaSourceConfiguration;
import com.amazonaws.kinesisvideo.java.service.JavaKinesisVideoServiceClient;
import com.amazonaws.kinesisvideo.java.service.MultiAuthServiceCallbacksImpl;
import com.amazonaws.kinesisvideo.producer.FragmentAckType;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFragmentAck;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.kinesisvideo.storage.DefaultStorageCallbacks;
import com.amazonaws.kinesisvideo.streaming.DefaultStreamCallbacks;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClientBuilder;
import com.amazonaws.services.kinesisvideo.model.CreateStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DeleteStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamResult;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public class MultiAuthServiceCallbacksImplTest {

    private static final Logger log = LogManager.getLogger(MultiAuthServiceCallbacksImplTest.class);

    private static final String FAILING_STREAMS_POSTFIX = "-fail";
    private static final String IMAGE_DIR = "src/main/resources/data/h264/";
    private static final Duration DURATION_TO_STREAM = Duration.ofSeconds(30);
    private static final String IMAGE_FILENAME_FORMAT = "frame-%03d.h264";
    private static final int START_FILE_INDEX = 1;
    private static final int END_FILE_INDEX = 375;

    // How close to DURATION_TO_STREAM, the greatest ACK timecode should be for the test to pass
    private static final Duration ACKS_DURATION_THRESHOLD = Duration.ofSeconds(3);

    // Stream names of the healthy and invalidCredentials streams
    private final List<String> streamNamePassingStreams = new ArrayList<>();
    private final List<String> streamNameFailingStreams = new ArrayList<>();

    /**
     * Test parameters for parameterized tests.
     * Each array contains: [numberOfHealthyStreams, numberOfKmsErroredStreams]
     *
     * @return Collection of test parameter arrays
     */
    @Parameterized.Parameters(name = "HealthyStreams={0}, InvalidCredentialsStreams={1}")
    public static Collection<Object[]> testParameters() {
        return Arrays.asList(new Object[][]{
                {1, 1},  // 1 healthy stream, 1 stream with invalid credentials
                {5, 5},  // 5 healthy streams, 5 streams with invalid credentials
        });
    }

    private final int healthyStreamCount;
    private final int invalidCredentialsStreamCount;

    public MultiAuthServiceCallbacksImplTest(final int healthyStreams, final int invalidCredentialsStreams) {
        assumeTrue(healthyStreams > 0 && invalidCredentialsStreams > 0);

        // TODO: KinesisVideoJavaClientFactory sets numberOfStreams in DeviceInfo to 10, need to add a parameter for it to be configurable
        assumeTrue(healthyStreams + invalidCredentialsStreams <= 10);

        this.healthyStreamCount = healthyStreams;
        this.invalidCredentialsStreamCount = invalidCredentialsStreams;
    }

    @Before
    public void setup() {
        assumeTrue(DefaultAWSCredentialsProviderChain.getInstance().getCredentials() != null);

        final String prefix = Optional.ofNullable(System.getenv("TEST_STREAMS_PREFIX")).orElse("");
        final AmazonKinesisVideo awsSdkKinesisVideoClient = AmazonKinesisVideoClientBuilder.standard().build();
        boolean success = true;

        for (int i = 0; i < this.healthyStreamCount; i++) {
            final String streamNamePassingStream = prefix + UUID.randomUUID();
            try {
                final CreateStreamRequest createStreamRequest = new CreateStreamRequest()
                        .withStreamName(streamNamePassingStream)
                        .withDataRetentionInHours(2);
                awsSdkKinesisVideoClient.createStream(createStreamRequest);
                this.streamNamePassingStreams.add(streamNamePassingStream);
            } catch (final Throwable t) {
                log.error("Encountered an error creating healthy streams: {}!", streamNamePassingStream, t);
                success = false;
            }
        }

        for (int i = 0; i < this.invalidCredentialsStreamCount; i++) {
            final String streamNameFailingStream = prefix + UUID.randomUUID() + UUID.randomUUID() + FAILING_STREAMS_POSTFIX;
            try {
                final CreateStreamRequest createStreamRequest = new CreateStreamRequest()
                        .withStreamName(streamNameFailingStream)
                        .withDataRetentionInHours(2);
                awsSdkKinesisVideoClient.createStream(createStreamRequest);
                this.streamNameFailingStreams.add(streamNameFailingStream);
            } catch (final Throwable t) {
                log.error("Encountered an error creating {} stream for bad credentials test!", streamNameFailingStream, t);
                success = false;
            }
        }

        assertTrue("Encountered an error in the setup, check the logs above", success);
    }

    @After
    public void tearDown() {
        final AmazonKinesisVideo awsSdkKinesisVideoClient = AmazonKinesisVideoClientBuilder.standard().build();
        boolean success = true;

        for (final String streamNamePassingStream : this.streamNamePassingStreams) {
            try {
                final DescribeStreamRequest describeStreamRequest = new DescribeStreamRequest().withStreamName(streamNamePassingStream);
                final DescribeStreamResult describeStreamResult = awsSdkKinesisVideoClient.describeStream(describeStreamRequest);

                final DeleteStreamRequest deleteStreamRequest = new DeleteStreamRequest()
                        .withStreamARN(describeStreamResult.getStreamInfo().getStreamARN())
                        .withCurrentVersion(describeStreamResult.getStreamInfo().getVersion());
                awsSdkKinesisVideoClient.deleteStream(deleteStreamRequest);
            } catch (final Throwable t) {
                log.error("Encountered an error deleting healthy stream: {}!", streamNamePassingStream, t);
                success = false;
            }
        }

        for (final String streamNameFailingStream : this.streamNameFailingStreams) {
            try {
                final DescribeStreamRequest describeStreamRequest = new DescribeStreamRequest().withStreamName(streamNameFailingStream);
                final DescribeStreamResult describeStreamResult = awsSdkKinesisVideoClient.describeStream(describeStreamRequest);

                final DeleteStreamRequest deleteStreamRequest = new DeleteStreamRequest()
                        .withStreamARN(describeStreamResult.getStreamInfo().getStreamARN())
                        .withCurrentVersion(describeStreamResult.getStreamInfo().getVersion());
                awsSdkKinesisVideoClient.deleteStream(deleteStreamRequest);
            } catch (final Throwable t) {
                log.error("Encountered an error deleting {} from the bad credentials test!", streamNameFailingStream, t);
                success = false;
            }
        }

        assertTrue(success);
    }

    @Rule
    public Timeout globalTimeout = Timeout.seconds(60);

    private class StreamContext {
        public MediaSource mediaSource;
        public final List<KinesisVideoFragmentAck> acksReceived = new ArrayList<>();
        public final List<Long> errorsReceived = new ArrayList<>();
    }

    @Test
    public void when_goodCredentialsPassedIn_ThenStreamingSuccessfully_And_WhenBadCrednentialsPassedIn_ThenNoStreaming_AndDoesNotImpactHealthyStreams() {

        final AWSCredentialsProvider dummyCredentialsProvider = new AWSCredentialsProvider() {
            @Override
            public AWSCredentials getCredentials() {
                return new BasicAWSCredentials("dummy", "dummy");
            }

            @Override
            public void refresh() {
                // No-op
            }
        };

        try {
            final ExecutorService registerExecutorBadCredentialsStreams = Executors.newFixedThreadPool(this.invalidCredentialsStreamCount);
            final ScheduledExecutorService executor = Executors.newScheduledThreadPool(this.healthyStreamCount + this.invalidCredentialsStreamCount,
                    new ThreadFactoryBuilder().setNameFormat("KVS-Test-JavaClientExecutor-%d").build());
            final KinesisVideoClientConfiguration configuration = KinesisVideoClientConfiguration.builder()
                    .withRegion(Regions.US_WEST_2.getName())
                    .withCredentialsProvider(JavaCredentialsFactory.createKinesisVideoCredentialsProvider(dummyCredentialsProvider))
                    .withStorageCallbacks(new DefaultStorageCallbacks())
                    .withIPVersionFilter(IPVersionFilter.IPV4_AND_IPV6)
                    .build();

            final Logger log = LogManager.getLogger(MultiAuthServiceCallbacksImplTest.class);

            // Use fake creds for a stream called 'fail'
            // Use real creds for a stream called 'pass'
            // (for easy verification)
            final Function<StreamInfo, AWSCredentialsProvider> testFunction = streamInfo -> {
                if (streamInfo == null) {
                    fail("StreamInfo is null, it shouldn't be!");
                }
                if (streamInfo.getName() == null) {
                    fail("StreamInfo.getName() is null, it shouldn't be!");
                }
                if (streamInfo.getName().endsWith(FAILING_STREAMS_POSTFIX)) {
                    return dummyCredentialsProvider;
                } else {
                    return DefaultAWSCredentialsProviderChain.getInstance();
                }
            };

            // Create CachedInfoServiceCallback
            final ServiceCallbacks serviceCallbacks =
                    new MultiAuthServiceCallbacksImpl(executor, configuration, new JavaKinesisVideoServiceClient(log), testFunction);
            // create Kinesis Video high level client
            final KinesisVideoClient kinesisVideoClient = KinesisVideoJavaClientFactory
                    .createKinesisVideoClient(log, configuration, executor, null, serviceCallbacks);

            final Map<String, StreamContext> testStreams = new HashMap<>();
            for (final String streamNamePassingStream : this.streamNamePassingStreams) {
                final StreamContext streamContext = new StreamContext();
                final MediaSource mediaSource = createImageFileMediaSource(streamNamePassingStream, streamContext.acksReceived, streamContext.errorsReceived);
                streamContext.mediaSource = mediaSource;
                testStreams.put(streamNamePassingStream, streamContext);

                kinesisVideoClient.registerMediaSource(mediaSource);
                mediaSource.start();
            }

            for (final String streamNameFailingStream : this.streamNameFailingStreams) {
                final StreamContext streamContext = new StreamContext();
                final MediaSource mediaSource = createImageFileMediaSource(streamNameFailingStream, streamContext.acksReceived, streamContext.errorsReceived);
                testStreams.put(streamNameFailingStream, streamContext);
                streamContext.mediaSource = mediaSource;

                // registerMediaSource is synchronous - it will block while the native stream is in the creation state
                registerExecutorBadCredentialsStreams.submit(() -> {
                    try {
                        kinesisVideoClient.registerMediaSource(mediaSource);
                        fail("Creating the failed stream should have timed out!");
                    } catch (final KinesisVideoException e) {
                        // Expected timeout failure
                    } catch (final Throwable e) {
                        log.error("Unexpected exception creating the failure stream", e);
                        fail();
                    }
                });
            }

            log.info("Main thread sleeping {} ms.", DURATION_TO_STREAM.toMillis());
            Thread.sleep(DURATION_TO_STREAM.toMillis());
            log.info("Stopping stream...");

            // unregister healthy streams from client and free client
            for (final Map.Entry<String, StreamContext> streamContext : testStreams.entrySet()) {
                final String streamName = streamContext.getKey();
                final StreamContext context = streamContext.getValue();

                if (!streamName.endsWith(FAILING_STREAMS_POSTFIX)) {
                    kinesisVideoClient.unregisterMediaSource(context.mediaSource);
                }
            }
            kinesisVideoClient.free();
            executor.shutdown();
            registerExecutorBadCredentialsStreams.shutdown();

            for (final Map.Entry<String, StreamContext> streamContext : testStreams.entrySet()) {
                final String streamName = streamContext.getKey();
                final StreamContext context = streamContext.getValue();

                if (streamName.endsWith(FAILING_STREAMS_POSTFIX)) {
                    // The fail stream would have still been retrying due to 403
                    assertEquals("Bad stream sent media! Received acks: " + context.acksReceived, 0, context.acksReceived.size());
                    assertEquals("Bad stream should not have seen an errors! Errors seen: " + context.errorsReceived, 0, context.errorsReceived.size());
                } else {
                    // Validate:
                    // - Received persisted acks
                    // - No errors
                    // - Largest fragment ack timecode is within ACKS_DURATION_THRESHOLD of DURATION_TO_STREAM
                    assertTrue("Success stream didn't receive any persisted ACKS!", context.acksReceived.stream().anyMatch(ack -> ack.getAckType().getIntType() == FragmentAckType.FRAGMENT_ACK_TYPE_PERSISTED));
                    assertEquals("Success stream saw an error!", 0, context.errorsReceived.size());
                    assertTrue("Success stream didn't stream for around " + DURATION_TO_STREAM.getSeconds() + " seconds!", context.acksReceived.stream().anyMatch(ack -> Math.abs(ack.getTimestamp() - DURATION_TO_STREAM.toMillis()) <= ACKS_DURATION_THRESHOLD.toMillis()));
                }
            }
        } catch (final KinesisVideoException | InterruptedException e) {
            log.error(e);
            fail(e.getMessage());
        }
    }

    private static ImageFileMediaSource createImageFileMediaSource(final String streamName, final List<KinesisVideoFragmentAck> acks, final List<Long> errors) {
        final ImageFileMediaSourceConfiguration configuration =
                new ImageFileMediaSourceConfiguration.Builder()
                        .fps(25)
                        .dir(IMAGE_DIR)
                        .filenameFormat(IMAGE_FILENAME_FORMAT)
                        .startFileIndex(START_FILE_INDEX)
                        .endFileIndex(END_FILE_INDEX)
                        .allowStreamCreation(false)
                        .streamCallbacks(new DefaultStreamCallbacks() {
                            @Override
                            public void fragmentAckReceived(final long uploadHandle, @Nonnull final KinesisVideoFragmentAck fragmentAck) throws ProducerException {
                                super.fragmentAckReceived(uploadHandle, fragmentAck);

                                acks.add(fragmentAck);
                            }

                            @Override
                            public void streamErrorReport(final long uploadHandle, final long frameTimecode, final long statusCode) throws ProducerException {
                                super.streamErrorReport(uploadHandle, frameTimecode, statusCode);

                                errors.add(statusCode);
                            }
                        })
                        .build();
        final ImageFileMediaSource mediaSource = new ImageFileMediaSource(streamName);
        mediaSource.configure(configuration);

        return mediaSource;
    }
}
