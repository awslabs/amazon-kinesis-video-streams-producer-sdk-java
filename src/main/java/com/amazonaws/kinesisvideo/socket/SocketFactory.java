package com.amazonaws.kinesisvideo.socket;

import com.amazonaws.kinesisvideo.client.IPVersionFilter;
import com.amazonaws.kinesisvideo.http.HostnameVerifyingX509ExtendedTrustManager;
import com.amazonaws.kinesisvideo.http.KvsFilteredDnsResolver;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509ExtendedTrustManager;

import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.security.SecureRandom;

/**
 * A factory class for creating TCP and SSL sockets based on a given URI.
 * It supports both HTTP and HTTPS connections and allows filtering of IP versions.
 */
public class SocketFactory {
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;
    private static final KeyManager[] NO_KEY_MANAGERS = null;

    /**
     * Creates a socket for the given URI.
     *
     * @param uri The URI to connect to.
     * @return A new {@link Socket} connected to the specified URI.
     * @throws RuntimeException If an error occurs while creating the socket.
     */
    public Socket createSocket(final URI uri) {
        try {
            return openSocket(uri);
        } catch (final Throwable e) {
            throw new RuntimeException("Exception while creating socket ! ", e);
        }
    }

    /**
     * Creates a socket for the given URI with an IP version filter.
     *
     * @param uri             The URI to connect to.
     * @param ipVersionFilter The filter to use for resolving the host IP.
     * @return A new {@link Socket} connected to the specified URI.
     * @throws RuntimeException If an error occurs while creating the socket.
     */
    public Socket createSocket(final URI uri, final IPVersionFilter ipVersionFilter) {
        try {
            final InetAddress address = toInetAddr(uri, ipVersionFilter);

            return openSocket(uri, address);
        } catch (final Throwable e) {
            throw new RuntimeException("Exception while creating socket !! ", e);
        }
    }

    /**
     * Opens a socket to the specified URI.
     *
     * @param uri The URI to connect to.
     * @return A connected {@link Socket}.
     * @throws Exception If an error occurs while opening the socket.
     */
    private Socket openSocket(final URI uri) throws Exception {
        final InetAddress address = toInetAddr(uri);

        return openSocket(uri, address);
    }

    /**
     * Opens a socket to the specified URI and resolved address.
     *
     * @param uri     The URI to connect to.
     * @param address The resolved {@link InetAddress} of the host.
     * @return A connected {@link Socket}.
     * @throws Exception If an error occurs while opening the socket.
     */
    private Socket openSocket(final URI uri, final InetAddress address) throws Exception {
        final int port = getPort(uri);

        return isHttps(uri)
                ? createSslSocket(address, port)
                : new Socket(address, port);
    }

    /**
     * Creates an SSL socket to the given address and port.
     *
     * @param address The target {@link InetAddress}.
     * @param port    The port number.
     * @return A secure {@link Socket} using TLSv1.2.
     * @throws Exception If an error occurs while creating the SSL socket.
     */
    private Socket createSslSocket(final InetAddress address, final int port) throws Exception {
        final SSLContext context = SSLContext.getInstance("TLSv1.2");

        // Skipping IP-based hostname verification to avoid unnecessary debug logs:
        // e.g. "Certificate for <x.x.x.x> doesn't match any of the subject alternative names: [s-1234abcd.kinesisvideo.us-west-2.amazonaws.com]"
        // The server certificate contains the hostname, not the raw IP address.
        // Verifying against the IP is redundant in this case and causes misleading debug output.
        context.init(NO_KEY_MANAGERS, new X509ExtendedTrustManager[]{
                new HostnameVerifyingX509ExtendedTrustManager(true, true)},
                new SecureRandom());
        return context.getSocketFactory().createSocket(address, port);
    }

    /**
     * Checks if the given URI uses HTTPS.
     *
     * @param uri The URI to check.
     * @return {@code true} if the URI scheme is "https", otherwise {@code false}.
     * @throws IllegalArgumentException if the URI scheme isn't "http" or "https".
     */
    private boolean isHttps(final URI uri) {
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Unsupported URI scheme: '" + uri.getScheme() + "' in URI: " + uri);
        }
        return "https".equalsIgnoreCase(uri.getScheme());
    }

    /**
     * Resolves the hostname of the given URI to an {@link InetAddress}.
     *
     * @param uri The URI to resolve.
     * @return The resolved {@link InetAddress}.
     * @throws Exception If resolution fails.
     */
    private InetAddress toInetAddr(final URI uri) throws Exception {
        return InetAddress.getByName(getHost(uri));
    }

    /**
     * Resolves the hostname of the given URI using an {@link IPVersionFilter}.
     *
     * @param uri      The URI to resolve.
     * @param ipFilter The IP version filter.
     * @return The resolved {@link InetAddress}.
     * @throws Exception If resolution fails.
     */
    private InetAddress toInetAddr(final URI uri, final IPVersionFilter ipFilter) throws Exception {
        return new KvsFilteredDnsResolver(ipFilter).resolve(uri.getHost())[0];
    }

    /**
     * Retrieves the host from the given URI.
     *
     * @param uri The URI to extract the host from.
     * @return The host as a {@link String}.
     */
    private String getHost(final URI uri) {
        return uri.getHost();
    }

    /**
     * Determines the appropriate port for the given URI.
     *
     * @param uri The URI to extract the port from.
     * @return The port number. Defaults to {@value DEFAULT_HTTPS_PORT} for HTTPS and {@value DEFAULT_HTTP_PORT} for HTTP if not specified in the URI.
     */
    private int getPort(final URI uri) {
        if (uri.getPort() > 0) {
            return uri.getPort();
        }

        return isHttps(uri)
                ? DEFAULT_HTTPS_PORT
                : DEFAULT_HTTP_PORT;
    }
}
