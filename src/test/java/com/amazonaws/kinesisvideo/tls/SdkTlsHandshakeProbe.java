package com.amazonaws.kinesisvideo.tls;

import com.amazonaws.kinesisvideo.socket.SocketFactory;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import java.net.Socket;
import java.net.URI;

/**
 * TLS handshake probe that drives the SDK's real SocketFactory (one of the three
 * former TLSv1.2-hardcoded sites) against a live endpoint. Needs no credentials:
 * only the handshake is performed, no request is sent.
 *
 * Usage:
 *   SdkTlsHandshakeProbe &lt;https-url&gt; [expected-named-group]
 *
 * Without a group argument the probe reports the negotiated protocol and cipher
 * suite of a plain SDK handshake. With a group argument the probe pins the SDK's
 * socket to offer ONLY that named group (requires SSLParameters.setNamedGroups,
 * JDK 20+): since Java has no public API to read the negotiated group, offering a
 * single group makes the server's acceptance the assertion -- a completed handshake
 * proves the server still accepts that group, a failed one is drift. The nightly
 * pqc-drift workflow pins the group expected of the KVS control plane via a
 * repository variable.
 *
 * Exit codes: 0 handshake completed, 1 handshake failed, 2 usage or capability error.
 */
public final class SdkTlsHandshakeProbe {

    /** Bounds the handshake so a silently-dropping network path cannot hang the probe. */
    private static final int HANDSHAKE_SO_TIMEOUT_MILLIS = 60000;

    private SdkTlsHandshakeProbe() {
    }

    public static void main(final String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: SdkTlsHandshakeProbe <https-url> [expected-named-group]");
            System.exit(2);
        }
        final URI uri = new URI(args[0]);
        final String pinnedGroup = args.length == 2 ? args[1] : null;
        if (pinnedGroup != null && !TlsNamedGroupSupport.isNamedGroupsApiAvailable()) {
            System.err.println("SDK TLS PROBE ERROR: this JVM has no SSLParameters.setNamedGroups; "
                    + "a group-pinned probe needs JDK 20+");
            System.exit(2);
        }

        final Socket socket = new SocketFactory().createSocket(uri); // SDK code under test
        try {
            final SSLSocket sslSocket = (SSLSocket) socket;
            sslSocket.setSoTimeout(HANDSHAKE_SO_TIMEOUT_MILLIS);
            if (pinnedGroup != null) {
                final SSLParameters parameters = sslSocket.getSSLParameters();
                TlsNamedGroupSupport.pinNamedGroups(parameters, pinnedGroup);
                sslSocket.setSSLParameters(parameters);
            }
            try {
                sslSocket.startHandshake();
            } catch (final Exception e) {
                System.out.println("SDK TLS PROBE FAILED: host=" + uri.getHost()
                        + " pinnedGroup=" + (pinnedGroup == null ? "(none)" : pinnedGroup)
                        + " error=" + e);
                System.exit(1);
            }
            System.out.println("SDK TLS PROBE OK: host=" + uri.getHost()
                    + " protocol=" + sslSocket.getSession().getProtocol()
                    + " cipherSuite=" + sslSocket.getSession().getCipherSuite()
                    + " pinnedGroup=" + (pinnedGroup == null ? "(none)" : pinnedGroup));
        } finally {
            socket.close();
        }
    }
}
