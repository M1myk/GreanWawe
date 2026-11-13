package com.greenwave.simulation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class GeminiSavedTime {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final JsonMapper json;
    private final String apiKey;
    private final URI endpoint;

    GeminiSavedTime(JsonMapper json, @Value("${GEMINI_API_KEY:}") String apiKey,
                    @Value("${GEMINI_MODEL:gemini-3.5-flash-lite}") String model) {
        this.json = json;
        this.apiKey = apiKey.strip();
        if (!model.matches("[a-zA-Z0-9][a-zA-Z0-9.-]*")) throw new IllegalArgumentException("Invalid Gemini model name.");
        endpoint = URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent");
    }

    boolean enabled() {
        return !apiKey.isBlank();
    }

    CompletableFuture<SavedTimeExamples.Example> generate(int seconds, int minimum, List<String> recentTopics) {
        var prompt = """
                Napisz jedno krótkie zdanie po polsku o tym, co można zrobić w czasie zaoszczędzonym dzięki GreenWave.
                Prognozowany czas: %d sekund. Czynność musi być wykonalna już w %d sekund.
                Tematy użyte ostatnio, które należy pominąć: %s.
                Wybierz jedną codzienną, bezpieczną czynność. Dodaj lekki humor albo zabawny szczegół.
                Użyj maksymalnie 20 słów. Pomiń liczby, nazwę algorytmu, emoji, hashtagi i formatowanie.
                Zwróć JSON z polami topic (krótka, ogólna etykieta czynności) i text (zdanie dla użytkownika).
                """.formatted(seconds, minimum, json.writeValueAsString(recentTopics));
        var stringType = Map.of("type", "string");
        var schema = Map.of("type", "object", "properties", Map.of("topic", stringType, "text", stringType),
                "required", List.of("topic", "text"));
        var payload = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json", "responseJsonSchema", schema,
                        "maxOutputTokens", 256));
        var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5))
                .header("x-goog-api-key", apiKey).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .orTimeout(5, TimeUnit.SECONDS)
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        LoggerFactory.getLogger(getClass()).debug("Gemini returned HTTP {}", response.statusCode());
                        return null;
                    }
                    var candidate = json.readTree(response.body()).path("candidates").path(0);
                    if (!"STOP".equals(candidate.path("finishReason").asText())) return null;
                    var content = new StringBuilder();
                    for (var part : candidate.path("content").path("parts")) {
                        if (!part.path("thought").asBoolean(false)) content.append(part.path("text").asText(""));
                    }
                    var value = json.readTree(content.toString());
                    return SavedTimeExamples.Example.fromModel(value.path("topic").asText(""), value.path("text").asText(""));
                }).exceptionally(error -> null);
    }
}
