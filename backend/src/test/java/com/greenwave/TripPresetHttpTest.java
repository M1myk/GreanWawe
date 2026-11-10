package com.greenwave;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class TripPresetHttpTest {
    private static final Path DB = database();
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB + "?foreign_keys=on&busy_timeout=5000");
    }
    private static Path database() {
        try { var path = Files.createTempFile("greenwave-crud-", ".db"); path.toFile().deleteOnExit(); return path; }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    @LocalServerPort int port;
    @Autowired DataSource dataSource;
    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    private HttpRequest request(String method, String path, String body) {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path));
        if (body != null) builder.header("Content-Type", "application/json");
        return builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build();
    }
    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        return client.send(request(method, path, body), HttpResponse.BodyHandlers.ofString());
    }
    private String body(String name, String destination, String version) {
        return """
                {"name":"%s","config":{"scenarioId":"synthetic-v1","startNodeId":"n1","destinationNodeId":"%s",
                "randomSeed":42,"vehicle":{"maxSpeedMps":13.89,"accelerationMps2":2,"brakingMps2":3},
                "navigation":{"observationRangeM":500,"decisionIntervalSeconds":1},"maxDurationSeconds":1800}%s}
                """.formatted(name, destination, version);
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> object(String content) { return json.readValue(content, Map.class); }
    private String create() throws Exception {
        var response = send("POST", "/trip-presets", body("Test trip", "n3", ""));
        assertEquals(201, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Location").orElseThrow().startsWith("/api/v1/trip-presets/"));
        return object(response.body()).get("id").toString();
    }

    @Test void simulationLifecycleAndIdempotency() throws Exception {
        var created = send("POST", "/simulations", """
                {"networkId":"synthetic-v1","startNodeId":"n1","destinationNodeId":"n3","randomSeed":42}
                """);
        assertEquals(201, created.statusCode(), created.body());
        String path = "/simulations/" + object(created.body()).get("id");
        assertEquals("/api/v1" + path, created.headers().firstValue("Location").orElseThrow());
        assertEquals("READY", object(created.body()).get("status"));
        try {
            String commands = path + "/commands";
            String start = """
                    {"commandId":"start","type":"START"}
                    """;
            var started = send("POST", commands, start);
            assertEquals(200, started.statusCode(), started.body());
            assertEquals("RUNNING", object(started.body()).get("status"));
            assertEquals(started.body(), send("POST", commands, start).body());
            assertEquals(409, send("POST", commands, start.replace("START", "PAUSE")).statusCode());
            long deadline = System.nanoTime() + java.time.Duration.ofSeconds(4).toNanos();
            while (((Number) object(send("GET", path, null).body()).get("simulationTimeSeconds")).doubleValue() == 0) {
                assertTrue(System.nanoTime() < deadline, "Simulation did not advance");
                Thread.sleep(25);
            }
            var paused = send("POST", commands, start.replace("start", "pause").replace("START", "PAUSE"));
            assertEquals(200, paused.statusCode(), paused.body());
            assertEquals("PAUSED", object(paused.body()).get("status"));
            Thread.sleep(250);
            assertEquals(paused.body(), send("GET", path, null).body());
            assertEquals("RUNNING", object(send("POST", commands,
                    start.replace("start", "resume").replace("START", "RESUME")).body()).get("status"));
            var reset = send("POST", commands, start.replace("start", "reset").replace("START", "RESET"));
            assertEquals(created.body(), reset.body());
        } finally {
            assertEquals(204, send("DELETE", path, null).statusCode());
        }
        assertEquals(404, send("GET", path, null).statusCode());
    }

    @Test void fullCrudAndStaleVersion() throws Exception {
        var response = send("POST", "/trip-presets", body("  Commute  ", "n3", ""));
        assertEquals(201, response.statusCode(), response.body());
        var created = object(response.body());
        var id = created.get("id").toString();
        assertEquals("Commute", created.get("name"));
        assertEquals(1, ((Number)created.get("version")).intValue());
        assertEquals(200, send("GET", "/trip-presets/" + id, null).statusCode());
        var updated = send("PUT", "/trip-presets/" + id, body("Updated", "n2", ",\"version\":1"));
        assertEquals(200, updated.statusCode(), updated.body());
        assertEquals(2, ((Number)object(updated.body()).get("version")).intValue());
        assertEquals(409, send("PUT", "/trip-presets/" + id, body("Stale", "n3", ",\"version\":1")).statusCode());
        assertEquals("Updated", object(send("GET", "/trip-presets/" + id, null).body()).get("name"));
        assertEquals(204, send("DELETE", "/trip-presets/" + id, null).statusCode());
        assertEquals(404, send("GET", "/trip-presets/" + id, null).statusCode());
        assertEquals(404, send("DELETE", "/trip-presets/" + id, null).statusCode());
    }
    @Test void rejectsUnreachableAndIdenticalEndpointsWithoutPartialInsert() throws Exception {
        var jdbc = new JdbcTemplate(dataSource);
        long before = jdbc.queryForObject("SELECT count(*) FROM trip_preset", Long.class);
        for (var destination : List.of("n4", "n1", "missing")) {
            var response = send("POST", "/trip-presets", body("Invalid", destination, ""));
            assertEquals(422, response.statusCode(), response.body());
            assertTrue(response.body().contains("config.destinationNodeId"));
        }
        assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM trip_preset", Long.class));
    }
    @Test void validatesNestedValuesAndMalformedJson() throws Exception {
        var negative = send("POST", "/trip-presets", body("Invalid", "n3", "").replace("13.89", "-1"));
        assertEquals(422, negative.statusCode());
        assertTrue(negative.body().contains("config.vehicle.maxSpeedMps"));
        assertEquals(422, send("POST", "/trip-presets", body(" ", "n3", "")).statusCode());
        assertEquals(422, send("POST", "/trip-presets", body("Invalid", "n3", "").replace("Seconds\":1}", "Seconds\":0.15}")).statusCode());
        assertEquals(400, send("POST", "/trip-presets", "{").statusCode());
        assertEquals(400, send("POST", "/trip-presets", body("Invalid", "n3", "").replace("\"randomSeed\":42", "\"randomSeed\":1.5")).statusCode());
        assertEquals(400, send("POST", "/trip-presets", body("Invalid", "n3", ",\"unexpected\":true")).statusCode());
        assertEquals(422, send("POST", "/trip-presets", "{\"name\":\"Missing config\"}").statusCode());
    }
    @Test void supportsPaginationAndValidatesBounds() throws Exception {
        create(); create();
        var page = send("GET", "/trip-presets?limit=1&offset=1", null);
        assertEquals(200, page.statusCode());
        var data = object(page.body());
        assertEquals(1, ((List<?>)data.get("items")).size());
        assertTrue(((Number)data.get("total")).longValue() >= 2);
        assertEquals(422, send("GET", "/trip-presets?limit=101", null).statusCode());
        assertEquals(422, send("GET", "/trip-presets?offset=-1", null).statusCode());
        assertEquals(400, send("GET", "/trip-presets?limit=no", null).statusCode());
    }
    @Test void concurrentEditsCannotOverwriteEachOther() throws Exception {
        var id = create();
        var a = client.sendAsync(request("PUT", "/trip-presets/" + id, body("A", "n3", ",\"version\":1")), HttpResponse.BodyHandlers.ofString());
        var b = client.sendAsync(request("PUT", "/trip-presets/" + id, body("B", "n2", ",\"version\":1")), HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(a, b).join();
        assertEquals(List.of(200,409), List.of(a.join().statusCode(), b.join().statusCode()).stream().sorted().toList());
    }
    @Test void migratesAndEnforcesForeignKeysAndPersistsToFile() throws Exception {
        var id = create();
        var jdbc = new JdbcTemplate(dataSource);
        assertEquals(1, jdbc.queryForObject("PRAGMA foreign_keys", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='3' AND success=1", Integer.class));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("DELETE FROM scenario WHERE id='synthetic-v1'"));
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + DB);
             var statement = connection.prepareStatement("SELECT name FROM trip_preset WHERE id=?")) {
            statement.setString(1, id);
            try (var result = statement.executeQuery()) { assertTrue(result.next()); assertEquals("Test trip", result.getString(1)); }
        }
    }
    @Test void exposesSyntheticScenarioAndCrudPreflight() throws Exception {
        var scenarios = send("GET", "/scenarios/synthetic-v1", null);
        assertEquals(200, scenarios.statusCode());
        assertEquals(4, ((List<?>)object(scenarios.body()).get("nodes")).size());
        var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/trip-presets"))
                .header("Origin", "http://localhost:5173").header("Access-Control-Request-Method", "PUT")
                .header("Access-Control-Request-Headers", "content-type").method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Access-Control-Allow-Methods").orElseThrow().contains("PUT"));
    }

    @Test void exposesGdanskNetworkWithConsistentGeometry() throws Exception {
        var response = send("GET", "/networks/gdansk-v1", null);
        assertEquals(200, response.statusCode());
        var network = json.readTree(response.body());
        assertTrue(network.get("nodes").size() > 100);
        var nodes = new HashMap<String, tools.jackson.databind.JsonNode>();
        for (var node : network.get("nodes")) nodes.put(node.get("id").asText(), node);
        for (var edge : network.get("edges")) {
            var start = nodes.get(edge.get("fromNodeId").asText());
            var end = nodes.get(edge.get("toNodeId").asText());
            assertNotNull(start);
            assertNotNull(end);
            assertTrue(edge.get("lengthM").asDouble() > 0);
            assertTrue(edge.get("speedLimitMps").asDouble() > 0);
            assertEquals(start.get("lon"), edge.get("geometry").get(0).get(0));
            assertEquals(end.get("lat"), edge.get("geometry").get(1).get(1));
        }
    }

    @Test void listsNetworkVersionsAndReturnsNotFoundForUnknownNetwork() throws Exception {
        var response = send("GET", "/networks", null);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("gdansk-v1"));
        assertTrue(response.body().contains("synthetic-v1"));
        assertEquals(404, send("GET", "/networks/unknown", null).statusCode());
    }

    @Test void previewsRoadRouteAcrossFormerEasternBoundary() throws Exception {
        var response = send("GET", "/networks/gdansk-v3/route?start=301675292&destination=301675209", null);
        assertEquals(200, response.statusCode(), response.body());
        var route = json.readTree(response.body());
        assertTrue(route.get("lengthM").asDouble() > 0);
        var geometry = route.get("geometry");
        assertTrue(geometry.size() > 2);
        var jdbc = new JdbcTemplate(dataSource);
        for (int i = 1; i < geometry.size(); i++) {
            var from = geometry.get(i - 1);
            var to = geometry.get(i);
            assertEquals(1, jdbc.queryForObject("""
                    SELECT EXISTS (
                      SELECT 1 FROM road_edge e JOIN road_node a
                        ON a.network_id=e.network_id AND a.node_id=e.from_node_id
                      JOIN road_node b ON b.network_id=e.network_id AND b.node_id=e.to_node_id
                      WHERE e.network_id='gdansk-v3' AND a.lon=? AND a.lat=? AND b.lon=? AND b.lat=?
                    )
                    """, Integer.class, from.get(0).asDouble(), from.get(1).asDouble(),
                    to.get(0).asDouble(), to.get(1).asDouble()));
        }
        assertTrue(geometry.get(geometry.size() - 1).get(0).asDouble() > 18.635);
    }

    @Test void routePreviewRejectsDisconnectedAndUnknownEndpoints() throws Exception {
        assertEquals(422, send("GET", "/networks/synthetic-v1/route?start=n3&destination=n1", null).statusCode());
        assertEquals(404, send("GET", "/networks/synthetic-v1/route?start=n1&destination=missing", null).statusCode());
    }
}
