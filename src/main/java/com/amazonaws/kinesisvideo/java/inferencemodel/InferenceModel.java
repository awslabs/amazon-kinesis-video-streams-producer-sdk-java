package com.amazonaws.kinesisvideo.java.inferencemodel;

import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.engine.Engine;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import ai.djl.translate.TranslateException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;

public class InferenceModel {
    private Predictor<Image, DetectedObjects> predictor;

    public InferenceModel() {
        String backbone;
        if ("TensorFlow".equals(Engine.getDefaultEngineName())) {
            backbone = "mobilenet_v2";
        } else {
            backbone = "resnet50";
        }

        Criteria<Image, DetectedObjects> criteria =
                Criteria.builder()
                        .optApplication(Application.CV.OBJECT_DETECTION)
                        .setTypes(Image.class, DetectedObjects.class)
                        .optFilter("backbone", backbone)
                        .optEngine(Engine.getDefaultEngineName())
                        .optProgress(new ProgressBar())
                        .build();

        try {
            ZooModel<Image, DetectedObjects> model = criteria.loadModel();
            this.predictor = model.newPredictor();
        } catch (ModelNotFoundException | MalformedModelException | IOException e) {
            System.out.println(e);
        }

    }
    public HashMap<String, Double> performObjectDetection(String path) {

        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(path);
        grabber.setOption("analyzeduration", "1000000"); // Set a higher value (in microseconds)
        grabber.setOption("probesize", "5000000");      // Set a higher value (in bytes)

        HashMap<String, Double> labels = new HashMap<>();
        Frame frame;

        try {
            grabber.start();
            frame = grabber.grabFrame();

            Java2DFrameConverter converter = new Java2DFrameConverter();
            BufferedImage image = converter.getBufferedImage(frame);

            if (image != null) {
                Image img = ImageFactory.getInstance().fromImage(image);
                DetectedObjects result = predictor.predict(img);

                for (Classifications.Classification detectedObject: result.items()) {
                    labels.put(detectedObject.getClassName(), detectedObject.getProbability());
                }
            }
            grabber.stop();
            grabber.release();
        } catch (FrameGrabber.Exception ignored) {

        } catch (TranslateException e) {
            System.out.println(e);
        }
        return labels;
    }

}