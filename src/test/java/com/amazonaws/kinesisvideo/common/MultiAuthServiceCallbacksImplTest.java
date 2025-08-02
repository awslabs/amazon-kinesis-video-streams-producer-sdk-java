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

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

public class MultiAuthServiceCallbacksImplTest {

    private static final Logger log = LogManager.getLogger(MultiAuthServiceCallbacksImplTest.class);

    private static final String IMAGE_DIR = "src/main/resources/data/h264/";
    private static final Duration DURATION_TO_STREAM = Duration.ofSeconds(30);
    private static final String IMAGE_FILENAME_FORMAT = "frame-%03d.h264";
    private static final int START_FILE_INDEX = 1;
    private static final int END_FILE_INDEX = 375;

    // How close to DURATION_TO_STREAM for the test to pass
    private static final Duration THRESHOLD = Duration.ofSeconds(3);

    // TODO: Make this number dynamic (parameterized test? with number of passing/failing streams ...)
    private String streamNamePassingStream;
    private String streamNameFailingStream;

    @Before
    public void setup() {
        assumeTrue(DefaultAWSCredentialsProviderChain.getInstance().getCredentials() != null);

        final String prefix = Optional.ofNullable(System.getenv("TEST_STREAMS_PREFIX")).orElse("");
        streamNamePassingStream = prefix + UUID.randomUUID();
        AmazonKinesisVideo awsSdkKinesisVideoClient = AmazonKinesisVideoClientBuilder.standard().build();
        CreateStreamRequest createStreamRequest = new CreateStreamRequest()
                .withStreamName(streamNamePassingStream)
                .withDataRetentionInHours(2);
        awsSdkKinesisVideoClient.createStream(createStreamRequest);

        streamNameFailingStream = prefix + UUID.randomUUID() + UUID.randomUUID() + "-fail";
        createStreamRequest = new CreateStreamRequest()
                .withStreamName(streamNameFailingStream)
                .withDataRetentionInHours(2);
        awsSdkKinesisVideoClient.createStream(createStreamRequest);
    }

    @After
    public void tearDown() {
        final AmazonKinesisVideo awsSdkKinesisVideoClient = AmazonKinesisVideoClientBuilder.standard().build();

        DescribeStreamRequest describeStreamRequest = new DescribeStreamRequest().withStreamName(streamNamePassingStream);
        DescribeStreamResult describeStreamResult = awsSdkKinesisVideoClient.describeStream(describeStreamRequest);

        DeleteStreamRequest deleteStreamRequest = new DeleteStreamRequest()
                .withStreamARN(describeStreamResult.getStreamInfo().getStreamARN())
                .withCurrentVersion(describeStreamResult.getStreamInfo().getVersion());
        awsSdkKinesisVideoClient.deleteStream(deleteStreamRequest);

        describeStreamRequest = new DescribeStreamRequest().withStreamName(streamNameFailingStream);
        describeStreamResult = awsSdkKinesisVideoClient.describeStream(describeStreamRequest);

        deleteStreamRequest = new DeleteStreamRequest()
                .withStreamARN(describeStreamResult.getStreamInfo().getStreamARN())
                .withCurrentVersion(describeStreamResult.getStreamInfo().getVersion());
        awsSdkKinesisVideoClient.deleteStream(deleteStreamRequest);
    }

    @Rule
    public Timeout globalTimeout = Timeout.seconds(45);

    @Test
    public void test() {

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
            final ScheduledExecutorService executor = Executors.newScheduledThreadPool(10,
                    new ThreadFactoryBuilder().setNameFormat("KVS-JavaClientExecutor-%d").build());
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
                if (streamInfo.getName().contains("fail")) {
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


            final String streamName1 = streamNamePassingStream;
            final String streamName2 = streamNameFailingStream;

            final List<KinesisVideoFragmentAck> acksReceivedPassStream = new ArrayList<>();
            final List<KinesisVideoFragmentAck> acksReceivedFailStream = new ArrayList<>();
            final List<Long> errorsPassStream = new ArrayList<>();
            final List<Long> errorsFailStream = new ArrayList<>();

            final MediaSource mediaSource1 = createImageFileMediaSource(streamName1, acksReceivedPassStream, errorsPassStream);
            final MediaSource mediaSource2 = createImageFileMediaSource(streamName2, acksReceivedFailStream, errorsFailStream);

            // Start #1
            kinesisVideoClient.registerMediaSource(mediaSource1);
            mediaSource1.start();

            // Media source 2 should fail but shouldn't impact #1
            new Thread(() -> {
                try {
                    kinesisVideoClient.registerMediaSource(mediaSource2);
                    fail("Creating the failed stream should have timed out!");
                } catch (final KinesisVideoException e) {
                    // Expected timeout failure
                } catch (final Throwable e) {
                    log.error("Unexpected exception creating the failure stream", e);
                    fail();
                }
            }).start();

            log.info("Main thread sleeping {} ms.", DURATION_TO_STREAM.toMillis());
            Thread.sleep(DURATION_TO_STREAM.toMillis());
            log.info("Stopping stream...");

            // unregister stream from client and free client
            kinesisVideoClient.unregisterMediaSource(mediaSource1);
            kinesisVideoClient.free();
            executor.shutdown();

            assertTrue("Success stream didn't receive any persisted ACKS!", acksReceivedPassStream.stream().anyMatch(ack -> ack.getAckType().getIntType() == FragmentAckType.FRAGMENT_ACK_TYPE_PERSISTED));
            assertEquals("Success stream saw an error!", 0, errorsPassStream.size());
            assertTrue("Success stream didn't stream for around " + DURATION_TO_STREAM.getSeconds() + " seconds!", acksReceivedPassStream.stream().anyMatch(ack -> Math.abs(ack.getTimestamp() - DURATION_TO_STREAM.toMillis()) <= THRESHOLD.toMillis()));

            // The fail stream would have still been retrying due to 403
            assertEquals("Bad stream sent media!", 0, acksReceivedFailStream.size());
            assertEquals("Bad stream should not have seen an errors", 0, errorsFailStream.size());
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
