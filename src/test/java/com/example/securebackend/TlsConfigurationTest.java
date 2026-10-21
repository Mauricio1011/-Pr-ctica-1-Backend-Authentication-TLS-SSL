package com.example.securebackend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.util.Set;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.core.io.ClassPathResource;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.http-redirect.enabled=false")
class TlsConfigurationTest {

    private static final Set<String> ALLOWED_PROTOCOLS = Set.of("TLSv1.2", "TLSv1.3");

    @LocalServerPort
    private int port;

    @Autowired
    private ServletWebServerApplicationContext webServerContext;

    private HttpClient clientForProtocol(String protocol) throws Exception {
        KeyStore source = KeyStore.getInstance("PKCS12");
        try (InputStream in = new ClassPathResource("keystore/backend-dev.p12").getInputStream()) {
            source.load(in, "changeit".toCharArray());
        }

        KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
        trust.load(null, null);
        trust.setCertificateEntry("backend-dev", source.getCertificate("backend-dev"));

        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(trust);

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, factory.getTrustManagers(), null);

        SSLParameters parameters = new SSLParameters();
        parameters.setProtocols(new String[]{protocol});

        return HttpClient.newBuilder()
                .sslContext(context)
                .sslParameters(parameters)
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TLSv1.2", "TLSv1.3"})
    void serverAcceptsTlsProtocols(String protocol) throws Exception {
        HttpClient client = clientForProtocol(protocol);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://localhost:" + port + "/api/public/health"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"secure\":true"));
        assertEquals(protocol, response.sslSession().orElseThrow().getProtocol());
    }

    @Test
    void connectorEnablesOnlyTls12AndTls13() {
        TomcatWebServer webServer = (TomcatWebServer) webServerContext.getWebServer();
        assertTrue(webServer.getTomcat().getConnector().getSecure());

        AbstractHttp11Protocol<?> protocol =
                (AbstractHttp11Protocol<?>) webServer.getTomcat().getConnector().getProtocolHandler();
        SSLHostConfig sslHostConfig = protocol.findSslHostConfigs()[0];

        for (String enabled : sslHostConfig.getEnabledProtocols()) {
            assertTrue(ALLOWED_PROTOCOLS.contains(enabled), enabled);
        }
        assertTrue(Set.of(sslHostConfig.getEnabledProtocols()).containsAll(ALLOWED_PROTOCOLS));
    }

    @Test
    void cleartextHttpOnTheTlsPortIsRejected() throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 5000);
            socket.setSoTimeout(5000);
            try {
                socket.getOutputStream().write("""
                        GET /api/public/health HTTP/1.1\r
                        Host: localhost\r
                        Connection: close\r
                        \r
                        """.getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
                String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
                assertFalse(response.startsWith("HTTP/1.1 200"), response);
                assertTrue(response.isBlank()
                                || response.startsWith("HTTP/1.1 400")
                                || response.toLowerCase().contains("tls"),
                        response);
            } catch (IOException ex) {
                // El puerto TLS cierra el handshake cuando recibe HTTP plano.
            }
        }
    }
}
