package com.amazonaws.kinesisvideo.tls;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * In-process TLS "referee" server for offline TLS assertions without credentials.
 *
 * The referee is constrained to specific enabled protocols and/or named groups, and
 * whether a client can complete a handshake against it IS the assertion: Java has no
 * public API to read the negotiated named group, so a server that only accepts
 * X25519MLKEM768 turns "did the client offer ML-KEM?" into a deterministic
 * handshake-completes/handshake-fails check.
 *
 * The server presents a self-signed certificate for "localhost" generated at runtime
 * with the JDK's keytool (SAN: dns:localhost, ip:127.0.0.1, ip:::1). Tests that drive
 * the SDK's real connection paths (which validate against the JVM default trust store)
 * install that keystore as the default trust store via
 * {@link #installRefereeTrustStore()} and must restore it with
 * {@link #restoreDefaultTrustStore()}.
 *
 * After a completed handshake the referee answers any HTTP request on the connection
 * with a minimal 200 response so the SDK's HTTP clients finish their exchange cleanly.
 */
public final class TlsRefereeServer implements Closeable {

    static final String KEYSTORE_PASSWORD = "referee-localhost";
    private static final int CONNECTION_SO_TIMEOUT_MILLIS = 15000;

    private static volatile File keyStoreFile;

    private final String[] enabledProtocols;
    private final String[] namedGroups;
    private final List<String> completedHandshakeProtocols =
            Collections.synchronizedList(new ArrayList<String>());
    private final CountDownLatch firstHandshakeAttempted = new CountDownLatch(1);

    private SSLServerSocket serverSocket;
    private Thread acceptThread;

    private TlsRefereeServer(final String[] enabledProtocols, final String[] namedGroups) {
        this.enabledProtocols = enabledProtocols;
        this.namedGroups = namedGroups;
    }

    /** A referee with the JVM's default protocols and named groups. */
    public static TlsRefereeServer unconstrained() throws Exception {
        return start(null, null);
    }

    /** A referee that only accepts the given protocol versions (e.g. "TLSv1.3"). */
    public static TlsRefereeServer withEnabledProtocols(final String... protocols) throws Exception {
        return start(protocols, null);
    }

    /**
     * A referee that only accepts the given TLS named groups (e.g. "X25519MLKEM768").
     * Requires the SSLParameters.setNamedGroups API (checked by
     * {@link TlsNamedGroupSupport#isNamedGroupsApiAvailable()}).
     */
    public static TlsRefereeServer withNamedGroups(final String... groups) throws Exception {
        return start(null, groups);
    }

    private static TlsRefereeServer start(final String[] protocols, final String[] groups) throws Exception {
        final TlsRefereeServer referee = new TlsRefereeServer(protocols, groups);
        referee.open();
        return referee;
    }

    private void open() throws Exception {
        final SSLContext context = SSLContext.getInstance("TLS");
        final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(loadKeyStore(), KEYSTORE_PASSWORD.toCharArray());
        context.init(kmf.getKeyManagers(), null, null);

        serverSocket = (SSLServerSocket) context.getServerSocketFactory()
                .createServerSocket(0, 1, InetAddress.getByName("localhost"));
        try {
            if (enabledProtocols != null) {
                serverSocket.setEnabledProtocols(enabledProtocols);
            }
            if (namedGroups != null) {
                final SSLParameters parameters = serverSocket.getSSLParameters();
                TlsNamedGroupSupport.pinNamedGroups(parameters, namedGroups);
                serverSocket.setSSLParameters(parameters);
            }
        } catch (final RuntimeException e) {
            serverSocket.close();
            throw e;
        }

        acceptThread = new Thread(new Runnable() {
            @Override
            public void run() {
                acceptLoop();
            }
        }, "tls-referee-" + getPort());
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public String getUrl() {
        return "https://localhost:" + getPort() + "/";
    }

    /** Protocol versions of handshakes the referee completed, in order. */
    public List<String> getCompletedHandshakeProtocols() {
        return new ArrayList<String>(completedHandshakeProtocols);
    }

    /** Waits until the referee has seen at least one handshake attempt (success or failure). */
    public boolean awaitFirstHandshakeAttempt(final long timeout, final TimeUnit unit) throws InterruptedException {
        return firstHandshakeAttempted.await(timeout, unit);
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
        try {
            // At least the per-connection soTimeout, so a mid-exchange connection
            // can run into its own timeout before the join gives up.
            acceptThread.join(CONNECTION_SO_TIMEOUT_MILLIS + 1000L);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            SSLSocket connection = null;
            try {
                connection = (SSLSocket) serverSocket.accept();
            } catch (final IOException e) {
                break; // server socket closed
            }
            try {
                connection.setSoTimeout(CONNECTION_SO_TIMEOUT_MILLIS);
                try {
                    connection.startHandshake();
                    completedHandshakeProtocols.add(connection.getSession().getProtocol());
                } catch (final IOException e) {
                    continue; // a failed handshake is recorded by its absence
                } finally {
                    firstHandshakeAttempted.countDown();
                }
                serveOneHttpExchange(connection);
            } catch (final IOException ignored) {
                // Post-handshake I/O failures do not affect the handshake record.
            } finally {
                closeQuietly(connection);
            }
        }
    }

    /** Reads one HTTP request (head plus Content-Length body) and answers 200. */
    private void serveOneHttpExchange(final SSLSocket connection) throws IOException {
        final InputStream in = connection.getInputStream();
        final StringBuilder head = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            head.append((char) b);
            if (head.length() >= 4 && head.substring(head.length() - 4).equals("\r\n\r\n")) {
                break;
            }
        }
        if (b == -1 && head.length() == 0) {
            return; // client closed without sending a request; handshake already recorded
        }
        int remainingBody = parseContentLength(head.toString());
        while (remainingBody > 0 && in.read() != -1) {
            remainingBody--;
        }
        final OutputStream out = connection.getOutputStream();
        out.write(("HTTP/1.1 200 OK\r\n"
                + "Content-Length: 2\r\n"
                + "Connection: close\r\n"
                + "\r\n"
                + "ok").getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    /**
     * Client side of {@link #serveOneHttpExchange}: sends a minimal HTTP request over an
     * already-connected socket and returns the response status line. Lets tests of raw
     * SDK sockets prove application data flows over the negotiated session, like the
     * HTTP client tests do.
     */
    public static String exchangeMinimalHttpRequest(final Socket socket) throws IOException {
        socket.setSoTimeout(CONNECTION_SO_TIMEOUT_MILLIS);
        final OutputStream out = socket.getOutputStream();
        out.write(("POST / HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Content-Length: 2\r\n"
                + "\r\n"
                + "{}").getBytes(StandardCharsets.US_ASCII));
        out.flush();
        final InputStream in = socket.getInputStream();
        final StringBuilder statusLine = new StringBuilder();
        int b;
        while ((b = in.read()) != -1 && b != '\n') {
            if (b != '\r') {
                statusLine.append((char) b);
            }
        }
        return statusLine.toString();
    }

    private static int parseContentLength(final String requestHead) {
        for (final String line : requestHead.split("\r\n")) {
            final int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("Content-Length")) {
                try {
                    return Integer.parseInt(line.substring(colon + 1).trim());
                } catch (final NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private static void closeQuietly(final SSLSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (final IOException ignored) {
                // best effort
            }
        }
    }

    // ------------------------------------------------------------------
    // Referee key material
    // ------------------------------------------------------------------

    /** Loads (generating on first use) the referee's self-signed localhost keystore. */
    public static synchronized KeyStore loadKeyStore() throws Exception {
        final File file = getKeyStoreFile();
        final KeyStore keyStore = KeyStore.getInstance("PKCS12");
        final InputStream in = new FileInputStream(file);
        try {
            keyStore.load(in, KEYSTORE_PASSWORD.toCharArray());
        } finally {
            in.close();
        }
        return keyStore;
    }

    /** Trust managers that trust exactly the referee's self-signed certificate. */
    public static TrustManagerFactory refereeTrustManagers() throws Exception {
        final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(loadKeyStore());
        return tmf;
    }

    private static synchronized File getKeyStoreFile() throws Exception {
        if (keyStoreFile == null) {
            keyStoreFile = generateKeyStore();
        }
        return keyStoreFile;
    }

    /**
     * Generates a self-signed EC certificate for localhost with the JDK's own keytool:
     * portable across JDK 11+ and OSes, and avoids both a BouncyCastle test dependency
     * and JDK-internal certificate APIs.
     */
    private static File generateKeyStore() throws Exception {
        final File file = File.createTempFile("tls-referee-", ".p12");
        if (!file.delete()) { // keytool must create the keystore itself
            throw new IOException("Unable to remove placeholder keystore file " + file);
        }
        file.deleteOnExit();
        final File keytool = resolveKeytool();
        final ProcessBuilder processBuilder = new ProcessBuilder(
                keytool.getAbsolutePath(),
                "-genkeypair",
                "-alias", "referee",
                "-keyalg", "EC",
                "-dname", "CN=localhost",
                "-ext", "SAN=dns:localhost,ip:127.0.0.1,ip:0:0:0:0:0:0:0:1",
                "-validity", "2",
                "-storetype", "PKCS12",
                "-keystore", file.getAbsolutePath(),
                "-storepass", KEYSTORE_PASSWORD,
                "-keypass", KEYSTORE_PASSWORD);
        processBuilder.redirectErrorStream(true);
        final Process process = processBuilder.start();
        final StringBuilder output = new StringBuilder();
        final InputStream in = process.getInputStream();
        int b;
        while ((b = in.read()) != -1) {
            output.append((char) b);
        }
        final int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("keytool failed with exit code " + exitCode + ": " + output);
        }
        return file;
    }

    private static File resolveKeytool() {
        final File binDir = new File(System.getProperty("java.home"), "bin");
        final File unixKeytool = new File(binDir, "keytool");
        return unixKeytool.exists() ? unixKeytool : new File(binDir, "keytool.exe");
    }

    // ------------------------------------------------------------------
    // Default trust store install/restore for tests driving real SDK paths
    // ------------------------------------------------------------------

    private static final String[] TRUST_STORE_PROPERTIES = {
            "javax.net.ssl.trustStore",
            "javax.net.ssl.trustStorePassword",
            "javax.net.ssl.trustStoreType",
    };
    private static Map<String, String> savedTrustStoreProperties;

    /**
     * Points the JVM default trust store at the referee keystore so the SDK's real
     * trust managers (built from the default TrustManagerFactory) accept the referee's
     * self-signed certificate. Callers must restore with
     * {@link #restoreDefaultTrustStore()} so later tests see the stock trust store.
     *
     * Install/restore assume surefire's default sequential class execution (one pair
     * of trust-store properties in flight at a time). If class-level parallelism is
     * ever enabled, a second concurrent install fails fast here rather than letting
     * one class's restore clobber another's in-flight trust store.
     */
    public static synchronized void installRefereeTrustStore() throws Exception {
        if (savedTrustStoreProperties != null) {
            throw new IllegalStateException("Referee trust store already installed");
        }
        final Map<String, String> saved = new HashMap<String, String>();
        for (final String property : TRUST_STORE_PROPERTIES) {
            saved.put(property, System.getProperty(property));
        }
        System.setProperty("javax.net.ssl.trustStore", getKeyStoreFile().getAbsolutePath());
        System.setProperty("javax.net.ssl.trustStorePassword", KEYSTORE_PASSWORD);
        System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");
        savedTrustStoreProperties = saved;
    }

    /** Undoes {@link #installRefereeTrustStore()}. */
    public static synchronized void restoreDefaultTrustStore() {
        if (savedTrustStoreProperties == null) {
            return;
        }
        for (final Map.Entry<String, String> entry : savedTrustStoreProperties.entrySet()) {
            if (entry.getValue() == null) {
                System.clearProperty(entry.getKey());
            } else {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        }
        savedTrustStoreProperties = null;
    }
}
