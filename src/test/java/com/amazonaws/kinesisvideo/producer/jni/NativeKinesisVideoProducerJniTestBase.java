package com.amazonaws.kinesisvideo.producer.jni;

import com.amazonaws.kinesisvideo.common.logging.Log;
import com.amazonaws.kinesisvideo.common.preconditions.Preconditions;
import com.amazonaws.kinesisvideo.producer.*;
import org.junit.After;
import org.junit.Before;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;

public class NativeKinesisVideoProducerJniTestBase implements KinesisVideoProducer {
    public static final String TEST_AUTH_BITS_STS = "Test security token bits";
    public static final byte[] TEST_STREAMING_TOKEN = new byte[] {1, 2, 3, 4, 5};

    private static final int CLIENT_CREATION_TIMEOUT_MILLIS = 5000;
    private static final int STREAM_CREATION_TIMEOUT_MILLIS = 5000;

    /**
     * MKV struct sizes as defined in the MKV generator
     */
    protected static final int MKV_SIMPLE_BLOCK_OVERHEAD = 13;
    protected static final int MKV_CLUSTER_OVERHEAD = 18 + MKV_SIMPLE_BLOCK_OVERHEAD;
    protected static final int MKV_HEADER_OVERHEAD = 223 + MKV_CLUSTER_OVERHEAD;
    protected static final int MKV_CODEC_PRIVATE_DATA_ELEM_SIZE = 2;

    protected final Log mLog = new Log(Log.SYSTEM_OUT);
    protected final AuthCallbacks mAuthCallbacks;
    protected final StorageCallbacks mStorageCallbacks;
    protected final ServiceCallbacks mServiceCallbacks;
    protected final StreamCallbacks mStreamCallbacks;
    protected final NativeKinesisVideoProducerJni mProducer;
    protected final StorageInfo mStorageInfo;
    protected final DeviceInfo mDeviceInfo;

    final CountDownLatch mClientReadyLatch = new CountDownLatch(1);
    final CountDownLatch mStreamReadyLatch = new CountDownLatch(1);

    final ExecutorService mExecutor = Executors.newFixedThreadPool(2);

    protected StreamInfo mStreamInfo;
    protected KinesisVideoProducerStream mStream;

    public NativeKinesisVideoProducerJniTestBase() throws Exception {
        mAuthCallbacks = new TestAuthCallbacks(mLog);
        mStorageCallbacks = new TestStorageCallbacks(mLog);
        mServiceCallbacks = new TestServiceCallbacks(this, mLog, mExecutor);
        mStreamCallbacks = new TestStreamCallbacks(mLog, mStreamReadyLatch);
        mStorageInfo = new StorageInfo(StorageInfo.STORAGE_INFO_CURRENT_VERSION, StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM, 10 * 1024 * 1024, 0, "/tmp");
        mDeviceInfo = new DeviceInfo(DeviceInfo.DEVICE_INFO_CURRENT_VERSION, "TestDevice", mStorageInfo, 10, null);

        mStreamInfo = new StreamInfo(StreamInfo.STREAM_INFO_CURRENT_VERSION,
                TestServiceCallbacks.TEST_STREAM_NAME,
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME,
                TestServiceCallbacks.TEST_CONTENT_TYPE,
                null,
                2 * Time.HUNDREDS_OF_NANOS_IN_AN_HOUR,
                false,
                0,
                2 * Time.HUNDREDS_OF_NANOS_IN_A_SECOND,
                true,
                true,
                false,
                true,
                true,
                "V_MPEG4/ISO/AVC",
                "test track",
                4000000,
                25,
                180 * Time.HUNDREDS_OF_NANOS_IN_A_SECOND,
                40 * Time.HUNDREDS_OF_NANOS_IN_A_SECOND,
                20 * Time.HUNDREDS_OF_NANOS_IN_A_SECOND,
                1 * Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                true,
                null,
                null,
                StreamInfo.NalAdaptationFlags.NAL_ADAPTATION_FLAG_NONE);

        mProducer = new NativeKinesisVideoProducerJni(
                mAuthCallbacks,
                mStorageCallbacks,
                mServiceCallbacks,
                mLog,
                mClientReadyLatch);

        assertNotNull(mProducer);
    }

    @Before
    public void setUp() throws Exception {
        String filePathWithoutExtension = System.getProperty("tests.additional.LD_LIBRARY_PATH");
        final Properties props = System.getProperties();
        if (filePathWithoutExtension == null || filePathWithoutExtension.isEmpty()) {
            filePathWithoutExtension = "../KinesisVideoProducerJNI/build/lib/libKinesisVideoProducerJNI";
        }

        final File file = new File(filePathWithoutExtension);
        mProducer.create(mDeviceInfo, file.getCanonicalPath());

        try {
            if (!mClientReadyLatch.await(CLIENT_CREATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                throw new Exception("Client creation time out");
            }
        } catch (final InterruptedException e) {
            throw new Exception("Client creation was interrupted");
        }
    }

    protected void createTestStream() throws Exception {
        Preconditions.checkState(isReady());

        mStream = mProducer.createStream(mStreamInfo, mStreamCallbacks);

        try {
            if (!mStreamReadyLatch.await(STREAM_CREATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                throw new Exception("Stream creation time out");
            }
        } catch (final InterruptedException e) {
            throw new Exception("Stream creation  was interrupted");
        }
    }

    @After
    public void tearDown() throws Exception  {
        if (mProducer != null && mProducer.isInitialized()) {
            mProducer.free();
        }
    }

    @Override
    public boolean isInitialized() {
        return mProducer.isInitialized();
    }

    @Override
    public boolean isReady() {
        return mProducer.isReady();
    }

    @Override
    public void create(@Nonnull final DeviceInfo deviceInfo) throws ProducerException {
        mProducer.create(deviceInfo);
    }

    @Override
    public void createSync(@Nonnull final DeviceInfo deviceInfo) throws ProducerException {
        mProducer.createSync(deviceInfo);
    }

    @Override
    public void free() throws ProducerException {
        mProducer.free();
    }

    @Override
    public void stopStreams() throws ProducerException {
        mProducer.stopStreams();
    }

    @Nonnull
    @Override
    public KinesisVideoProducerStream createStream(@Nonnull final StreamInfo streamInfo, @Nullable final StreamCallbacks streamCallbacks) throws ProducerException {
        return mProducer.createStream(streamInfo, streamCallbacks);
    }

    @Nonnull
    @Override
    public KinesisVideoProducerStream createStreamSync(@Nonnull final StreamInfo streamInfo, @Nullable final StreamCallbacks streamCallbacks) throws ProducerException {
        return mProducer.createStreamSync(streamInfo, streamCallbacks);
    }

    @Override
    public void createStreamResult(final long customData, @Nullable final String streamArn, final int httpStatusCode) throws ProducerException {
        mProducer.createStreamResult(customData, streamArn, httpStatusCode);
    }

    @Override
    public void describeStreamResult(final long customData, @Nullable final StreamDescription streamDescription, final int httpStatusCode) throws ProducerException {
        mProducer.describeStreamResult(customData, streamDescription, httpStatusCode);
    }

    @Override
    public void getStreamingEndpointResult(final long customData, @Nullable final String endpoint, final int httpStatusCode) throws ProducerException {
        mProducer.getStreamingEndpointResult(customData, endpoint, httpStatusCode);

    }

    @Override
    public void getStreamingTokenResult(final long customData, @Nullable final byte[] token, final long expiration, final int httpStatusCode) throws ProducerException {
        mProducer.getStreamingTokenResult(customData, token, expiration, httpStatusCode);
    }

    @Override
    public void putStreamResult(final long customData, final long clientStreamHandle, final int httpStatusCode) throws ProducerException {
        mProducer.putStreamResult(customData, clientStreamHandle, httpStatusCode);
    }

    @Override
    public void tagResourceResult(final long customData, final int httpStatusCode) throws ProducerException {
        mProducer.tagResourceResult(customData, httpStatusCode);
    }

    @Override
    public void createDeviceResult(final long customData, @Nullable final String deviceArn, final int httpStatusCode) throws ProducerException {
        mProducer.createDeviceResult(customData, deviceArn, httpStatusCode);
    }

    @Override
    public void deviceCertToTokenResult(final long customData, @Nullable final byte[] token, final long expiration, final int httpStatusCode) throws ProducerException {
        mProducer.deviceCertToTokenResult(customData, token, expiration, httpStatusCode);
    }

    @Nonnull
    @Override
    public KinesisVideoMetrics getMetrics() throws ProducerException {
        return mProducer.getMetrics();
    }
}