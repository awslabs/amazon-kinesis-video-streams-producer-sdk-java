package com.amazonaws.kinesisvideo.socket;

import com.amazonaws.kinesisvideo.http.HostnameVerifyingX509ExtendedTrustManager;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509ExtendedTrustManager;

import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.security.SecureRandom;

public class SocketFactory {
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;
    private static final KeyManager[] NO_KEY_MANAGERS = null;

    public Socket createSocket(final URI uri) {
        try {
            return openSocket(uri);
        } catch (final Throwable e) {
            throw new RuntimeException("Exception while creating socket ! ", e);
        }
    }

    private Socket openSocket(final URI uri) throws Exception {
        final InetAddress address = toInetAddr(uri);
        final int port = getPort(uri);

        return isHttps(uri)
                ? createSslSocket(address, port)
                : new Socket(address, port);
    }

    private Socket createSslSocket(final InetAddress address, final int port) throws Exception {
        final SSLContext context = SSLContext.getInstance("TLSv1.2");
        context.init(NO_KEY_MANAGERS, new X509ExtendedTrustManager[] {
                new HostnameVerifyingX509ExtendedTrustManager(true)}, new SecureRandom());
        return context.getSocketFactory().createSocket(address, port);
    }

    private boolean isHttps(final URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme());
    }

    private InetAddress toInetAddr(final URI uri) throws Exception {
        return InetAddress.getByName(getHost(uri));
    }

    private String getHost(final URI uri) {
        return uri.getHost();
    }

    private int getPort(final URI uri) {
        if (uri.getPort() > 0) {
            return uri.getPort();
        }

        return isHttps(uri)
                ? DEFAULT_HTTPS_PORT
                : DEFAULT_HTTP_PORT;
    }
}
