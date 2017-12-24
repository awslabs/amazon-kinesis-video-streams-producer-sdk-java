package com.amazonaws.kinesisvideo.java.mediasource.camera;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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
import com.github.sarxos.webcam.WebcamResolution;
import com.github.sarxos.webcam.WebcamUtils;
import com.github.sarxos.webcam.util.ImageUtils;
import com.xuggle.xuggler.IStreamCoder;

import io.netty.buffer.ByteBuf;

import com.xuggle.mediatool.IMediaWriter;

import com.xuggle.mediatool.ToolFactory;

import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IPixelFormat;
import com.xuggle.xuggler.IStreamCoder.Direction;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;

public class CameraFrameSource {
	
		protected final IStreamCoder iStreamCoder = IStreamCoder.make(Direction.ENCODING, ICodec.ID.CODEC_ID_H264);
		protected final IPacket iPacket = IPacket.make();
		protected final ChannelGroup channelGroup = new DefaultChannelGroup();
	   	public static final int DISCRETENESS_HZ = 25;
	    private final ExecutorService executor = Executors.newFixedThreadPool(1);
	    private final DiscreteTimePeriodsThrottler throttler;
	    private final CameraMediaSourceConfiguration configuration;
	    private OnFrameDataAvailable onFrameDataAvailable;
	    private boolean isRunning = false;
	    protected long startTime ;
        Webcam webcam = null;
        H264StreamEncoder h264Encoder = null;
        protected final Dimension dimension = null;
	
	public CameraFrameSource(final CameraMediaSourceConfiguration configuration, Webcam webcam) {
        this.configuration = configuration;
        this.throttler = new DiscreteTimePeriodsThrottler(configuration.getFrameRate(), DISCRETENESS_HZ);
        this.webcam = webcam;
        this.h264Encoder = new H264StreamEncoder(webcam.getViewSize(), false);

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
                try {
					generateFrameAndNotifyListener();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
            }
        });
    }

    private void generateFrameAndNotifyListener() throws IOException {
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
    
    private ByteBuffer createKinesisVideoFrameFromCamera(final long index) throws IOException {

    	BufferedImage image = webcam.getImage();
    	ChannelBuffer channelBuffer;
		try {
			Object msg = h264Encoder.encode(image);
			if (msg != null) {
				channelGroup.write(msg);
				channelBuffer = (ChannelBuffer)msg;
				return channelBuffer.toByteBuffer();
			}
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
    	return null;

    }
    
    
	
    private void stopFrameGenerator() {

    }
}
