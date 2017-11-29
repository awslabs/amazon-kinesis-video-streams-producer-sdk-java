package com.amazonaws.kinesisvideo.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;

public interface HttpClient {
    HttpMethodName getMethod();

    URI getUri();

    Map<String, String> getHeaders();
    
    InputStream getContent();

    void close() throws IOException;
}
