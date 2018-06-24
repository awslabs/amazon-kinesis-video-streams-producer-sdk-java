package com.amazonaws.kinesisvideo.client;

import static com.amazonaws.kinesisvideo.producer.StreamInfo.NalAdaptationFlags.NAL_ADAPTATION_FLAG_NONE;
import static com.amazonaws.kinesisvideo.producer.Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
import static com.amazonaws.kinesisvideo.producer.Time.HUNDREDS_OF_NANOS_IN_A_SECOND;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.ABSOLUTE_TIMECODES;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_BITRATE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_BUFFER_DURATION_IN_SECONDS;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_GOP_DURATION;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_REPLAY_DURATION_IN_SECONDS;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_STALENESS_DURATION_IN_SECONDS;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.DEFAULT_TIMESCALE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.FRAME_RATE_30;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.KEYFRAME_FRAGMENTATION;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.MAX_LATENCY_ZERO;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.NOT_ADAPTIVE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.NO_KMS_KEY_ID;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.RECALCULATE_METRICS;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.RECOVER_ON_FAILURE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.REQUEST_FRAGMENT_ACKS;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.USE_FRAME_TIMECODES;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.VERSION_ZERO;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import javax.annotation.Nonnull;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.amazonaws.kinesisvideo.client.mediasource.MediaSource;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.common.logging.Log;
import com.amazonaws.kinesisvideo.producer.AuthCallbacks;
import com.amazonaws.kinesisvideo.producer.DeviceInfo;
import com.amazonaws.kinesisvideo.producer.KinesisVideoProducer;
import com.amazonaws.kinesisvideo.producer.KinesisVideoProducerStream;
import com.amazonaws.kinesisvideo.producer.StorageCallbacks;
import com.amazonaws.kinesisvideo.producer.StorageInfo;
import com.amazonaws.kinesisvideo.producer.StreamCallbacks;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.kinesisvideo.producer.Tag;
import com.amazonaws.kinesisvideo.service.DefaultServiceCallbacksImpl;

@RunWith(MockitoJUnitRunner.class)
public class NativeKinesisVideoClientTest {
    private static final DeviceInfo DEVICE_INFO = new DeviceInfo(
            0,
            "foo", new StorageInfo(0, StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM, 0, 0, "/tmp"),
            0,
            null);
    private static final StreamInfo STREAM_INFO = new StreamInfo(VERSION_ZERO,
            "foo",
            StreamInfo.StreamingType.STREAMING_TYPE_REALTIME,
            "application/octet-stream",
            NO_KMS_KEY_ID,
            0,
            NOT_ADAPTIVE,
            MAX_LATENCY_ZERO,
            DEFAULT_GOP_DURATION * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
            KEYFRAME_FRAGMENTATION,
            USE_FRAME_TIMECODES,
            ABSOLUTE_TIMECODES,
            REQUEST_FRAGMENT_ACKS,
            RECOVER_ON_FAILURE,
            null,
            null,
            DEFAULT_BITRATE,
            FRAME_RATE_30,
            DEFAULT_BUFFER_DURATION_IN_SECONDS * HUNDREDS_OF_NANOS_IN_A_SECOND,
            DEFAULT_REPLAY_DURATION_IN_SECONDS * HUNDREDS_OF_NANOS_IN_A_SECOND,
            DEFAULT_STALENESS_DURATION_IN_SECONDS * HUNDREDS_OF_NANOS_IN_A_SECOND,
            DEFAULT_TIMESCALE,
            RECALCULATE_METRICS,
            null,
            new Tag[] {
                    new Tag("device", "Test Device"),
                    new Tag("stream", "Test Stream") },
            NAL_ADAPTATION_FLAG_NONE);
    private static final Log LOG = new Log(Log.SYSTEM_OUT);

    @Mock private AuthCallbacks authCallbacks;
    @Mock private StorageCallbacks storageCallbacks;
    @Mock private DefaultServiceCallbacksImpl serviceCallbacks;
    @Mock private StreamCallbacks streamCallbacks;
    @Mock private KinesisVideoProducerStream producerStream;
    @Mock private MediaSource mediaSource;
    @Mock private KinesisVideoProducer kinesisVideoProducer;

    private NativeKinesisVideoClient nativeKinesisVideoClient;

    @Before
    public void setUp() throws KinesisVideoException {
        // Override initializeNewKinesisVideoProducer to return our mock instead of trying to create the actual native object
        nativeKinesisVideoClient = new NativeKinesisVideoClient(LOG, authCallbacks, storageCallbacks, serviceCallbacks, streamCallbacks) {
            @Nonnull @Override KinesisVideoProducer initializeNewKinesisVideoProducer(final DeviceInfo deviceInfo) {
                return kinesisVideoProducer;
            }
        };
        nativeKinesisVideoClient.initialize(DEVICE_INFO);
    }

    @Test
    public void testRegisterMediaSource() throws KinesisVideoException {
        // given: media source providing some stream information (just reuse BytesMediaSource placeholder info for now as it's a giant object)
        given(mediaSource.getStreamInfo(any())).willReturn(STREAM_INFO);

        // and: producer returning a stream
        given(kinesisVideoProducer.createStreamSync(any(), any())).willReturn(producerStream);

        // when: stream name registered
        nativeKinesisVideoClient.registerMediaSource("streamName", mediaSource);

        // then: media source stream info queried based on that name
        then(mediaSource).should().getStreamInfo("streamName");

        // and: producer creates a stream synchronously using that stream info
        then(kinesisVideoProducer).should().createStreamSync(STREAM_INFO, streamCallbacks);

        // and: media source should be initialized
        then(mediaSource).should().initialize(any());

        // and: a callback added for that stream
        then(serviceCallbacks).should().addStream(producerStream);
    }
}
