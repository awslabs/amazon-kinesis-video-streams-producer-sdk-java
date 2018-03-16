package com.amazonaws.kinesisvideo.client.stream;

import com.amazonaws.kinesisvideo.client.PutMediaClient;
import com.amazonaws.kinesisvideo.common.function.Consumer;
import com.amazonaws.kinesisvideo.config.ClientConfiguration;
import com.amazonaws.kinesisvideo.signing.KinesisVideoSigner;
import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javax.annotation.WillClose;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Put Media manager responsible for sending and receiving streams to the InletService using PutMedia api.
 *
 * @author bdhandap
 */
public class PutMediaManager {
    private final KinesisVideoSigner signer;
    private static final String RELATIVE = "RELATIVE";
    private static final String ABSOLUTE = "ABSOLUTE";

    @SuppressFBWarnings("OBL_UNSATISFIED_OBLIGATION")
    public void sendTestMkvStream(final ClientConfiguration config) throws Exception {
        @WillClose
        final InputStream testMkvStream = new FileInputStream("testdata/test_depth_cal.mkv");
        final Long streamStartTime = 1498511782000L;
        //TODO: Add as a cmd line parameter.
        PutMediaClient.builder().putMediaDestinationUri(config.getStreamUri()).mkvStream(testMkvStream).streamName(config.getStreamName()).timestamp(streamStartTime).fragmentTimecodeType(ABSOLUTE).signWith(signer).receiveAcks(new StreamConsumer(config.getApiName())).build().putMediaInBackground();
    }

    public void sendMkvStreamWithAbsoluteFragmentTimeCode(final ClientConfiguration config, final InputStream inputStream, final Consumer<InputStream> consumer, final Integer receiveTimeout) throws Exception {
        PutMediaClient.builder().putMediaDestinationUri(config.getStreamUri()).mkvStream(inputStream).streamName(config.getStreamName()).fragmentTimecodeType(ABSOLUTE).signWith(signer).receiveAcks(consumer).receiveTimeout(receiveTimeout).build().putMediaInBackground();
    }

    @Inject
    public PutMediaManager(final KinesisVideoSigner signer) {
        this.signer = signer;
    }
}
