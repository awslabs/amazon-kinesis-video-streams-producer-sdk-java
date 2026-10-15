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

import javax.net.ssl.SSLSocket;
import java.net.Socket;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Guards the SDK's TLS protocol policy on every supported JDK: all three connection
 * paths the SDK builds its own SSLContext for (SocketFactory for PutMedia, and the
 * sync and async Apache HTTP clients behind the SDK's streaming read and media
 * clients) must enable TLSv1.3. Control plane calls go through the AWS SDK's own HTTP
 * client and are not covered here. These are the three sites that historically
 * hardcoded SSLContext.getInstance("TLSv1.2"); each test fails if any site regresses
 * to a TLS-1.2-only context, because the referee it handshakes with accepts TLSv1.3
 * only. Runs offline against an in-process referee server, no credentials.
 */
public class TlsProtocolPolicyTest {

    private static final String TLS_1_3 = "TLSv1.3";
    private static final String TLS_1_2 = "TLSv1.2";
    private static final int RESPONSE_TIMEOUT_SECONDS = 30;

    @BeforeClass
    public static void trustRefereeCertificate() throws Exception {
        TlsRefereeServer.installRefereeTrustStore();
    }

    @AfterClass
    public static void restoreDefaultTrust() {
        TlsRefereeServer.restoreDefaultTrustStore();
    }

    @Test(timeout = 60000)
    public void socketFactorySocketEnablesTls13AndTls12() throws Exception {
        final TlsRefereeServer referee = TlsRefereeServer.unconstrained();
        try {
            final Socket socket = new SocketFactory().createSocket(new URI(referee.getUrl()));
            try {
                final SSLSocket sslSocket = (SSLSocket) socket;
                assertTrue("SocketFactory must enable TLSv1.3; enabled: "
                                + Arrays.toString(sslSocket.getEnabledProtocols()),
                        Arrays.asList(sslSocket.getEnabledProtocols()).contains(TLS_1_3));
                assertTrue("SocketFactory must keep TLSv1.2 enabled for older endpoints; enabled: "
                                + Arrays.toString(sslSocket.getEnabledProtocols()),
                        Arrays.asList(sslSocket.getEnabledProtocols()).contains(TLS_1_2));
            } finally {
                socket.close();
            }
        } finally {
            referee.close();
        }
    }

    @Test(timeout = 60000)
    public void socketFactoryHandshakesWithTls13OnlyReferee() throws Exception {
        final TlsRefereeServer referee = TlsRefereeServer.withEnabledProtocols(TLS_1_3);
        try {
            final Socket socket = new SocketFactory().createSocket(new URI(referee.getUrl()));
            try {
                final SSLSocket sslSocket = (SSLSocket) socket;
                sslSocket.startHandshake();
                assertEquals(TLS_1_3, sslSocket.getSession().getProtocol());
                assertEquals("HTTP/1.1 200 OK", TlsRefereeServer.exchangeMinimalHttpRequest(sslSocket));
            } finally {
                socket.close();
            }
        } finally {
            referee.close();
        }
    }

    @Test(timeout = 60000)
    public void apacheHttpClientHandshakesWithTls13OnlyReferee() throws Exception {
        final TlsRefereeServer referee = TlsRefereeServer.withEnabledProtocols(TLS_1_3);
        try {
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
            assertEquals("Referee accepts TLSv1.3 only, so the client must have negotiated it",
                    Arrays.asList(TLS_1_3), referee.getCompletedHandshakeProtocols());
        } finally {
            referee.close();
        }
    }

    @Test(timeout = 60000)
    public void apacheHttpAsyncClientHandshakesWithTls13OnlyReferee() throws Exception {
        final TlsRefereeServer referee = TlsRefereeServer.withEnabledProtocols(TLS_1_3);
        try {
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
            assertEquals("Referee accepts TLSv1.3 only, so the client must have negotiated it",
                    Arrays.asList(TLS_1_3), referee.getCompletedHandshakeProtocols());
        } finally {
            referee.close();
        }
    }
}
