package com.amazonaws.kinesisvideo.demoapp;

import java.awt.Dimension;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.kinesisvideo.client.KinesisVideoClient;
import com.amazonaws.kinesisvideo.client.mediasource.CameraMediaSourceConfiguration;
import com.amazonaws.kinesisvideo.client.mediasource.MediaSource;
import com.amazonaws.kinesisvideo.client.mediasource.MediaSourceState;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.demoapp.auth.AuthHelper;
import com.amazonaws.kinesisvideo.java.client.KinesisVideoJavaClientFactory;
import com.amazonaws.kinesisvideo.java.logging.SysOutLogChannel;
import com.amazonaws.kinesisvideo.java.mediasource.camera.CameraMediaSource;
import com.amazonaws.kinesisvideo.java.mediasource.file.ImageFileMediaSource;
import com.amazonaws.kinesisvideo.java.mediasource.file.ImageFileMediaSourceConfiguration;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.regions.Regions;
import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import com.github.sarxos.webcam.util.ImageUtils;

/**
 * Demo Java Producer.
 */
public final class DemoAppMain {
    private static final String STREAM_NAME = "my-stream";
    private static final int FPS_22 = 22;
    private static final int FPS_24 = 24;
    private static final int FPS_30 = 30;
    private static final String IMAGE_DIR = "src/main/resources/data/h264/";
    private static final String IMAGE_FILENAME_FORMAT = "frame-%03d.h264";
    private static final int START_FILE_INDEX = 1;
    private static final int END_FILE_INDEX = 299;

    private DemoAppMain() {
        throw new UnsupportedOperationException();
    }

    public static void main(final String[] args) {
        try {
            // create Kinesis Video high level client
        	final KinesisVideoClient kinesisVideoClient = KinesisVideoJavaClientFactory
                    .createKinesisVideoClient(new ProfileCredentialsProvider("default"));

            // create a media source. this class produces the data and pushes it into
            // Kinesis Video Producer lower level components
            final MediaSource bytesMediaSource = createCameraMediaSource();

            // register media source with Kinesis Video Client
            kinesisVideoClient.registerMediaSource(STREAM_NAME, bytesMediaSource);
            
            

            // start streaming
            bytesMediaSource.start();
        } catch (final KinesisVideoException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create a MediaSource based on local sample H.264 frames.
     *
     * @return a MediaSource backed by local H264 frame files
     */
    private static MediaSource createImageFileMediaSource() {
        final ImageFileMediaSourceConfiguration configuration =
                new ImageFileMediaSourceConfiguration.Builder()
                        .fps(FPS_24)
                        .dir(IMAGE_DIR)
                        .filenameFormat(IMAGE_FILENAME_FORMAT)
                        .startFileIndex(START_FILE_INDEX)
                        .endFileIndex(END_FILE_INDEX)
                        .build();
        final ImageFileMediaSource mediaSource = new ImageFileMediaSource();
        mediaSource.configure(configuration);

        return mediaSource;
    }
    
    private static MediaSource createCameraMediaSource() {
    	
    	Webcam webcam = Webcam.getDefault();
    	
    	final CameraMediaSourceConfiguration configuration =
    			new CameraMediaSourceConfiguration.Builder()
    			.withFrameRate(FPS_22)
    			.withRetentionPeriodInHours(1)
    			.withCameraId("/dev/video0")
    			.withIsEncoderHardwareAccelerated(false)
    			.withEncodingMimeType("video/avc")
    			.withNalAdaptationFlags(StreamInfo.NalAdaptationFlags.NAL_ADAPTATION_FLAG_NONE)
    			.withIsAbsoluteTimecode(false)
    			.withEncodingBitRate(200000)
    			.withHorizontalResolution(640)
    			.withVerticalResolution(480)
    			.build();
    	
    	final CameraMediaSource mediaSource = new CameraMediaSource();
    	mediaSource.setupWebCam(webcam);
    	mediaSource.configure(configuration);
    	return mediaSource;    	
    }
}
