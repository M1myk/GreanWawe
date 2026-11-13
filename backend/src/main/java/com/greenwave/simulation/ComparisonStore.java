package com.greenwave.simulation;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Repository
class ComparisonStore {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final SavedTimeExamples examples;

    ComparisonStore(JdbcTemplate jdbc, JsonMapper json, SavedTimeExamples examples) {
        this.jdbc = jdbc;
        this.json = json;
        this.examples = examples;
    }

    @PostConstruct
    void recoverInterrupted() {
        jdbc.update("UPDATE comparison SET status='FAILED', interrupted_at=? WHERE status='READY'", Instant.now().toString());
    }

    void save(ComparisonSnapshot snapshot) {
        jdbc.update("""
                INSERT INTO comparison (id, scenario_id, status, snapshot_json) VALUES (?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET status=excluded.status, snapshot_json=excluded.snapshot_json, interrupted_at=NULL
                """, snapshot.id(), snapshot.config().scenarioId(), snapshot.status().name(), json.writeValueAsString(snapshot));
    }

    @Transactional
    SavedTimeExamples.Selection saveCompleted(ComparisonSnapshot snapshot, SavedTimeExamples.Draft draft) {
        var example = examples.selectAndRecord(snapshot.timeSavedSeconds(), draft);
        save(snapshot.withSavedTimeExample(example));
        return example;
    }

    Optional<ComparisonSnapshot> find(String id) {
        return jdbc.query("SELECT snapshot_json, interrupted_at FROM comparison WHERE id=?", (row, index) -> {
            var snapshot = json.readValue(row.getString(1), ComparisonSnapshot.class);
            String interruptedAt = row.getString(2);
            return interruptedAt == null ? snapshot : snapshot.interrupted(interruptedAt);
        }, id).stream().findFirst();
    }

    void discardUnfinished(String id) {
        jdbc.update("DELETE FROM comparison WHERE id=? AND status='READY'", id);
    }
}
