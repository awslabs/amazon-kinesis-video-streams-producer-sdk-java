package com.amazonaws.kinesisvideo.java.mediasource.camera;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

import com.amazonaws.kinesisvideo.client.mediasource.CameraMediaSourceConfiguration;
import com.amazonaws.kinesisvideo.mediasource.OnFrameDataAvailable;
import com.amazonaws.kinesisvideo.stream.throttling.DiscreteTimePeriodsThrottler;
import com.github.sarxos.webcam.Webcam;

import io.netty.buffer.ByteBuf;

public class CameraFrameSource {
	   	public static final int DISCRETENESS_HZ = 25;
	    private final ExecutorService executor = Executors.newFixedThreadPool(1);
	    private final DiscreteTimePeriodsThrottler throttler;
	    private final CameraMediaSourceConfiguration configuration;
	    private OnFrameDataAvailable onFrameDataAvailable;
	    private boolean isRunning = false;
        Webcam webcam = null;
	
	public CameraFrameSource(final CameraMediaSourceConfiguration configuration, Webcam webcam) {
        this.configuration = configuration;
        this.throttler = new DiscreteTimePeriodsThrottler(configuration.getFrameRate(), DISCRETENESS_HZ);
        this.webcam = webcam;
    }
	
	public void start() {
        if (isRunning) {
            throw new IllegalStateException("Frame source is already running");
        }

        isRunning = true;
        startFrameGenerator();
    }

    public void stop() {
        isRunning = false;
        stopFrameGenerator();
    }

    public void onBytesAvailable(final OnFrameDataAvailable onFrameDataAvailable) {
        this.onFrameDataAvailable = onFrameDataAvailable;
    }

    private void startFrameGenerator() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                generateFrameAndNotifyListener();
            }
        });
    }

    private void generateFrameAndNotifyListener() {
        int frameCounter = 0;
    	while (isRunning) {
            // TODO: Throttler is not limiting first time call when input param
            // are the same
            throttler.throttle();
			if (onFrameDataAvailable != null) {
				ByteBuffer frameData = createKinesisVideoFrameFromCamera(frameCounter);
				if (frameData != null) {
					onFrameDataAvailable.onFrameDataAvailable(frameData);
					frameCounter++;
				}
            }
            
        }
    }
    
    private ByteBuffer createKinesisVideoFrameFromCamera(final long index) {

    	ByteBuffer frameBytes = webcam.getImageBytes();
        return frameBytes;

    }
    private void stopFrameGenerator() {

    }
    
    
	
}
