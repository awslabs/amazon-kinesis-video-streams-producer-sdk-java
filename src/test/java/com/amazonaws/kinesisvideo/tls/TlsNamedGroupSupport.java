package com.amazonaws.kinesisvideo.tls;

import org.junit.Assume;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Runtime capability checks for TLS named-group control (SSLParameters.setNamedGroups,
 * JDK 20+) and hybrid post-quantum key exchange (X25519MLKEM768, JDK 27+ via JEP 527).
 *
 * Capabilities are probed at runtime -- reflection for the API, a JDK-only loopback
 * handshake for ML-KEM -- never inferred from a JDK version list, so tests stay correct
 * on any future JDK or vendor backport. The SDK compiles to Java 8 bytecode, hence the
 * reflective access to setNamedGroups.
 */
public final class TlsNamedGroupSupport {

    /** Hybrid post-quantum TLS group (IANA name, as JSSE spells it). */
    public static final String HYBRID_MLKEM_GROUP = "X25519MLKEM768";
    /** Classical-only TLS group used to pin referees and control clients. */
    public static final String CLASSICAL_X25519_GROUP = "x25519";
    /** A second classical group, disjoint from x25519, for referee constraint controls. */
    public static final String CLASSICAL_SECP256R1_GROUP = "secp256r1";

    /**
     * System property that turns "this JVM cannot do ML-KEM" from a test skip into a
     * test failure. Set it where ML-KEM is a requirement rather than a capability to
     * probe (the nightly PQC drift check on JDK 27), so a JVM whose ML-KEM support is
     * missing or disabled (e.g. via jdk.tls.disabledAlgorithms) fails the offline
     * referee step instead of passing it with skips.
     */
    public static final String REQUIRE_ML_KEM_PROPERTY = "kvs.tls.requireMlKem";

    private static final long CAPABILITY_HANDSHAKE_TIMEOUT_SECONDS = 30;

    private static volatile Boolean mlKemKeyExchangeSupported;

    private TlsNamedGroupSupport() {
    }

    /** Whether this JVM has SSLParameters.setNamedGroups (JDK 20+). */
    public static boolean isNamedGroupsApiAvailable() {
        return findSetNamedGroups() != null;
    }

    /**
     * Restricts the given parameters to exactly the given named groups.
     *
     * @throws IllegalStateException if this JVM has no setNamedGroups API.
     */
    public static void pinNamedGroups(final SSLParameters parameters, final String... groups) {
        final Method setNamedGroups = findSetNamedGroups();
        if (setNamedGroups == null) {
            throw new IllegalStateException(
                    "SSLParameters.setNamedGroups is not available on this JVM; "
                            + "guard callers with isNamedGroupsApiAvailable()");
        }
        try {
            setNamedGroups.invoke(parameters, (Object) groups);
        } catch (final IllegalAccessException e) {
            throw new IllegalStateException("Unable to invoke SSLParameters.setNamedGroups", e);
        } catch (final InvocationTargetException e) {
            throw new IllegalStateException("SSLParameters.setNamedGroups rejected "
                    + String.join(":", groups), e.getCause());
        }
    }

    /**
     * Whether this JVM can complete a TLS handshake using only X25519MLKEM768,
     * determined once per JVM by handshaking a JDK-only loopback client/server pair
     * with both sides pinned to that group. Deliberately uses no SDK code, so a
     * capable JVM with a broken SDK path fails the SDK assertions instead of
     * skipping them.
     */
    public static synchronized boolean isMlKemKeyExchangeSupported() {
        if (mlKemKeyExchangeSupported == null) {
            mlKemKeyExchangeSupported = probeMlKemKeyExchange();
        }
        return mlKemKeyExchangeSupported;
    }

    /**
     * Skips the calling test when this JVM cannot do ML-KEM key exchange, or fails it
     * instead when {@link #REQUIRE_ML_KEM_PROPERTY} is set to true.
     */
    public static void assumeMlKemKeyExchangeSupported(final String skipReason) {
        final boolean supported = isMlKemKeyExchangeSupported();
        if (!supported && Boolean.getBoolean(REQUIRE_ML_KEM_PROPERTY)) {
            throw new AssertionError(REQUIRE_ML_KEM_PROPERTY + "=true, but this JVM ("
                    + System.getProperty("java.vendor") + " " + System.getProperty("java.version")
                    + ") cannot complete an X25519MLKEM768 handshake: ML-KEM is unavailable or "
                    + "disabled (check jdk.tls.disabledAlgorithms and jdk.tls.namedGroups)");
        }
        Assume.assumeTrue(skipReason, supported);
    }

    private static boolean probeMlKemKeyExchange() {
        if (!isNamedGroupsApiAvailable()) {
            return false;
        }
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            final SSLContext serverContext = SSLContext.getInstance("TLS");
            final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(TlsRefereeServer.loadKeyStore(), TlsRefereeServer.KEYSTORE_PASSWORD.toCharArray());
            serverContext.init(kmf.getKeyManagers(), null, null);

            final SSLContext clientContext = SSLContext.getInstance("TLS");
            clientContext.init(null, TlsRefereeServer.refereeTrustManagers().getTrustManagers(), null);

            final SSLServerSocket serverSocket = (SSLServerSocket) serverContext.getServerSocketFactory()
                    .createServerSocket(0, 1, InetAddress.getByName("localhost"));
            try {
                final SSLParameters serverParameters = serverSocket.getSSLParameters();
                pinNamedGroups(serverParameters, HYBRID_MLKEM_GROUP);
                serverSocket.setSSLParameters(serverParameters);

                final Future<Boolean> serverHandshake = executor.submit(new java.util.concurrent.Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        final SSLSocket connection = (SSLSocket) serverSocket.accept();
                        try {
                            connection.setSoTimeout((int) TimeUnit.SECONDS.toMillis(CAPABILITY_HANDSHAKE_TIMEOUT_SECONDS));
                            connection.startHandshake();
                            return Boolean.TRUE;
                        } finally {
                            connection.close();
                        }
                    }
                });

                final SSLSocket clientSocket = (SSLSocket) clientContext.getSocketFactory()
                        .createSocket(InetAddress.getByName("localhost"), serverSocket.getLocalPort());
                try {
                    clientSocket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(CAPABILITY_HANDSHAKE_TIMEOUT_SECONDS));
                    final SSLParameters clientParameters = clientSocket.getSSLParameters();
                    pinNamedGroups(clientParameters, HYBRID_MLKEM_GROUP);
                    clientSocket.setSSLParameters(clientParameters);
                    clientSocket.startHandshake();
                } finally {
                    clientSocket.close();
                }
                return serverHandshake.get(CAPABILITY_HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } finally {
                serverSocket.close();
            }
        } catch (final Exception e) {
            // Any failure (group rejected, no shared group, API quirk) means the JVM
            // cannot do ML-KEM key exchange; tests then assert the classical path only.
            return false;
        } finally {
            executor.shutdownNow();
        }
    }

    private static Method findSetNamedGroups() {
        try {
            return SSLParameters.class.getMethod("setNamedGroups", String[].class);
        } catch (final NoSuchMethodException e) {
            return null;
        }
    }
}
