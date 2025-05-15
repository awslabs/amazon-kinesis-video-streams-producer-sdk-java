package com.amazonaws.kinesisvideo.client.stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import com.amazonaws.kinesisvideo.common.function.Consumer;

public class StreamConsumer implements Consumer<InputStream> {
    private static final String END_OF_CHUNKED_DATA = "0";
    private final String apiName;

    @Override
    public void accept(final InputStream inputStream) {
        try {
            final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, UTF_8));
            String line = null;
            while ((line = reader.readLine()) != null) {
                System.out.println(apiName + " --> " + line);
                if (END_OF_CHUNKED_DATA.equals(line)) {
                    System.out.println("Received EOF for Http chunked encoding data");
                    break;
                }
            }
        } catch (final Throwable e) {
            System.out.println("Exception while reading outputstream" + e.getMessage());
            throw new RuntimeException("Exception while reading outputstream", e);
        }
    }

    public StreamConsumer(final String apiName) {
        this.apiName = apiName;
    }
}
