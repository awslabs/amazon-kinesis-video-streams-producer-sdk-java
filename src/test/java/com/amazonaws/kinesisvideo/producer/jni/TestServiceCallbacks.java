package com.amazonaws.kinesisvideo.producer.jni;

import com.amazonaws.kinesisvideo.common.logging.Log;
import com.amazonaws.kinesisvideo.producer.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

import static com.amazonaws.kinesisvideo.producer.jni.NativeKinesisVideoProducerJniTestBase.TEST_STREAMING_TOKEN;

public class TestServiceCallbacks implements ServiceCallbacks {
    public static final String TEST_DEVICE_NAME = "Test device name";
    public static final String TEST_DEVICE_ARN = "Test device ARN";
    public static final String TEST_STREAM_NAME = "Test stream name";
    public static final String TEST_STREAM_ARN = "Test stream ARN";
    public static final String TEST_CONTENT_TYPE = "video/h264";
    public static final String TEST_UPDATE_VERSION = "Test update ver";
    public static final StreamStatus TEST_STREAM_STATUS = StreamStatus.ACTIVE;
    public static final String TEST_STREAMING_ENDPOINT = "Test streaming endpoint";
    public static final long TEST_TOKEN_EXPIRATION = Time.getCurrentTime() * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND
            + 60 * Time.HUNDREDS_OF_NANOS_IN_A_MINUTE; // a minute expiration
    public static final long TEST_STREAM_CLIENT_HANDLE = 0;
    public static final int TEST_CALL_RESULT_OK = 200;

    private final Log mLog;
    private final KinesisVideoProducer mKinesisVideoProducer;
    private final StreamDescription mStreamDescription;
    private final ExecutorService mExecutorService;
    private final CountDownLatch mDataAvailableLatch;

    private boolean mIsInitialized;

    public TestServiceCallbacks(final @Nonnull KinesisVideoProducer kinesisVideoProducer, final @Nonnull Log log, final @Nonnull ExecutorService executorService,
            final @Nonnull CountDownLatch dataAvailableLatch) {
        mKinesisVideoProducer = kinesisVideoProducer;
        mLog = log;
        mIsInitialized = false;
        mExecutorService = executorService;
        mDataAvailableLatch = dataAvailableLatch;

        mStreamDescription = new StreamDescription(StreamDescription.STREAM_DESCRIPTION_CURRENT_VERSION,
                TEST_DEVICE_NAME,
                TEST_STREAM_NAME,
                TEST_CONTENT_TYPE,
                TEST_UPDATE_VERSION,
                TEST_STREAM_ARN,
                TEST_STREAM_STATUS,
                Time.getCurrentTime());
    }

    @Override
    public void initialize(@Nonnull final KinesisVideoProducer kinesisVideoProducer) throws ProducerException {
        mLog.verbose("Called initialize");
        mIsInitialized = true;
    }

    @Override
    public boolean isInitialized() {
        return mIsInitialized;
    }

    @Override
    public void createStream(@Nonnull final String deviceName, @Nonnull final String streamName, @Nonnull final String contentType, @Nullable final String kmsKeyId, final long retentionPeriod, final long callAfter, final long timeout, @Nullable final byte[] authData, final int authType, final long customData) throws ProducerException {
        mLog.verbose("Called createStream");
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    mKinesisVideoProducer.createStreamResult(customData, TEST_STREAM_ARN, TEST_CALL_RESULT_OK);
                } catch (final ProducerException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void describeStream(@Nonnull final String streamName, final long callAfter, final long timeout, @Nullable final byte[] authData, final int authType, final long customData) throws ProducerException {
        mLog.verbose("Called describeStream");

        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    mKinesisVideoProducer.describeStreamResult(customData, mStreamDescription, TEST_CALL_RESULT_OK);
                } catch (final ProducerException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void getStreamingEndpoint(@Nonnull final String streamName, @Nonnull final String apiName, final long callAfter, final long timeout, @Nullable final byte[] authData, final int authType, final long customData) throws ProducerException {
        mLog.verbose("Called getStreamingEndpoint");

        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    mKinesisVideoProducer.getStreamingEndpointResult(customData, TEST_STREAMING_ENDPOINT, TEST_CALL_RESULT_OK);
                } catch (final ProducerException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void getStreamingToken(@Nonnull final String streamName, final long callAfter, final long timeout, @Nullable final byte[] authData, final int authType, final long customData) throws ProducerException {
        mLog.verbose("Called getStreamingToken");

        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    mKinesisVideoProducer.getStreamingTokenResult(customData, TEST_STREAMING_TOKEN, TEST_TOKEN_EXPIRATION, TEST_CALL_RESULT_OK);
                } catch (final ProducerException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void putStream(@Nonnull final String streamName, @Nonnull final String containerType, final long streamStartTime, final boolean absoluteFragmentTimes, final boolean ackRequired, @Nonnull final String streamingEndpoint, final long callAfter, final long timeout, @Nullable final byte[] authData, final int authType, final long customData) throws ProducerException {
        mLog.verbose("Called putStream");

        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    mKinesisVideoProducer.putStreamResult(customData, TEST_STREAM_CLIENT_HANDLE, TEST_CALL_RESULT_OK);
                    mDataAvailableLatch.countDown();
                } catch (final ProducerException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void tagResource(@Nonnull final String resourceArn, @Nullable final Tag[] tags, final long callAfter, final long timeout, @Nullable final byte[] authData, final int authType, final long customData) throws ProducerException {
        mLog.verbose("Called tagResource");

        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    mKinesisVideoProducer.tagResourceResult(customData, TEST_CALL_RESULT_OK);
                } catch (final ProducerException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void createDevice(@Nonnull final String deviceName, final long callAfter, final long timeout, @Nullable final byte[] authData, final int authType, final long customData) throws ProducerException {
        mLog.verbose("Called createDevice");

        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    mKinesisVideoProducer.createDeviceResult(customData, TEST_DEVICE_ARN, TEST_CALL_RESULT_OK);
                } catch (final ProducerException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void deviceCertToToken(@Nonnull final String deviceName, final long callAfter, final long timeout, @Nullable final byte[] authData, final int authType, final long customData) throws ProducerException {
        mLog.verbose("Called deviceCertToToken");

        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    mKinesisVideoProducer.deviceCertToTokenResult(customData, NativeKinesisVideoProducerJniTestBase.TEST_AUTH_BITS_STS.getBytes(Charset.defaultCharset()), TEST_TOKEN_EXPIRATION, TEST_CALL_RESULT_OK);
                } catch (final ProducerException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void free() {
        this.mExecutorService.shutdown();
    }
}