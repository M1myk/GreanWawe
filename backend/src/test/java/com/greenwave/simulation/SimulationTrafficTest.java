package com.greenwave.simulation;

import com.greenwave.network.NetworkController.Edge;
import com.greenwave.network.NetworkController.Network;
import com.greenwave.network.NetworkController.Node;
import com.greenwave.trip.domain.TrafficMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimulationTrafficTest {
    private static final Node A = new Node("a", 54.350, 18.65);
    private static final Node B = new Node("b", 54.351, 18.65);
    private static final Node C = new Node("c", 54.352, 18.65);

    @Test void peakIncludesNormalCorridorsAndKeepsOppositeDirectionsConsistent() {
        var edges = new ArrayList<Edge>();
        for (int i = 0; i < 80; i++) {
            String street = "Corridor " + i;
            edges.add(edge("out" + i, A, B, 100, street));
            edges.add(edge("back" + i, B, A, 100, street));
        }
        var network = new Network("test", "1", List.of(A, B), edges);
        assertTrue(SimulationTraffic.create(network, TrafficMode.NONE, 42).caps().isEmpty());
        var normal = SimulationTraffic.create(network, TrafficMode.NORMAL, 42);
        var peak = SimulationTraffic.create(network, TrafficMode.RUSH_HOUR, 42);
        assertEquals(peak, SimulationTraffic.create(network, TrafficMode.RUSH_HOUR, 42));
        assertFalse(normal.caps().isEmpty());
        assertTrue(peak.caps().size() > normal.caps().size());
        normal.caps().forEach((edge, cap) -> assertTrue(peak.caps().get(edge) < cap));
        for (int i = 0; i < 80; i++) assertEquals(peak.caps().get("out" + i), peak.caps().get("back" + i));
    }

    @Test void bothCarsRespectTheSameCapAndBrakeBeforeAQueue() {
        var edges = List.of(edge("ab", A, B, 150, "A"), edge("bc", B, C, 120, "B"));
        var network = new Network("test", "1", List.of(A, B, C), edges);
        var traffic = new SimulationTraffic(Map.of("bc", 5.0));
        var scenario = new SimulationSignals.Scenario(Set.of(), Map.of());
        for (var algorithm : BaselineSimulation.Algorithm.values()) {
            var run = new BaselineSimulation(edges, 42, scenario, algorithm, network, SimulationSettings.DEFAULT, traffic);
            run.status(BaselineSimulation.Status.RUNNING);
            boolean reachedCruise = false;
            for (int tick = 0; tick < 800 && run.status() == BaselineSimulation.Status.RUNNING; tick++) {
                run.advance();
                var state = run.snapshot("test", 1);
                reachedCruise |= state.vehicle().speedMps() > 10;
                if (state.metrics().distanceM() >= 150) assertTrue(state.vehicle().speedMps() <= 5.000001);
            }
            assertTrue(reachedCruise);
            assertEquals(BaselineSimulation.Status.COMPLETED, run.status(), run.failure());
        }
    }

    @Test void greenWaveCanAvoidAnObservedSlowCorridor() {
        var main = List.of(edge("ab", A, B, 100, "Main"), edge("bc", B, C, 100, "Main"));
        var detour = edge("ac", A, C, 250, "Detour");
        var network = new Network("test", "1", List.of(A, B, C), List.of(main.get(0), main.get(1), detour));
        var planner = new GreenWavePlanner(network, C.id(), SimulationSettings.DEFAULT);
        var empty = planner.observe(A.id(), 0, node -> null, SimulationTraffic.EMPTY);
        assertEquals(main, planner.chooseSuffix(A.id(), 0, false, main, Set.of(), empty));
        var traffic = new SimulationTraffic(Map.of("ab", 5.0, "bc", 5.0));
        var seen = planner.observe(A.id(), 0, node -> null, traffic);
        assertEquals(List.of(detour), planner.chooseSuffix(A.id(), 0, false, main, Set.of(), seen));
        var distant = planner.observe(A.id(), 1600, node -> null, traffic);
        assertEquals(main, planner.chooseSuffix(A.id(), 0, false, main, Set.of(), distant));
    }

    private Edge edge(String id, Node from, Node to, double length, String name) {
        return new Edge(id, from.id(), to.id(), length, 50 / 3.6, name,
                List.of(List.of(from.lon(), from.lat()), List.of(to.lon(), to.lat())));
    }
}
