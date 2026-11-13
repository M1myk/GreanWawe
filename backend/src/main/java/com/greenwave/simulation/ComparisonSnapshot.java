package com.greenwave.simulation;

import com.greenwave.trip.api.dto.TripConfigRequest;
import java.util.List;

record ComparisonSnapshot(String id, long revision, Status status, double simulationTimeSeconds, double playbackRate,
                          TripConfigRequest config, String networkVersion, String scenarioVersion, String engineVersion,
                          String createdAt, String finishedAt, Failure failure, List<BaselineSimulation.Snapshot> runs,
                          Double timeSavedSeconds, Double timeSavedPercent, String savedTimeExample,
                          SavedTimeExamples.Source savedTimeExampleSource) {
    enum Status { READY, RUNNING, PAUSED, COMPLETED, FAILED }
    record Failure(String code, String message) {}

    boolean terminal() { return status == Status.COMPLETED || status == Status.FAILED; }

    ComparisonSnapshot withSavedTimeExample(SavedTimeExamples.Selection example) {
        return new ComparisonSnapshot(id, revision, status, simulationTimeSeconds, playbackRate,
                config, networkVersion, scenarioVersion, engineVersion, createdAt, finishedAt, failure,
                runs, timeSavedSeconds, timeSavedPercent, example == null ? null : example.text(),
                example == null ? null : example.source());
    }

    ComparisonSnapshot interrupted(String at) {
        return new ComparisonSnapshot(id, revision + 1, Status.FAILED, simulationTimeSeconds, playbackRate,
                config, networkVersion, scenarioVersion, engineVersion, createdAt, at,
                new Failure("BACKEND_RESTARTED", "The backend restarted before the comparison finished."), runs, null, null, null, null);
    }
}
