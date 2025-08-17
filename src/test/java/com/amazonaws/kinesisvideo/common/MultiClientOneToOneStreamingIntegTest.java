package com.amazonaws.kinesisvideo.common;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.kinesisvideo.auth.DefaultAuthCallbacks;
import com.amazonaws.kinesisvideo.client.KinesisVideoClientConfiguration;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducer;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.internal.producer.jni.NativeKinesisVideoProducerJni;
import com.amazonaws.kinesisvideo.internal.service.DefaultServiceCallbacksImpl;
import com.amazonaws.kinesisvideo.java.auth.JavaCredentialsFactory;
import com.amazonaws.kinesisvideo.java.service.JavaKinesisVideoServiceClient;
import com.amazonaws.kinesisvideo.producer.DeviceInfo;
import com.amazonaws.kinesisvideo.producer.FragmentAckType;
import com.amazonaws.kinesisvideo.producer.FrameFlags;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFragmentAck;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.producer.MkvTrackInfoType;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.StorageInfo;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.kinesisvideo.producer.Tag;
import com.amazonaws.kinesisvideo.producer.Time;
import com.amazonaws.kinesisvideo.producer.TrackInfo;
import com.amazonaws.kinesisvideo.storage.DefaultStorageCallbacks;
import com.amazonaws.kinesisvideo.streaming.DefaultStreamCallbacks;
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.amazonaws.kinesisvideo.internal.producer.jni.NativeKinesisVideoProducerJni.PRODUCER_NATIVE_LIBRARY_NAME;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.HTTP_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public class MultiClientOneToOneStreamingIntegTest extends ProducerTestBase {
    private static final Logger log = LogManager.getLogger(MultiClientOneToOneStreamingIntegTest.class);
    private static final String AUDIO_VIDEO_FRAMES_DIR = "src/main/resources/data/audio-video-frames";
    private static final long FRAME_DURATION_IN_MS = 20L;
    private static final long WAIT_FOR_ACKS_MS = 5000L;
    private static final int FRAMES_TO_STREAM = 100;
    private static final int STORAGE_SIZE_MB = 10;
    private static final int STREAM_TIMEOUT_SECONDS = 120;
    private static final int EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 5;

    private static final int AUDIO_TRACK_ONE_ID = 3;
    private static final int AUDIO_TRACK_TWO_ID = 4;

    private static final String AUDIO_MIME_TYPE = "audio/aac";
    private static final String AUDIO_CODEC_ID = "A_AAC";
    private static final String AUDIO_TRACK_NAME = "AudioTrack";
    private static final byte[] AUDIO_CPD = null;
    private static final long FRAGMENT_DURATION_HUNDREDS_OF_NANOS = Time.HUNDREDS_OF_NANOS_IN_A_SECOND; // 1 second

    private final int numClients;
    private ExecutorService testExecutor;
    private List<ClientContext> clientContexts;
    private AmazonKinesisVideo awsSdkClient;

    @Parameterized.Parameters(name = "clients&streams={0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[][]{
                        {3},
                        {5},
                }
        );
    }

    public MultiClientOneToOneStreamingIntegTest(final int numClients) {
        assumeTrue("There needs to be at least 1 client!", numClients >= 1);
        this.numClients = numClients;
    }

    private static class ClientContext {
        String streamName;
        KinesisVideoProducer producer;
        KinesisVideoProducerStream stream;

        // Executor for the streamCallbacks and authCallbacks
        ScheduledExecutorService executor;

        // List of the fragment ACKs received from KVS
        final List<KinesisVideoFragmentAck> fragmentAcks = new ArrayList<>();

        // True only after all the frames were submitted successfully on the streaming thread
        final AtomicBoolean streamingComplete = new AtomicBoolean(false);

        // Not null if an exception was received on the streaming thread
        @Nullable
        Exception streamingException;
    }

    @Before
    public void setUp() {
        try {
            System.loadLibrary(PRODUCER_NATIVE_LIBRARY_NAME);
        } catch (final UnsatisfiedLinkError e) {
            fail("JNI library not found.");
        }

        assumeTrue("Unable to find AWS credentials!", DefaultAWSCredentialsProviderChain.getInstance().getCredentials() != null);

        this.testExecutor = Executors.newFixedThreadPool(this.numClients * 2, new ThreadFactoryBuilder().setNameFormat("test-executor-%d").build());
        this.clientContexts = new ArrayList<>();
        this.awsSdkClient = AmazonKinesisVideoClientBuilder.standard().build();

        final String prefix = Optional.ofNullable(System.getenv("TEST_STREAMS_PREFIX")).orElse("");

        for (int i = 0; i < this.numClients; i++) {
            final ClientContext context = new ClientContext();
            context.streamName = String.join("-", prefix, "multi-client-audio-stream",
                    Integer.toString(i), Long.toString(System.currentTimeMillis()), UUID.randomUUID().toString());

            try {
                log.info("Creating stream {}", context.streamName);
                final CreateStreamRequest request = new CreateStreamRequest()
                        .withStreamName(context.streamName)
                        .withDataRetentionInHours(2)
                        .withMediaType(AUDIO_MIME_TYPE);
                this.awsSdkClient.createStream(request);
                this.clientContexts.add(context);
            } catch (final Exception e) {
                log.error("Failed to create stream: {}", context.streamName, e);
                fail("Failed to create stream: " + context.streamName);
            }
        }
    }

    @After
    public void tearDown() {
        boolean success = true;

        for (final ClientContext context : this.clientContexts) {
            try {
                if (context.stream != null) {
                    context.stream.stopStreamSync();
                }
                if (context.producer != null) {
                    context.producer.freeStreams();
                    context.producer.free();
                }
                if (context.executor != null) {
                    context.executor.shutdown();
                    if (!context.executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        log.error("Timeout waiting for {} executor to shut down", context.streamName);
                        success = false;
                    }
                }
            } catch (final Exception e) {
                log.warn("Error during cleanup for stream: {}", context.streamName, e);
                success = false;
            }
        }

        if (this.testExecutor != null) {
            this.testExecutor.shutdown();
            try {
                if (!this.testExecutor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    log.error("Timeout waiting for test executor to shut down");
                    success = false;
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (!deleteStreams()) {
            success = false;
        }
        assertTrue("Cleanup failed", success);
    }

    @Test
    public void testMultiClientAudioStreaming() throws Exception {
        log.info("Testing {} clients streaming audio concurrently", this.numClients);

        createClientsAndStreams();

        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch completionLatch = new CountDownLatch(this.numClients);

        for (final ClientContext context : this.clientContexts) {
            this.testExecutor.submit(() -> {
                try {
                    startLatch.await();
                    streamAudioFrames(context);
                    context.streamingComplete.set(true);
                } catch (final Exception e) {
                    context.streamingException = e;
                    log.error("Streaming failed for {}", context.streamName, e);
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue("Streaming did not complete in time",
                completionLatch.await(STREAM_TIMEOUT_SECONDS, TimeUnit.SECONDS));

        verifyResults();
    }

    /**
     * Creates {@link #numClients} clients, each with one stream each.
     */
    private void createClientsAndStreams() throws Exception {
        final KinesisVideoClientConfiguration config = KinesisVideoClientConfiguration.builder()
                .withCredentialsProvider(JavaCredentialsFactory.createKinesisVideoCredentialsProvider(
                        DefaultAWSCredentialsProviderChain.getInstance()))
                .build();

        for (int i = 0; i < this.clientContexts.size(); i++) {
            final ClientContext context = this.clientContexts.get(i);
            context.executor = Executors.newScheduledThreadPool(2,
                    new ThreadFactoryBuilder()
                            .setNameFormat("test-client-%d")
                            .build()
            );

            final DeviceInfo deviceInfo = createDeviceInfo("audio-device-" + i);

            final DefaultAuthCallbacks authCallbacks = new DefaultAuthCallbacks(
                    config.getCredentialsProvider(), context.executor, log);
            final DefaultStorageCallbacks storageCallbacks = new DefaultStorageCallbacks();
            final DefaultServiceCallbacksImpl serviceCallbacks = new DefaultServiceCallbacksImpl(
                    log, context.executor, config, new JavaKinesisVideoServiceClient());

            context.producer = new NativeKinesisVideoProducerJni(authCallbacks,
                    storageCallbacks, serviceCallbacks, log);
            context.producer.createSync(deviceInfo);

            final StreamInfo streamInfo = createAudioStreamInfo(context.streamName);
            context.stream = context.producer.createStreamSync(streamInfo, new DefaultStreamCallbacks() {
                @Override
                public void fragmentAckReceived(final long uploadHandle, @Nonnull final KinesisVideoFragmentAck fragmentAck)
                        throws ProducerException {
                    super.fragmentAckReceived(uploadHandle, fragmentAck);
                    synchronized (context.fragmentAcks) {
                        context.fragmentAcks.add(fragmentAck);
                    }
                    log.debug("Received ACK for {}: {}", context.streamName, fragmentAck);
                }
            });
        }
    }

    @SuppressWarnings("BusyWait")
    private void streamAudioFrames(@Nonnull final ClientContext context) throws Exception {
        assumeNotNull(context, "Context cannot be null!");

        final File framesDir = new File(AUDIO_VIDEO_FRAMES_DIR);
        final File[] audioFiles = framesDir.listFiles((dir, name) -> name.contains("audio"));

        if (audioFiles == null || audioFiles.length == 0) {
            fail("No audio frames found in: " + AUDIO_VIDEO_FRAMES_DIR);
        }

        Arrays.sort(audioFiles, Comparator.comparing(File::getName));

        log.info("Streaming {} audio frames for {}", audioFiles.length, context.streamName);

        for (int i = 0; i < Math.min(audioFiles.length, FRAMES_TO_STREAM); i++) {
            final File audioFile = audioFiles[i];
            final byte[] frameData = Files.readAllBytes(audioFile.toPath());

            final long timestamp = System.currentTimeMillis() * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;

            // Even though every frame is a key frame,
            // we use the keyFrameFragmentation = off
            final KinesisVideoFrame trackOneFrame = new KinesisVideoFrame(
                    i,
                    FrameFlags.FRAME_FLAG_KEY_FRAME,
                    timestamp,
                    timestamp,
                    FRAME_DURATION_IN_MS * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                    ByteBuffer.wrap(frameData),
                    AUDIO_TRACK_ONE_ID
            );

            context.stream.putFrame(trackOneFrame);

            // Even though every frame is a key frame,
            // we use the keyFrameFragmentation = off
            final KinesisVideoFrame trackTwoFrame = new KinesisVideoFrame(
                    i + 1,
                    FrameFlags.FRAME_FLAG_KEY_FRAME,
                    timestamp + 1,
                    timestamp + 1,
                    FRAME_DURATION_IN_MS * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                    ByteBuffer.wrap(frameData),
                    AUDIO_TRACK_TWO_ID
            );

            context.stream.putFrame(trackTwoFrame);
            Thread.sleep(FRAME_DURATION_IN_MS);
        }

        Thread.sleep(WAIT_FOR_ACKS_MS);
    }

    @Nonnull
    @SuppressWarnings({"ConstantConditions", "ExtractMethodRecommender"})
    private DeviceInfo createDeviceInfo(@Nonnull final String deviceName) {
        assumeNotNull("DeviceName cannot be null!", deviceName);
        assumeTrue("DeviceName cannot be empty!", !deviceName.isEmpty());

        final int storageSizeTenMBs = STORAGE_SIZE_MB * 1024 * 1024;
        final int storageVersion = 0;
        final StorageInfo.DeviceStorageType storageType = StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM;
        final int spillRatioNinetyPercent = 90;
        final String rootDirectory = "/tmp";

        final StorageInfo storageInfo = new StorageInfo(storageVersion,
                storageType,
                storageSizeTenMBs,
                spillRatioNinetyPercent,
                rootDirectory);

        final int deviceInfoVersion = 0;
        final int streamCount = 1;
        final Tag[] tags = null;

        return new DeviceInfo(deviceInfoVersion,
                deviceName,
                storageInfo,
                streamCount,
                tags);
    }

    /**
     * Creates the StreamInfo with the given streamName for an {@value #AUDIO_CODEC_ID} audio stream.
     */
    @Nonnull
    private StreamInfo createAudioStreamInfo(@Nonnull final String streamName) {
        assumeNotNull("StreamName cannot be null!", streamName);
        assumeTrue("StreamName cannot be empty!", !streamName.isEmpty());

        final TrackInfo audioTrackOne = new TrackInfo(AUDIO_TRACK_ONE_ID,
                AUDIO_CODEC_ID,
                AUDIO_TRACK_NAME,
                AUDIO_CPD,
                MkvTrackInfoType.AUDIO);

        final TrackInfo audioTrackTwo = new TrackInfo(AUDIO_TRACK_TWO_ID,
                AUDIO_CODEC_ID,
                AUDIO_TRACK_NAME,
                AUDIO_CPD,
                MkvTrackInfoType.AUDIO);

        return new StreamInfo(0,
                streamName,
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME,
                AUDIO_MIME_TYPE,
                null,
                24 * 60 * 60 * 1000L * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                false,
                0,
                FRAGMENT_DURATION_HUNDREDS_OF_NANOS,
                false,
                true,
                false,
                false,
                true,
                2000000,
                25,
                40 * Time.HUNDREDS_OF_NANOS_IN_A_SECOND,
                20 * Time.HUNDREDS_OF_NANOS_IN_A_SECOND,
                20 * Time.HUNDREDS_OF_NANOS_IN_A_SECOND,
                10000,
                true,
                null,
                StreamInfo.NalAdaptationFlags.NAL_ADAPTATION_FLAG_NONE,
                null,
                new TrackInfo[]{audioTrackOne, audioTrackTwo},
                false);
    }

    /**
     * Verifies:
     * <ol>
     *     <li>No exceptions were thrown</li>
     *     <li>Streaming completed successfully</li>
     *     <li>Received at least one persisted ACKs</li>
     *     <li>No errored ACKs received</li>
     * </ol>
     */
    private void verifyResults() {
        for (final ClientContext context : this.clientContexts) {
            assertNull("Streaming failed for " + context.streamName, context.streamingException);
            assertTrue("Streaming not completed for " + context.streamName, context.streamingComplete.get());

            synchronized (context.fragmentAcks) {
                assertFalse("No fragment ACKs received for " + context.streamName,
                        context.fragmentAcks.isEmpty());

                final long persistedAcks = context.fragmentAcks.stream()
                        .filter(ack -> ack.getAckType().getIntType() == FragmentAckType.FRAGMENT_ACK_TYPE_PERSISTED)
                        .count();

                assertTrue("No persisted ACKs received for " + context.streamName, persistedAcks > 0);

                for (final KinesisVideoFragmentAck ack : context.fragmentAcks) {
                    assertEquals("Non-successful ACK received for " + context.streamName + ": " + ack,
                            HTTP_OK, ack.getResult());
                }
            }
        }
    }

    /**
     * Delete all the Amazon Kinesis Video streams created by this test run.
     *
     * @return true if all the streams were deleted successfully, false if an error was encountered
     */
    private boolean deleteStreams() {
        boolean success = true;
        for (final ClientContext context : this.clientContexts) {
            try {
                log.info("Deleting stream {}", context.streamName);
                final DescribeStreamRequest describeRequest = new DescribeStreamRequest()
                        .withStreamName(context.streamName);
                final DescribeStreamResult describeResult = this.awsSdkClient.describeStream(describeRequest);

                final DeleteStreamRequest deleteRequest = new DeleteStreamRequest()
                        .withStreamARN(describeResult.getStreamInfo().getStreamARN())
                        .withCurrentVersion(describeResult.getStreamInfo().getVersion());
                this.awsSdkClient.deleteStream(deleteRequest);
            } catch (final Exception e) {
                log.error("Failed to delete stream: {}", context.streamName, e);
                success = false;
            }
        }
        return success;
    }
}
