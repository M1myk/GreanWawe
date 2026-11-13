package com.greenwave.simulation;

import com.greenwave.network.NetworkController.Edge;
import com.greenwave.network.RoadNetworkReader;
import com.greenwave.network.RoadRoute;
import com.greenwave.shared.api.ApiException;
import com.greenwave.trip.domain.TrafficMode;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.datasource.url=jdbc:sqlite:file:gdansk-stage6?mode=memory&cache=shared")
class GdanskStage6SmokeTest {
    @Autowired RoadNetworkReader networks;

    @Test void demonstrationExplainsCommittedTurnWithoutChangingRoute() {
        var network = networks.get("gdansk-v3");
        var edges = RoadRoute.findEdges(network, "1319985144", "11724815621", 13.89);
        var traffic = SimulationTraffic.create(network, TrafficMode.NORMAL, 42);
        var settings = new SimulationSettings(13.89, 2, 3, 1500, 10, 18_000);
        var run = new BaselineSimulation(edges, 42, SimulationSignals.Scenario.from(network),
                BaselineSimulation.Algorithm.GREENWAVE, network, settings, traffic);
        run.status(BaselineSimulation.Status.RUNNING);
        for (int tick = 0; tick < 1751; tick++) run.advance();
        var before = run.snapshot("demo", 1);
        var faster = before.routeOptions().stream().filter(option -> !option.selected())
                .min(Comparator.comparingDouble(BaselineSimulation.RouteOption::remainingSeconds)).orElseThrow();
        assertTrue(faster.remainingSeconds() < before.routeOptions().getFirst().remainingSeconds());
        assertTrue(faster.scoreGainSeconds() < faster.requiredGainSeconds());

        for (int tick = 1751; tick < 1762; tick++) run.advance();
        var committed = run.snapshot("demo", 1);
        assertTrue(committed.routeNotices().stream().anyMatch(notice -> notice.id().equals(faster.id())
                && notice.reason() == BaselineSimulation.NoticeReason.COMMITTED));
        assertEquals(before.routeGeometry(), committed.routeGeometry());

        for (int tick = 1762; tick < 1862; tick++) run.advance();
        assertTrue(run.snapshot("demo", 1).routeNotices().isEmpty());
    }

    @Test void sampledImportedRoutesCompleteForBothAlgorithms() {
        var network = networks.get("gdansk-v3");
        var scenario = SimulationSignals.Scenario.from(network);
        var random = new Random(42);
        int checked = 0;
        for (int i = 0; i < 60 && checked < 18; i++) {
            var from = network.nodes().get(random.nextInt(network.nodes().size())).id();
            var to = network.nodes().get(random.nextInt(network.nodes().size())).id();
            List<Edge> edges;
            try {
                edges = RoadRoute.findEdges(network, from, to, BaselineSimulation.MAX_SPEED);
            } catch (ApiException ex) {
                if (ex.status() == 422) continue;
                throw ex;
            }
            if (edges.size() < 3) continue;
            for (var algorithm : BaselineSimulation.Algorithm.values()) {
                var engine = new BaselineSimulation(edges, 42, scenario, algorithm, network);
                engine.status(BaselineSimulation.Status.RUNNING);
                for (int tick = 0; tick < 12000 && engine.status() == BaselineSimulation.Status.RUNNING; tick++) engine.advance();
                var result = engine.snapshot("check", 1);
                assertEquals(BaselineSimulation.Status.COMPLETED, result.status(),
                        () -> from + " to " + to + " " + algorithm + " " + result.failure()
                                + " at " + result.simulationTimeSeconds() + " distance " + result.metrics().distanceM());
            }
            checked++;
        }
        assertEquals(18, checked);
    }
}
