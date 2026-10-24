package com.amazonaws.kinesisvideo.internal.producer.jni;

import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducer;
import com.amazonaws.kinesisvideo.internal.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.internal.producer.ReadResult;
import com.amazonaws.kinesisvideo.internal.producer.ServiceCallbacks;
import com.amazonaws.kinesisvideo.producer.AuthCallbacks;
import com.amazonaws.kinesisvideo.producer.AuthInfo;
import com.amazonaws.kinesisvideo.producer.AuthInfoType;
import com.amazonaws.kinesisvideo.producer.ProducerException;
import com.amazonaws.kinesisvideo.producer.StreamDescription;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.kinesisvideo.producer.StreamStatus;
import com.amazonaws.kinesisvideo.producer.Tag;
import com.amazonaws.kinesisvideo.producer.Time;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.amazonaws.kinesisvideo.internal.producer.jni.NativeKinesisVideoProducerJni.PRODUCER_NATIVE_LIBRARY_NAME;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_BITRATE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_BUFFER_DURATION;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_GOP_DURATION;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_REPLAY_DURATION;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_STALENESS_DURATION;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_TIMESCALE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.HTTP_OK;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.KEYFRAME_FRAGMENTATION;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.MAX_LATENCY_ZERO;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.NOT_ADAPTIVE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.NO_KMS_KEY_ID;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.RECALCULATE_METRICS;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.RECOVER_ON_FAILURE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.RELATIVE_TIMECODES;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.REQUEST_FRAGMENT_ACKS;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.RETENTION_ONE_HOUR;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.USE_FRAME_TIMECODES;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.VERSION_ZERO;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.VIDEO_CODEC_ID;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.VIDEO_CONTENT_TYPE;

/**
 * Auth and service callbacks that let a real native client run without AWS: every service call is answered locally.
 * PutStream starts an upload thread that drains the stream through getStreamData, the same JNI path the PutMedia client
 * uses. Shared by the JNI lifecycle and lock-order tests.
 */
final class OfflineCallbacks
{
    static final String OFFLINE_DEVICE_NAME = "offline-test-device";

    private OfflineCallbacks()
    {
    }

    static StreamInfo streamInfo(final String streamName)
    {
        return streamInfo(streamName, false);
    }

    static StreamInfo streamInfo(final String streamName, final boolean allowStreamCreation)
    {
        return streamInfo(streamName, allowStreamCreation, REQUEST_FRAGMENT_ACKS);
    }

    /**
     * @param fragmentAcks without acks PIC closes a stopped stream once it has sent everything; with acks it waits for
     *                     the last PERSISTED ack, which these callbacks never send
     */
    static StreamInfo streamInfo(final String streamName, final boolean allowStreamCreation, final boolean fragmentAcks)
    {
        return new StreamInfo(StreamInfo.STREAM_INFO_CURRENT_VERSION,
                streamName,
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME,
                VIDEO_CONTENT_TYPE,
                NO_KMS_KEY_ID,
                RETENTION_ONE_HOUR,
                NOT_ADAPTIVE,
                MAX_LATENCY_ZERO,
                DEFAULT_GOP_DURATION,
                KEYFRAME_FRAGMENTATION,
                USE_FRAME_TIMECODES,
                RELATIVE_TIMECODES,
                fragmentAcks,
                RECOVER_ON_FAILURE,
                VIDEO_CODEC_ID,
                "test-track",
                DEFAULT_BITRATE,
                50,
                DEFAULT_BUFFER_DURATION,
                DEFAULT_REPLAY_DURATION,
                DEFAULT_STALENESS_DURATION,
                DEFAULT_TIMESCALE,
                RECALCULATE_METRICS,
                null,
                new Tag[0],
                StreamInfo.NalAdaptationFlags.NAL_ADAPTATION_FLAG_NONE,
                allowStreamCreation);
    }

    static boolean isJniLoaded()
    {
        try {
            System.loadLibrary(PRODUCER_NATIVE_LIBRARY_NAME);
            return true;
        } catch (final UnsatisfiedLinkError e) {
            return false;
        }
    }

    static long expiresInOneHour()
    {
        return (System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)) * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
    }

    static final class Auth implements AuthCallbacks
    {
        @Nullable
        @Override
        public AuthInfo getDeviceCertificate()
        {
            return null;
        }

        @Nullable
        @Override
        public AuthInfo getSecurityToken()
        {
            return new AuthInfo(AuthInfoType.SECURITY_TOKEN, "offline-token".getBytes(StandardCharsets.UTF_8),
                    expiresInOneHour());
        }

        @Nullable
        @Override
        public String getDeviceFingerprint()
        {
            return null;
        }
    }

    /**
     * Answers every service call locally. putStream starts an upload thread that drains the stream through
     * getStreamData, the same JNI path the PutMedia client uses.
     */
    static class Service implements ServiceCallbacks
    {
        private final ExecutorService mExecutor;
        final ExecutorService mUploaders;
        private final AtomicLong mNextUploadHandle = new AtomicLong(1);
        volatile KinesisVideoProducer mProducer;

        Service(final ExecutorService executor, final ExecutorService uploaders)
        {
            mExecutor = executor;
            mUploaders = uploaders;
        }

        interface Result
        {
            void deliver() throws ProducerException;
        }

        void deliver(final Result result)
        {
            mExecutor.execute(() -> {
                try {
                    result.deliver();
                } catch (final ProducerException | IllegalStateException | IllegalArgumentException e) {
                    // The client or the stream was freed before the result arrived
                }
            });
        }

        static String arn(final String streamName)
        {
            return "arn:aws:kinesisvideo:us-west-2:123456789012:stream/" + streamName + "/1";
        }

        @Override
        public void initialize(@Nonnull final KinesisVideoProducer kinesisVideoProducer)
        {
            mProducer = kinesisVideoProducer;
        }

        @Override
        public void free()
        {
        }

        @Override
        public boolean isInitialized()
        {
            return mProducer != null;
        }

        @Override
        public void createStream(@Nonnull final String deviceName, @Nonnull final String streamName,
                                 @Nonnull final String contentType, @Nullable final String kmsKeyId,
                                 final long retentionPeriod, final long callAfter, final long timeout,
                                 @Nullable final byte[] authData, final int authType,
                                 @Nonnull final KinesisVideoProducerStream stream)
        {
            deliver(() -> mProducer.createStreamResult(stream, arn(streamName), HTTP_OK));
        }

        @Override
        public void describeStream(@Nonnull final String streamName, final long callAfter, final long timeout,
                                   @Nullable final byte[] authData, final int authType, final long streamHandle,
                                   final KinesisVideoProducerStream stream)
        {
            final StreamDescription description = new StreamDescription(VERSION_ZERO, OFFLINE_DEVICE_NAME, streamName,
                    VIDEO_CONTENT_TYPE, "1", arn(streamName), StreamStatus.ACTIVE,
                    System.currentTimeMillis() * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND, RETENTION_ONE_HOUR, "");
            deliver(() -> mProducer.describeStreamResult(stream, streamHandle, description, HTTP_OK));
        }

        @Override
        public void getStreamingEndpoint(@Nonnull final String streamName, @Nonnull final String apiName,
                                         final long callAfter, final long timeout, @Nullable final byte[] authData,
                                         final int authType, final long streamHandle,
                                         final KinesisVideoProducerStream stream)
        {
            deliver(() -> mProducer.getStreamingEndpointResult(stream, streamHandle, "https://localhost", HTTP_OK));
        }

        @Override
        public void getStreamingToken(@Nonnull final String streamName, final long callAfter, final long timeout,
                                      @Nullable final byte[] authData, final int authType, final long streamHandle,
                                      final KinesisVideoProducerStream stream)
        {
            deliver(() -> mProducer.getStreamingTokenResult(stream, streamHandle,
                    "offline-token".getBytes(StandardCharsets.UTF_8), expiresInOneHour(), HTTP_OK));
        }

        @Override
        public void putStream(@Nonnull final String streamName, @Nonnull final String containerType,
                              final long streamStartTime, final boolean absoluteFragmentTimes,
                              final boolean ackRequired, @Nonnull final String streamingEndpoint,
                              final long callAfter, final long timeout, @Nullable final byte[] authData,
                              final int authType, final KinesisVideoProducerStream stream)
        {
            final long uploadHandle = mNextUploadHandle.getAndIncrement();
            deliver(() -> {
                mProducer.putStreamResult(stream, uploadHandle, HTTP_OK);
                mUploaders.execute(() -> drain(stream, uploadHandle));
            });
        }

        static void drain(final KinesisVideoProducerStream stream, final long uploadHandle)
        {
            final byte[] buffer = new byte[16 * 1024];
            final ReadResult readResult = new ReadResult();
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    stream.getStreamData(uploadHandle, buffer, 0, buffer.length, readResult);
                    if (readResult.isEndOfStream()) {
                        return;
                    }
                    if (readResult.getReadBytes() == 0) {
                        Thread.sleep(1);
                    }
                } catch (final InterruptedException e) {
                    return;
                } catch (final Exception e) {
                    // The stream or the client has been freed
                    return;
                }
            }
        }

        @Override
        public void tagResource(@Nonnull final String resourceArn, @Nullable final Tag[] tags, final long callAfter,
                                final long timeout, @Nullable final byte[] authData, final int authType,
                                final long streamHandle, final KinesisVideoProducerStream stream)
        {
            deliver(() -> mProducer.tagResourceResult(stream, streamHandle, HTTP_OK));
        }

        @Override
        public void createDevice(@Nonnull final String deviceName, final long callAfter, final long timeout,
                                 @Nullable final byte[] authData, final int authType, final long customData)
        {
            deliver(() -> mProducer.createDeviceResult(customData, "arn:aws:kinesisvideo:us-west-2:123456789012:device/"
                    + deviceName, HTTP_OK));
        }

        @Override
        public void deviceCertToToken(@Nonnull final String deviceName, final long callAfter, final long timeout,
                                      @Nullable final byte[] authData, final int authType, final long customData)
        {
            deliver(() -> mProducer.deviceCertToTokenResult(customData,
                    "offline-token".getBytes(StandardCharsets.UTF_8), expiresInOneHour(), HTTP_OK));
        }

        @Override
        public void addStream(@Nonnull final KinesisVideoProducerStream kinesisVideoProducerStream)
        {
        }

        @Override
        public void removeStream(@Nonnull final KinesisVideoProducerStream kinesisVideoProducerStream)
        {
        }
    }
}
