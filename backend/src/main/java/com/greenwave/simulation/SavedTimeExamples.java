package com.greenwave.simulation;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class SavedTimeExamples {
    static final int MINIMUM_SECONDS = 30;
    private static final int RECENT_LIMIT = 20;

    record Example(String topic, String text) {
        static Example fromModel(String topic, String text) {
            topic = key(topic);
            text = text.strip();
            if (topic.isBlank() || topic.length() > 48 || topic.split(" ").length > 4
                    || text.length() > 180 || text.split("\\s+").length > 20
                    || !text.matches("[\\p{L} ,–—'-]+[.!?]")) return null;
            return new Example(topic, text);
        }
    }

    enum Source { GEMINI, LOCAL }
    record Selection(String text, Source source) {}
    record Draft(int minimum, CompletableFuture<Example> example) {}
    private record Used(String topic, String sentenceKey) {}

    private final JdbcTemplate jdbc;
    private final GeminiSavedTime gemini;

    SavedTimeExamples(JdbcTemplate jdbc, GeminiSavedTime gemini) {
        this.jdbc = jdbc;
        this.gemini = gemini;
    }

    boolean prefetchEnabled() {
        return gemini.enabled();
    }

    Draft prefetch(int seconds) {
        var minimum = band(seconds);
        if (minimum == null) return null;
        var topics = recent().stream().map(Used::topic).distinct().toList();
        return new Draft(minimum, gemini.generate(seconds, minimum, topics));
    }

    Selection selectAndRecord(Double savedSeconds, Draft draft) {
        if (savedSeconds == null) return null;
        var minimum = band(wholeSeconds(savedSeconds));
        if (minimum == null) return null;

        var recent = recent();
        Example selected = null;
        if (draft != null && draft.minimum() == minimum && !draft.example().isCompletedExceptionally()) {
            var candidate = draft.example().getNow(null);
            if (candidate != null && recent.stream().noneMatch(used ->
                    used.topic().equals(candidate.topic()) || used.sentenceKey().equals(key(candidate.text())))) {
                selected = candidate;
            }
        }
        var source = selected == null ? Source.LOCAL : Source.GEMINI;
        if (selected == null) {
            selected = jdbc.queryForObject("""
                    SELECT example.topic, example.sentence
                    FROM saved_time_example example
                    LEFT JOIN saved_time_example_history history
                        ON history.min_seconds = example.min_seconds AND history.topic = example.topic
                    WHERE example.min_seconds = ?
                    GROUP BY example.topic, example.sentence
                    ORDER BY COALESCE(MAX(history.id), 0), example.topic
                    LIMIT 1
                    """, (row, index) -> new Example(row.getString(1), row.getString(2)), minimum);
        }
        jdbc.update("INSERT INTO saved_time_example_history (min_seconds, topic, sentence_key) VALUES (?, ?, ?)",
                minimum, key(selected.topic()), key(selected.text()));
        return new Selection(selected.text(), source);
    }

    static int wholeSeconds(double seconds) {
        return (int) Math.floor(seconds + 1e-8);
    }

    private Integer band(int seconds) {
        if (seconds < MINIMUM_SECONDS) return null;
        return jdbc.query("""
                SELECT min_seconds FROM saved_time_example
                WHERE min_seconds <= ? AND (max_seconds IS NULL OR max_seconds > ?)
                ORDER BY min_seconds DESC LIMIT 1
                """, (row, index) -> row.getInt(1), seconds, seconds).stream().findFirst().orElse(null);
    }

    private List<Used> recent() {
        return jdbc.query("""
                SELECT topic, sentence_key FROM saved_time_example_history ORDER BY id DESC LIMIT ?
                """, (row, index) -> new Used(row.getString(1), row.getString(2)), RECENT_LIMIT);
    }

    private static String key(String value) {
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('ł', 'l').replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
}
