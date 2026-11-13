package com.greenwave.simulation;

import com.greenwave.trip.api.dto.TripConfigRequest;
import com.greenwave.trip.domain.TrafficMode;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.datasource.url=jdbc:sqlite:file:saved-time-test?mode=memory&cache=shared")
class SavedTimeExamplesTest {
    @MockitoBean GeminiSavedTime gemini;
    @Autowired SavedTimeExamples examples;
    @Autowired ComparisonService comparisons;

    @Test void prefetchRunsOnceBeforeBaselineArrivesAndResultSurvivesRelease() {
        when(gemini.enabled()).thenReturn(true);
        var request = new CompletableFuture<SavedTimeExamples.Example>();
        when(gemini.generate(anyInt(), anyInt(), anyList())).thenReturn(request);
        var config = new TripConfigRequest("synthetic-v1", "n1", "n3", 42L,
                new TripConfigRequest.Vehicle(13.89, 2.0, 3.0), new TripConfigRequest.Navigation(500.0, 1.0),
                3000.0, TrafficMode.RUSH_HOUR);
        synchronized (comparisons) {
            var snapshot = comparisons.create(config);
            String id = snapshot.id();
            snapshot = comparisons.command(id, new ComparisonController.Command("rate", snapshot.revision(),
                    SimulationController.CommandType.SET_PLAYBACK_RATE, 10.0));
            snapshot = comparisons.command(id, new ComparisonController.Command("start", snapshot.revision(),
                    SimulationController.CommandType.START, null));
            for (int i = 0; i < 100 && snapshot.runs().getLast().status() != BaselineSimulation.Status.COMPLETED; i++) {
                comparisons.tick();
                snapshot = comparisons.get(id);
            }
            assertEquals(BaselineSimulation.Status.RUNNING, snapshot.runs().getFirst().status());
            assertEquals(BaselineSimulation.Status.COMPLETED, snapshot.runs().getLast().status());
            assertNull(snapshot.savedTimeExample());
            verify(gemini).generate(intThat(seconds -> seconds >= 30), anyInt(), anyList());

            for (int i = 0; i < 300 && !snapshot.terminal(); i++) {
                comparisons.tick();
                snapshot = comparisons.get(id);
            }
            assertEquals(ComparisonSnapshot.Status.COMPLETED, snapshot.status());
            assertTrue(snapshot.timeSavedSeconds() >= 30);
            assertNotNull(snapshot.savedTimeExample());
            assertEquals(SavedTimeExamples.Source.LOCAL, snapshot.savedTimeExampleSource());
            verify(gemini, times(1)).generate(anyInt(), anyInt(), anyList());

            comparisons.release(id);
            assertEquals(snapshot, comparisons.result(id));
            var reset = comparisons.command(id, new ComparisonController.Command("reset", snapshot.revision(),
                    SimulationController.CommandType.RESET, null));
            request.complete(new SavedTimeExamples.Example("balon", "Zdążysz nadmuchać balon."));
            assertNull(reset.savedTimeExample());
            assertNull(reset.savedTimeExampleSource());
            assertNull(comparisons.get(id).savedTimeExample());
            comparisons.release(id);
        }
    }

    @Test void thresholdAndDurationBandUseTheMeasuredSaving() {
        var draft = new SavedTimeExamples.Draft(30, CompletableFuture.completedFuture(
                new SavedTimeExamples.Example("origami", "Zdążysz złożyć papierową łódkę.")));
        assertNull(examples.selectAndRecord(29.9, draft));
        assertEquals(new SavedTimeExamples.Selection("Zdążysz złożyć papierową łódkę.", SavedTimeExamples.Source.GEMINI),
                examples.selectAndRecord(30.0, draft));
        assertEquals(SavedTimeExamples.Source.LOCAL, examples.selectAndRecord(60.0, draft).source());
        assertEquals(SavedTimeExamples.Source.LOCAL, examples.selectAndRecord(40.0, draft).source());
        assertNull(examples.selectAndRecord(null, draft));
    }

    @Test void repeatsAreCheckedByTopicAndTextAndLocalExamplesRotate() {
        var sentence = "Zdążysz wykonać kilka przysiadów.";
        var first = new SavedTimeExamples.Draft(120, CompletableFuture.completedFuture(
                new SavedTimeExamples.Example("przysiady", sentence)));
        assertEquals(sentence, examples.selectAndRecord(150.0, first).text());
        for (var duplicate : List.of(new SavedTimeExamples.Example("przysiady", "Zdążysz zrobić kilka przysiadów."),
                new SavedTimeExamples.Example("cwiczenie", sentence))) {
            var draft = new SavedTimeExamples.Draft(120, CompletableFuture.completedFuture(duplicate));
            assertNotEquals(duplicate.text(), examples.selectAndRecord(150.0, draft).text());
        }
        assertNotEquals(examples.selectAndRecord(150.0, null), examples.selectAndRecord(150.0, null));
    }
}
