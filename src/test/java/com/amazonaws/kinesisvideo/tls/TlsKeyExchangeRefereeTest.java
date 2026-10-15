package com.amazonaws.kinesisvideo.tls;

import com.amazonaws.kinesisvideo.http.HttpMethodName;
import com.amazonaws.kinesisvideo.http.KinesisVideoApacheHttpAsyncClient;
import com.amazonaws.kinesisvideo.http.KinesisVideoApacheHttpClient;
import com.amazonaws.kinesisvideo.socket.SocketFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.protocol.BasicAsyncResponseConsumer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.amazonaws.kinesisvideo.tls.TlsNamedGroupSupport.CLASSICAL_SECP256R1_GROUP;
import static com.amazonaws.kinesisvideo.tls.TlsNamedGroupSupport.CLASSICAL_X25519_GROUP;
import static com.amazonaws.kinesisvideo.tls.TlsNamedGroupSupport.HYBRID_MLKEM_GROUP;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/**
 * Offline, credential-free proof of the SDK's post-quantum key exchange capability
 * and its classical fallback, on every JDK in the CI matrix.
 *
 * Java has no public API to read the negotiated TLS named group, so the referee's
 * group constraint IS the assertion: a handshake completed against a referee that
 * accepts only X25519MLKEM768 proves the client offered ML-KEM; one completed
 * against a classical-only referee proves fallback. Control tests show the
 * constraint really rejects non-matching clients, so the SDK assertions cannot
 * pass vacuously; one of them needs only the named-groups API, so lanes without
 * ML-KEM verify the constraint too.
 *
 * Capability gating is at runtime, never a JDK version list:
 * - no SSLParameters.setNamedGroups (pre JDK 20): group-pinned referees are
 *   impossible; only the classical/unconstrained path is asserted (JDK 11 lane);
 * - setNamedGroups present but ML-KEM absent: PQC referee tests are skipped and
 *   the classical-only referee fallback tests and the classical constraint
 *   control run (JDK 21 lane);
 * - ML-KEM capable (JDK 27+, JEP 527): everything runs, fallback included
 *   (JDK 27 lane).
 * With -Dkvs.tls.requireMlKem=true (nightly PQC drift check) the ML-KEM tests fail
 * instead of skipping on a JVM without usable ML-KEM.
 */
public class TlsKeyExchangeRefereeTest {

    private static final int RESPONSE_TIMEOUT_SECONDS = 30;

    @BeforeClass
    public static void trustRefereeCertificate() throws Exception {
        TlsRefereeServer.installRefereeTrustStore();
    }

    @AfterClass
    public static void restoreDefaultTrust() {
        TlsRefereeServer.restoreDefaultTrustStore();
    }

    // ------------------------------------------------------------------
    // Referee controls: the group constraint must actually constrain
    // ------------------------------------------------------------------

    @Test(timeout = 60000)
    public void pqcOnlyRefereeRejectsClassicalPinnedClient() throws Exception {
        TlsNamedGroupSupport.assumeMlKemKeyExchangeSupported(
                "JVM cannot do ML-KEM key exchange; PQC referee not constructible");
        final TlsRefereeServer referee = TlsRefereeServer.withNamedGroups(HYBRID_MLKEM_GROUP);
        try {
            final SSLSocket client = refereeTrustingRawSocket(referee.getPort());
            try {
                final SSLParameters parameters = client.getSSLParameters();
                TlsNamedGroupSupport.pinNamedGroups(parameters, CLASSICAL_X25519_GROUP);
                client.setSSLParameters(parameters);
                client.startHandshake();
                fail("Classical-pinned client must not handshake with an ML-KEM-only referee");
            } catch (final IOException expected) {
                // no shared named group: the referee's constraint is enforced
            } finally {
                client.close();
            }
            assertTrue("Referee never saw a handshake attempt",
                    referee.awaitFirstHandshakeAttempt(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertEquals("Referee must not have completed any handshake",
                    0, referee.getCompletedHandshakeProtocols().size());
        } finally {
            referee.close();
        }
    }

    @Test(timeout = 60000)
    public void pqcOnlyRefereeAcceptsPqcPinnedClient() throws Exception {
        TlsNamedGroupSupport.assumeMlKemKeyExchangeSupported(
                "JVM cannot do ML-KEM key exchange; PQC referee not constructible");
        final TlsRefereeServer referee = TlsRefereeServer.withNamedGroups(HYBRID_MLKEM_GROUP);
        try {
            final SSLSocket client = refereeTrustingRawSocket(referee.getPort());
            try {
                final SSLParameters parameters = client.getSSLParameters();
                TlsNamedGroupSupport.pinNamedGroups(parameters, HYBRID_MLKEM_GROUP);
                client.setSSLParameters(parameters);
                client.startHandshake();
                assertEquals("TLSv1.3", client.getSession().getProtocol());
            } finally {
                client.close();
            }
        } finally {
            referee.close();
        }
    }

    @Test(timeout = 60000)
    public void classicalOnlyRefereeRejectsDifferentlyPinnedClient() throws Exception {
        // Gated on the API alone, so this control runs on every lane that runs the
        // classical-fallback tests (e.g. JDK 21, no ML-KEM) and proves there that
        // the referee's group constraint actually constrains.
        assumeTrue("JVM cannot pin named groups (SSLParameters.setNamedGroups absent)",
                TlsNamedGroupSupport.isNamedGroupsApiAvailable());
        final TlsRefereeServer referee = TlsRefereeServer.withNamedGroups(CLASSICAL_X25519_GROUP);
        try {
            final SSLSocket client = refereeTrustingRawSocket(referee.getPort());
            try {
                final SSLParameters parameters = client.getSSLParameters();
                TlsNamedGroupSupport.pinNamedGroups(parameters, CLASSICAL_SECP256R1_GROUP);
                client.setSSLParameters(parameters);
                client.startHandshake();
                fail("A secp256r1-pinned client must not handshake with an x25519-only referee");
            } catch (final IOException expected) {
                // no shared named group: the referee's constraint is enforced
            } finally {
                client.close();
            }
            assertTrue("Referee never saw a handshake attempt",
                    referee.awaitFirstHandshakeAttempt(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertEquals("Referee must not have completed any handshake",
                    0, referee.getCompletedHandshakeProtocols().size());
        } finally {
            referee.close();
        }
    }

    // ------------------------------------------------------------------
    // SDK assertions: the real connection paths against group-pinned referees
    // ------------------------------------------------------------------

    @Test(timeout = 60000)
    public void sdkSocketFactoryOffersMlKemAgainstPqcOnlyReferee() throws Exception {
        TlsNamedGroupSupport.assumeMlKemKeyExchangeSupported(
                "JVM cannot do ML-KEM key exchange; classical fallback tests cover this lane");
        final TlsRefereeServer referee = TlsRefereeServer.withNamedGroups(HYBRID_MLKEM_GROUP);
        try {
            handshakeSdkSocketFactory(referee);
            assertEquals("Referee accepts X25519MLKEM768 only, so the SDK must have offered it",
                    Arrays.asList("TLSv1.3"), referee.getCompletedHandshakeProtocols());
        } finally {
            referee.close();
        }
    }

    @Test(timeout = 60000)
    public void sdkApacheHttpClientOffersMlKemAgainstPqcOnlyReferee() throws Exception {
        TlsNamedGroupSupport.assumeMlKemKeyExchangeSupported(
                "JVM cannot do ML-KEM key exchange; classical fallback tests cover this lane");
        final TlsRefereeServer referee = TlsRefereeServer.withNamedGroups(HYBRID_MLKEM_GROUP);
        try {
            executeSdkHttpClientRequest(referee);
            assertEquals("Referee accepts X25519MLKEM768 only, so the SDK must have offered it",
                    Arrays.asList("TLSv1.3"), referee.getCompletedHandshakeProtocols());
        } finally {
            referee.close();
        }
    }

    @Test(timeout = 60000)
    public void sdkApacheHttpAsyncClientOffersMlKemAgainstPqcOnlyReferee() throws Exception {
        TlsNamedGroupSupport.assumeMlKemKeyExchangeSupported(
                "JVM cannot do ML-KEM key exchange; classical fallback tests cover this lane");
        final TlsRefereeServer referee = TlsRefereeServer.withNamedGroups(HYBRID_MLKEM_GROUP);
        try {
            executeSdkHttpAsyncClientRequest(referee);
            assertEquals("Referee accepts X25519MLKEM768 only, so the SDK must have offered it",
                    Arrays.asList("TLSv1.3"), referee.getCompletedHandshakeProtocols());
        } finally {
            referee.close();
        }
    }

    @Test(timeout = 60000)
    public void sdkSocketFactoryFallsBackAgainstClassicalOnlyReferee() throws Exception {
        assumeTrue("JVM cannot pin referee named groups (SSLParameters.setNamedGroups absent); "
                        + "the unconstrained-referee tests cover the classical path on this lane",
                TlsNamedGroupSupport.isNamedGroupsApiAvailable());
        final TlsRefereeServer referee = TlsRefereeServer.withNamedGroups(CLASSICAL_X25519_GROUP);
        try {
            handshakeSdkSocketFactory(referee);
            assertEquals("The SDK must fall back cleanly to a classical-only server over TLS 1.3",
                    Arrays.asList("TLSv1.3"), referee.getCompletedHandshakeProtocols());
        } finally {
            referee.close();
        }
    }

    @Test(timeout = 60000)
    public void sdkApacheHttpClientFallsBackAgainstClassicalOnlyReferee() throws Exception {
        assumeTrue("JVM cannot pin referee named groups (SSLParameters.setNamedGroups absent); "
                        + "the unconstrained-referee tests cover the classical path on this lane",
                TlsNamedGroupSupport.isNamedGroupsApiAvailable());
        final TlsRefereeServer referee = TlsRefereeServer.withNamedGroups(CLASSICAL_X25519_GROUP);
        try {
            executeSdkHttpClientRequest(referee);
            assertEquals("The SDK must fall back cleanly to a classical-only server over TLS 1.3",
                    Arrays.asList("TLSv1.3"), referee.getCompletedHandshakeProtocols());
        } finally {
            referee.close();
        }
    }

    @Test(timeout = 60000)
    public void sdkApacheHttpAsyncClientFallsBackAgainstClassicalOnlyReferee() throws Exception {
        assumeTrue("JVM cannot pin referee named groups (SSLParameters.setNamedGroups absent); "
                        + "the unconstrained-referee tests cover the classical path on this lane",
                TlsNamedGroupSupport.isNamedGroupsApiAvailable());
        final TlsRefereeServer referee = TlsRefereeServer.withNamedGroups(CLASSICAL_X25519_GROUP);
        try {
            executeSdkHttpAsyncClientRequest(referee);
            assertEquals("The SDK must fall back cleanly to a classical-only server over TLS 1.3",
                    Arrays.asList("TLSv1.3"), referee.getCompletedHandshakeProtocols());
        } finally {
            referee.close();
        }
    }

    // ------------------------------------------------------------------
    // Drivers for the SDK's real connection paths
    // ------------------------------------------------------------------

    private void handshakeSdkSocketFactory(final TlsRefereeServer referee) throws Exception {
        final Socket socket = new SocketFactory().createSocket(new URI(referee.getUrl()));
        try {
            ((SSLSocket) socket).startHandshake();
            // The referee records the handshake before it serves the request, so the
            // response also guarantees the record is in place for the caller's assertion.
            assertEquals("HTTP/1.1 200 OK", TlsRefereeServer.exchangeMinimalHttpRequest(socket));
        } finally {
            socket.close();
        }
    }

    private void executeSdkHttpClientRequest(final TlsRefereeServer referee) throws Exception {
        final KinesisVideoApacheHttpClient client = KinesisVideoApacheHttpClient.builder()
                .withUri(new URI(referee.getUrl()))
                .withMethod(HttpMethodName.POST)
                .withContentType(ContentType.APPLICATION_JSON)
                .withContentInJson("{}")
                .build();
        try {
            final CloseableHttpResponse response = client.executeRequest();
            try {
                assertEquals(200, response.getStatusLine().getStatusCode());
            } finally {
                response.close();
            }
        } finally {
            client.close();
        }
    }

    private void executeSdkHttpAsyncClientRequest(final TlsRefereeServer referee) throws Exception {
        final CountDownLatch responseReceived = new CountDownLatch(1);
        final AtomicReference<HttpResponse> response = new AtomicReference<HttpResponse>();
        final AtomicReference<Exception> failure = new AtomicReference<Exception>();
        final KinesisVideoApacheHttpAsyncClient client = KinesisVideoApacheHttpAsyncClient.builder()
                .withHttpAsyncResponseConsumer(new BasicAsyncResponseConsumer())
                .withFutureCallback(new FutureCallback<HttpResponse>() {
                    @Override
                    public void completed(final HttpResponse result) {
                        response.set(result);
                        responseReceived.countDown();
                    }

                    @Override
                    public void failed(final Exception e) {
                        failure.set(e);
                        responseReceived.countDown();
                    }

                    @Override
                    public void cancelled() {
                        responseReceived.countDown();
                    }
                })
                .withUri(new URI(referee.getUrl()))
                .withMethod(HttpMethodName.POST)
                .withContentType(ContentType.APPLICATION_JSON)
                .withContentInJson("{}")
                .build();
        try {
            client.executeRequest();
            assertTrue("No response from the referee within " + RESPONSE_TIMEOUT_SECONDS + "s",
                    responseReceived.await(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } finally {
            client.close();
        }
        assertNull("Async request failed: " + failure.get(), failure.get());
        assertEquals(200, response.get().getStatusLine().getStatusCode());
    }

    /** A JDK-only client socket trusting the referee certificate; no SDK code involved. */
    private SSLSocket refereeTrustingRawSocket(final int port) throws Exception {
        final SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, TlsRefereeServer.refereeTrustManagers().getTrustManagers(), null);
        final SSLSocket socket = (SSLSocket) context.getSocketFactory()
                .createSocket(InetAddress.getByName("localhost"), port);
        socket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(RESPONSE_TIMEOUT_SECONDS));
        return socket;
    }
}
