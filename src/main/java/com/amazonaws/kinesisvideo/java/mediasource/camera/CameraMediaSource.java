package com.amazonaws.kinesisvideo.java.mediasource.camera;

import com.amazonaws.kinesisvideo.client.mediasource.CameraMediaSourceConfiguration;
import com.amazonaws.kinesisvideo.client.mediasource.MediaSource;
import com.amazonaws.kinesisvideo.client.mediasource.MediaSourceConfiguration;
import com.amazonaws.kinesisvideo.client.mediasource.MediaSourceSink;
import com.amazonaws.kinesisvideo.client.mediasource.MediaSourceState;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.java.mediasource.file.ImageFileMediaSourceConfiguration;
import com.amazonaws.kinesisvideo.java.mediasource.file.ImageFrameSource;
import com.amazonaws.kinesisvideo.mediasource.OnFrameDataAvailable;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.github.sarxos.webcam.Webcam;

import java.nio.ByteBuffer;

import org.openimaj.image.ImageUtilities;
import org.openimaj.image.processing.face.detection.DetectedFace;
import org.openimaj.image.processing.face.detection.HaarCascadeDetector;
import org.openimaj.math.geometry.shape.Rectangle;

public class CameraMediaSource implements MediaSource {

    private static final int FRAME_FLAG_KEY_FRAME = 1;
    private static final int FRAME_FLAG_NONE = 0;
    private static final long HUNDREDS_OF_NANOS_IN_MS = 10 * 1000;
    private static final long FRAME_DURATION_20_MS = 20L;
    
	private CameraMediaSourceConfiguration cameraMediaSourceConfiguration;
    private MediaSourceState mediaSourceState;
    private MediaSourceSink mediaSourceSink;
    private CameraFrameSource cameraFrameSource;
    private int frameIndex;
    Webcam webcam = null;
    
    public void setupWebCam(Webcam webcam) {
    	this.webcam = webcam;
    	webcam.open(true);
    }

	@Override
	public MediaSourceState getMediaSourceState() {
		// TODO Auto-generated method stub
		return mediaSourceState;
	}

	@Override
	public MediaSourceConfiguration getConfiguration() {
		// TODO Auto-generated method stub
		return cameraMediaSourceConfiguration;
	}

	@Override
	public void initialize(MediaSourceSink mediaSourceSink) throws KinesisVideoException {
		// TODO Auto-generated method stub
		this.mediaSourceSink = mediaSourceSink;
	}

	@Override
	public void configure(MediaSourceConfiguration configuration) {
		// TODO Auto-generated method stub
		
		if (!(configuration instanceof CameraMediaSourceConfiguration)) {
            throw new IllegalStateException("Configuration must be an instance of OpenCvMediaSourceConfiguration");
        }
        this.cameraMediaSourceConfiguration = (CameraMediaSourceConfiguration) configuration;
        this.frameIndex = 0;
		
	}

	@Override
	public void start() throws KinesisVideoException {
		// TODO Auto-generated method stub
		mediaSourceState = MediaSourceState.RUNNING;
		cameraFrameSource = new CameraFrameSource(cameraMediaSourceConfiguration, webcam);
		cameraFrameSource.onBytesAvailable(createKinesisVideoFrameAndPushToProducer());
		cameraFrameSource.start();
		
	}

	private OnFrameDataAvailable createKinesisVideoFrameAndPushToProducer() {
		// TODO Auto-generated method stub
        return new OnFrameDataAvailable() {
            @Override
            public void onFrameDataAvailable(final ByteBuffer data) {
                final long currentTimeMs = System.currentTimeMillis();

                final int flags = FRAME_FLAG_NONE;

                final KinesisVideoFrame frame = new KinesisVideoFrame(
                        frameIndex++,
                        flags,
                        currentTimeMs * HUNDREDS_OF_NANOS_IN_MS,
                        currentTimeMs * HUNDREDS_OF_NANOS_IN_MS,
                        FRAME_DURATION_20_MS * HUNDREDS_OF_NANOS_IN_MS,
                        data);

                if (frame.getSize() == 0) {
                    return;
                }

                putFrame(frame);
            }
        };
	}
	
    private void putFrame(final KinesisVideoFrame kinesisVideoFrame) {
        try {
            mediaSourceSink.onFrame(kinesisVideoFrame);
        } catch (final KinesisVideoException ex) {
            throw new RuntimeException(ex);
        }
    }

	@Override
	public void stop() throws KinesisVideoException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean isStopped() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void free() throws KinesisVideoException {
		// TODO Auto-generated method stub
		
	}

}
