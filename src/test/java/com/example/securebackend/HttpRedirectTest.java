package com.example.securebackend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class HttpRedirectTest {

    private static final int HTTPS_PORT = freePort();
    private static final int HTTP_PORT = freePort();

    @DynamicPropertySource
    static void ports(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> HTTPS_PORT);
        registry.add("app.http-redirect.enabled", () -> "true");
        registry.add("app.http-redirect.http-port", () -> HTTP_PORT);
    }

    @Test
    void plainHttpIsRedirectedToHttps() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + HTTP_PORT + "/api/public/health"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(302, response.statusCode());
        String location = response.headers().firstValue("location").orElseThrow();
        assertTrue(location.startsWith("https://localhost:" + HTTPS_PORT + "/api/public/health"), location);
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException("No hay un puerto libre para la prueba de redirección", ex);
        }
    }
}
