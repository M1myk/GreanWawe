package com.greenwave;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "greenwave.cors.allowed-origins=http://localhost:5173,https://example.test")
class HealthHttpTest {
    @LocalServerPort
    private int port;
    private final HttpClient client = HttpClient.newHttpClient();

    private HttpRequest.Builder request() {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/health"));
    }

    @Test
    void returnsApplicationHealth() throws Exception {
        var response = client.send(request().GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"));
        assertEquals("{\"status\":\"UP\",\"application\":\"greenwave\"}", response.body());
    }

    @Test
    void allowsConfiguredOriginAndPreflight() throws Exception {
        var response = client.send(request().header("Origin", "https://example.test")
                .header("Access-Control-Request-Method", "GET")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("https://example.test", response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertTrue(response.headers().firstValue("Access-Control-Allow-Methods").orElseThrow().contains("GET"));
        assertTrue(response.headers().firstValue("Access-Control-Allow-Credentials").isEmpty());
    }

    @Test
    void rejectsUnapprovedOrigin() throws Exception {
        var response = client.send(request().header("Origin", "https://unapproved.test").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, response.statusCode());
        assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
    }
}
