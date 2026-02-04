package com.amazonaws.kinesisvideo.socket;

import org.junit.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SocketFactoryTest {

    private static final int httpPort = 8080;
    private static final int httpsPort = 8443;
    private final SocketFactory socketFactory = new SocketFactory();

    @Test
    public void testCreateSocket_Http() throws Exception {
        try (final ServerSocket server = new ServerSocket(httpPort)) {
            final URI uri = new URI("http://localhost:" + httpPort);
            final Socket socket = socketFactory.createSocket(uri);

            assertNotNull(socket);
            assertTrue(socket.isConnected());
            assertEquals("localhost", socket.getInetAddress().getHostName());
            assertEquals(httpPort, socket.getPort());
        }
    }

    @Test
    public void testCreateSocket_Https() throws Exception {
        try (final ServerSocket server = new ServerSocket(httpsPort)) {
            final URI uri = new URI("https://localhost:" + httpsPort);
            final Socket socket = socketFactory.createSocket(uri);

            assertNotNull(socket);
            assertTrue(socket.isConnected());
            assertEquals("localhost", socket.getInetAddress().getHostName());
            assertEquals(httpsPort, socket.getPort());
        }
    }

    @Test
    public void testCreateSocket_InvalidURI() throws URISyntaxException, IOException {
        try (final Socket socket = socketFactory.createSocket(new URI("invalid://localhost"))) {
            fail("It should have thrown an exception");
        } catch (final RuntimeException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testCreateSocket_UnreachablePort() throws URISyntaxException, IOException {
        try (final Socket socket = socketFactory.createSocket(new URI("http://localhost:65535"))) {
            fail("It should have thrown an exception");
        } catch (final RuntimeException e) {
            assertTrue(e.getCause() instanceof ConnectException);
        }
    }

    @Test
    public void testCreateSocket_NonExistentDomain() throws URISyntaxException, IOException {
        try (final Socket socket = socketFactory.createSocket(new URI("http://nonexistent.example.com:80"))) {
            fail("It should have thrown an exception");
        } catch (final RuntimeException e) {
            assertTrue(e.getCause() instanceof UnknownHostException);
        }
    }
}
