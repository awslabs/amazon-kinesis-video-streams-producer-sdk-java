package com.amazonaws.kinesisvideo.client;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;

import com.amazonaws.kinesisvideo.client.stream.PutMediaManager;
import com.amazonaws.kinesisvideo.client.stream.StreamingReadManager;
import com.amazonaws.kinesisvideo.config.ClientConfiguration;
import com.google.inject.Guice;
import com.google.inject.Injector;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(value = "OBL_UNSATISFIED_OBLIGATION_EXCEPTION_EDGE")
public final class ClientDemoApp {
    private static final String SERVICE_NAME = "kinesisvideo";
    private static final String PUT_MEDIA = "putMedia";
    private static final String GET_INLET_MEDIA = "getInletMedia";
    private static final String GET_MEDIA = "getMedia";
    private static final String GET_MP4_FRAGMENT = "getMP4Fragment";
    private static final String GET_MEDIA_FOR_FRAGMENT_LIST = "getMediaForFragmentList";
    private static final int ARGS_SIZE = 5;
    private static Injector sInjector;

    private ClientDemoApp() { }

    public static void main(final String[] args) throws Exception {
        if (args.length < ARGS_SIZE) {
            System.out.println("Usage: \n  ClientDemoApp <apiName> <apiUrl> <streamName> <region> <material set>");
            return;
        }
        Thread.sleep(3000);
        final ClientConfiguration configuration = ClientConfiguration.builder()
                .apiName(args[0])
                .streamUri(new URI(args[1]))
                .streamName(args[2])
                .region(args[3])
                .materialSet(args[4])
                .serviceName(SERVICE_NAME)
                .readTimeoutInMillis(1000000)
                .build();

        System.out.println("Configuration : " + configuration);
        sInjector = Guice.createInjector(new AWSKinesisVideoClientLibModule(configuration));

        switch (configuration.getApiName()) {
            case PUT_MEDIA:
                final PutMediaManager putMediaManager = sInjector.getInstance(PutMediaManager.class);
                putMediaManager.sendTestMkvStream(configuration);
                break;
            case GET_MEDIA:
                //TODO : Take input from arguments
                final String inputInJson = "{\"StreamName\":\""  + configuration.getStreamName() + "\","
                        + "\"StartSelector\":{\"StartSelectorType\":\"NOW\"}}";
//                        + "\"StartSelector\":{\"StartSelectorType\":\"SERVER_TIMESTAMP\", "
//                        + "\"StartTimestamp\": 1505861291.943}}";

                executeStreamingRead(configuration, inputInJson, GET_MEDIA, "mkv");
                break;
            case GET_INLET_MEDIA:
                //TODO : Take input from arguments
                final String getInletInput = "{\"StreamId\":\""  + configuration.getStreamName() + "\","
                        + "\"StreamBufferId\":\"c22f9dbb-7348-41ee-80f7-b6e80c54d3f6\", "
                        + "\"StartFragmentSubSequenceNumber\":1113586327}";
                executeStreamingRead(configuration, getInletInput, GET_INLET_MEDIA, "mkv");
                break;
            case GET_MP4_FRAGMENT:
                //TODO : Take input from arguments
                final String getMP4MediaInput = "{\"StreamName\":\"" + configuration.getStreamName() + "\","
                        + "\"SelectorType\":\"FRAGMENT_NUMBER\", "
                        + "\"FragmentNumber\":\"91343852333189629764227161907213523458943640442\"}";
                executeStreamingRead(configuration, getMP4MediaInput, GET_MP4_FRAGMENT, "mp4");
                break;
            case GET_MEDIA_FOR_FRAGMENT_LIST:
                //TODO : Take input from arguments
                final String getMediaForFragmentListInput = "{\"StreamName\":\"" + configuration.getStreamName()
                    + "\"," + " \"Fragments\":"
                    + "  [\"91343852334337924628107459385246420550508930348\"]"
                    + "}";

                executeStreamingRead(configuration, getMediaForFragmentListInput, GET_MEDIA_FOR_FRAGMENT_LIST, "mkv");
                break;
            default:
                System.out.println("Invalid API. check the api name provided !");
                break;
        }
    }

    private static void executeStreamingRead(final ClientConfiguration configuration, final String inputInJson,
            final String apiName, final String fileExtention) throws IOException {
        final StreamingReadManager getMediaManager = sInjector.getInstance(StreamingReadManager.class);
        System.out.println("Requesting data at " + System.currentTimeMillis());
        final CloseableHttpResponse response = getMediaManager.receiveStreamData(configuration, inputInJson);
        System.out.println(String.format("Status of %s call %s at %s", apiName, response.getStatusLine(),
            System.currentTimeMillis()));
        for (final Header header : response.getAllHeaders()) {
            System.out.println(header);
        }
        if (response.getStatusLine().getStatusCode() != 200) {
            IOUtils.copy(response.getEntity().getContent(), System.out);
        } else {
            final InputStream inputStream = response.getEntity().getContent();
            final String outputFileName = String.format("/tmp/output.%s", fileExtention);
            final OutputStream outputStream = new FileOutputStream(outputFileName);
            IOUtils.copy(inputStream, outputStream);
            inputStream.close();
            outputStream.close();
            System.out.println(String.format("Output File %s written successfully !", outputFileName));
        }
    }
}
