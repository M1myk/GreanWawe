package com.greenwave.simulation;

import com.greenwave.network.NetworkController.Edge;
import com.greenwave.network.NetworkController.Network;
import com.greenwave.network.NetworkController.Node;
import com.greenwave.trip.api.dto.TripConfigRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ComparisonSessionTest {
    private static final Node A = new Node("a", 54.35, 18.65);
    private static final Node B = new Node("b", 54.351, 18.65);
    private static final Node C = new Node("c", 54.352, 18.65);

    @Test void playbackRateDoesNotChangeResultsAndTheFirstArrivalStaysFrozen() {
        var slow = comparison(1);
        var fast = comparison(5);
        BaselineSimulation.Metrics firstArrival = null;
        for (int i = 0; i < 1000 && slow.status() == ComparisonSnapshot.Status.RUNNING; i++) {
            slow.pulse();
            var snapshot = slow.snapshot();
            var astar = snapshot.runs().get(0);
            var green = snapshot.runs().get(1);
            assertEquals(astar.signals(), green.signals());
            if (green.status() == BaselineSimulation.Status.COMPLETED && astar.status() == BaselineSimulation.Status.RUNNING) {
                if (firstArrival == null) firstArrival = green.metrics();
                assertEquals(firstArrival, green.metrics());
            }
        }
        for (int i = 0; i < 200 && fast.status() == ComparisonSnapshot.Status.RUNNING; i++) fast.pulse();
        var slowerResult = slow.snapshot();
        var fasterResult = fast.snapshot();
        assertNotNull(firstArrival);
        assertEquals(ComparisonSnapshot.Status.COMPLETED, fasterResult.status());
        assertEquals(firstArrival, slowerResult.runs().get(1).metrics());
        assertEquals(slowerResult.simulationTimeSeconds(), fasterResult.simulationTimeSeconds());
        assertEquals(slowerResult.runs().stream().map(BaselineSimulation.Snapshot::metrics).toList(),
                fasterResult.runs().stream().map(BaselineSimulation.Snapshot::metrics).toList());
        assertTrue(slowerResult.timeSavedSeconds() > 0);
        assertEquals(slowerResult.timeSavedSeconds(), fasterResult.timeSavedSeconds());
    }

    @Test void olderResultsWithNoFuelDataCanStillBeRead() {
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        var original = comparison(1).snapshot();
        var oldJson = mapper.writeValueAsString(original).replace(",\"fuelLiters\":0.0", "")
                .replace(",\"greenApproach\":null", "");
        var restored = mapper.readValue(oldJson, ComparisonSnapshot.class);
        assertNull(restored.runs().getFirst().metrics().fuelLiters());
        assertEquals(original.id(), restored.id());
    }

    private ComparisonSession comparison(double rate) {
        var approach = new Edge("ab", A.id(), B.id(), 125, 30 / 3.6, "",
                List.of(List.of(A.lon(), A.lat()), List.of(B.lon(), B.lat())));
        var edges = List.of(approach, edge(B, C, 130));
        var network = new Network("test", "v1", List.of(A, B, C), edges);
        var config = new TripConfigRequest("test", A.id(), C.id(), 42L,
                new TripConfigRequest.Vehicle(13.89, 2.0, 3.0), new TripConfigRequest.Navigation(500.0, 1.0), 1800.0, null);
        var scenario = new SimulationSignals.Scenario(Set.of(), Map.of(B.id(),
                new SimulationSignals.Definition(SignalProgram.Type.FIXED, 150)));
        var definition = new ComparisonSession.Definition(config, network, "v1", SimulationSettings.from(config), scenario, edges, SimulationTraffic.EMPTY);
        var session = new ComparisonSession("comparison", definition, "2026-11-11T10:00:00Z");
        session.rate = rate;
        session.transition(ComparisonSnapshot.Status.RUNNING, BaselineSimulation.Status.READY, BaselineSimulation.Status.RUNNING);
        return session;
    }

    private Edge edge(Node from, Node to, double length) {
        return new Edge(from.id() + to.id(), from.id(), to.id(), length, 13.89, "",
                List.of(List.of(from.lon(), from.lat()), List.of(to.lon(), to.lat())));
    }
}
