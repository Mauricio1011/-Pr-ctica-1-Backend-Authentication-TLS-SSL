package com.example.securebackend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.http-redirect.enabled=false")
class TlsConfigurationTest {

    @LocalServerPort
    private int port;

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
}
