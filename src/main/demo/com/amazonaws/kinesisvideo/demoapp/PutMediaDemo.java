package com.amazonaws.kinesisvideo.demoapp;


import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentials;
import com.amazonaws.kinesisvideo.auth.KinesisVideoCredentialsProvider;
import com.amazonaws.kinesisvideo.client.KinesisVideoClientConfiguration;
import com.amazonaws.kinesisvideo.client.PutMediaClient;
import com.amazonaws.kinesisvideo.client.signing.AWSKinesisVideoV4Signer;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.common.function.Consumer;
import com.amazonaws.kinesisvideo.common.logging.Log;
import com.amazonaws.kinesisvideo.config.ClientConfiguration;
import com.amazonaws.kinesisvideo.demoapp.auth.AuthHelper;
import com.amazonaws.kinesisvideo.java.auth.JavaCredentialsProviderImpl;
import com.amazonaws.kinesisvideo.java.client.JavaKinesisVideoClient;
import com.amazonaws.kinesisvideo.java.client.KinesisVideoJavaClientFactory;
import com.amazonaws.kinesisvideo.java.service.JavaKinesisVideoServiceClient;
import com.amazonaws.kinesisvideo.producer.client.KinesisVideoServiceClient;
import com.amazonaws.kinesisvideo.service.DefaultServiceCallbacksImpl;
import com.amazonaws.kinesisvideo.signing.KinesisVideoSigner;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoAsyncClient;
import com.amazonaws.services.kinesisvideo.model.GetDataEndpointRequest;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.apache.commons.codec.CharEncoding.UTF_8;

/**
 * An example on how to send an MKV file to Kinesis Video Streams.
 *
 * If you have other video formats, you can use ffmpeg to convert to MKV. Only H264 videos are playable in the console.
 * Steps to convert MP4 to MKV:
 *
 * 1. Install ffmpeg if not yet done so already:
 *
 *      Mac OS X:
 *          brew install ffmpeg --with-opus --with-fdk-aac --with-tools --with-freetype --with-libass --with-libvorbis --with-libvpx --with-x265 --with-libopus
 *
 *      Others:
 *          git clone https://git.ffmpeg.org/ffmpeg.git ffmpeg
 *          ./configure
 *          make
 *          make install
 *
 *  2. Convert MP4 to MKV
 *      ffmpeg -i input.mp4 -b:v 10M -minrate 10M -maxrate 10M -bufsize 10M -bf 0 input.mkv
 */
public class PutMediaDemo {
    private static final String DEFAULT_REGION = "us-west-2";
    private static final String KINESISVIDEO_SERVICE_NAME = "kinesisvideo";
    private static final String PUT_MEDIA_API = "/putMedia";

    /* the name of the stream */
    private static final String STREAM_NAME = "my-stream";

    /* sample MKV file */
    private static final String MKV_FILE_PATH = "src/main/resources/data/mkv/sample-30s-h264-1080p-30fps-15Mbps.mkv";

    /* max upload bandwidth */
    private static final long MAX_BANDWIDTH_KBPS = 15 * 1024L;

    /* response read timeout */
    private static final int READ_TIMEOUT_IN_MILLIS = 1_000_000;

    /* connect timeout */
    private static final int CONNECTION_TIMEOUT_IN_MILLIS = 10_000;

    public static void main(final String[] args) throws Exception {
        AmazonKinesisVideo frontendClient = AmazonKinesisVideoAsyncClient.builder()
                .withCredentials(AuthHelper.getSystemPropertiesCredentialsProvider())
                .withRegion(DEFAULT_REGION)
                .build();

        /* this is the endpoint returned by GetDataEndpoint API */
        String dataEndpoint = frontendClient.getDataEndpoint(
                new GetDataEndpointRequest()
                    .withStreamName(STREAM_NAME)
                    .withAPIName("PUT_MEDIA")).getDataEndpoint();

        /* send the same MKV file over and over */
        while (true) {
            /* actually URI to send PutMedia request */
            final URI uri = URI.create(dataEndpoint + PUT_MEDIA_API);

            /* input stream for sample MKV file */
            final InputStream inputStream = new FileInputStream(MKV_FILE_PATH);

            /* use a latch for main thread to wait for response to complete */
            final CountDownLatch latch = new CountDownLatch(1);

            /* a consumer for PutMedia ACK events */
            final AckConsumer ackConsumer = new AckConsumer(latch);

            /* client configuration used for AWS SigV4 signer */
            final ClientConfiguration configuration = getClientConfiguration(uri);

            /* PutMedia client */
            final PutMediaClient client = PutMediaClient.builder()
                    .putMediaDestinationUri(uri)
                    .mkvStream(inputStream)
                    .streamName(STREAM_NAME)
                    .timestamp(System.currentTimeMillis())
                    .fragmentTimecodeType("RELATIVE")
                    .signWith(getKinesisVideoSigner(configuration))
                    .upstreamKbps(MAX_BANDWIDTH_KBPS)
                    .receiveAcks(ackConsumer)
                    .build();

            /* start streaming video in a background thread */
            client.putMediaInBackground();

            /* wait for request/response to complete */
            latch.await();

            /* close the client */
            client.close();
        }
    }

    private static KinesisVideoSigner getKinesisVideoSigner(final ClientConfiguration configuration) {
        return new AWSKinesisVideoV4Signer(AuthHelper.getSystemPropertiesCredentialsProvider(),
                configuration,
                true);
    }

    private static ClientConfiguration getClientConfiguration(final URI uri) {
        return ClientConfiguration.builder()
                .apiName(PUT_MEDIA_API)
                .streamName(STREAM_NAME)
                .streamUri(uri)
                .region(DEFAULT_REGION)
                .serviceName(KINESISVIDEO_SERVICE_NAME)
                .readTimeoutInMillis(READ_TIMEOUT_IN_MILLIS)
                .connectionTimeoutInMillis(CONNECTION_TIMEOUT_IN_MILLIS)
                .build();
    }

    private static class AckConsumer implements Consumer<InputStream> {
        private static final String END_OF_CHUNKED_DATA = "0";
        private final CountDownLatch latch;

        public AckConsumer(final CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void accept(final InputStream inputStream) {
            try {
                final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, UTF_8));
                String line = null;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                    if (END_OF_CHUNKED_DATA.equals(line)) {
                        System.out.println("Received EOF for HTTP chunked encoding data.");
                        break;
                    }
                }
            } catch (final Throwable t) {
                System.out.println("Exception while reading output stream " + t.getMessage());
                throw new RuntimeException(t);
            } finally {
                latch.countDown();
            }
        }
    }
}
