package com.greenwave.simulation;

import com.greenwave.network.NetworkController.Edge;
import com.greenwave.network.NetworkController.Network;
import com.greenwave.network.NetworkController.Node;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GreenWavePlannerTest {
    private static final Node A = new Node("a", 54.35, 18.65);
    private static final Node B = new Node("b", 54.351, 18.65);
    private static final Node C = new Node("c", 54.352, 18.65);
    private static final Node D = new Node("d", 54.353, 18.65);
    private static final Node E = new Node("e", 54.352, 18.651);

    private static Edge edge(Node from, Node to, double length) {
        return new Edge(from.id() + to.id(), from.id(), to.id(), length, 13.89, "",
                List.of(List.of(from.lon(), from.lat()), List.of(to.lon(), to.lat())));
    }

    @Test void sharedPhysicsBrakesBeforeStraightJunctionAndTurn() {
        assertManeuverCap(C, true, 30);
        assertManeuverCap(new Node("turn", 54.352, 18.6515), false, 20);
        assertManeuverCap(new Node("right", B.lat(), 18.652), false, 15);
    }

    private void assertManeuverCap(Node end, boolean junction, double kmh) {
        var edges = List.of(edge(A, B, 120), edge(B, end, 130));
        var network = new Network("test", "v1", List.of(A, B, end), edges);
        var scenario = new SimulationSignals.Scenario(junction ? Set.of(B.id()) : Set.of(), Map.of());
        for (var algorithm : BaselineSimulation.Algorithm.values()) {
            var engine = new BaselineSimulation(edges, 42, scenario, algorithm, network);
            engine.status(BaselineSimulation.Status.RUNNING);
            var previous = engine.snapshot("test", 1);
            boolean reachedCruise = false;
            boolean enteredJunction = false;
            for (int i = 0; i < 500 && engine.status() == BaselineSimulation.Status.RUNNING; i++) {
                engine.advance();
                var next = engine.snapshot("test", 1);
                double speed = next.vehicle().speedMps();
                double speedChange = speed - previous.vehicle().speedMps();
                assertTrue(speedChange >= -0.300001 && speedChange <= 0.200001);
                reachedCruise |= speed > 12;
                if (next.metrics().distanceM() >= 118 && previous.metrics().distanceM() <= 122) {
                    enteredJunction = true;
                    assertTrue(speed <= kmh / 3.6 + 1e-7, "Vehicle exceeded the maneuver speed");
                }
                previous = next;
            }
            assertTrue(reachedCruise && enteredJunction);
            assertEquals(BaselineSimulation.Status.COMPLETED, previous.status(), previous.failure());
        }
    }

    @Test void fixedSignalSpeedAdviceAvoidsAStopWhenGreenIsReachable() {
        var edges = List.of(edge(A, B, 120), edge(B, C, 130));
        var network = new Network("test", "v1", List.of(A, B, C), edges);
        var scenario = new SimulationSignals.Scenario(Set.of(), Map.of(B.id(),
                new SimulationSignals.Definition(SignalProgram.Type.FIXED, 400)));
        var baseline = complete(new BaselineSimulation(edges, 42, scenario, BaselineSimulation.Algorithm.ASTAR, network));
        var greenWave = complete(new BaselineSimulation(edges, 42, scenario, BaselineSimulation.Algorithm.GREENWAVE, network));
        assertEquals(1, baseline.metrics().stopCount());
        assertEquals(0, greenWave.metrics().stopCount());
        assertEquals(baseline.metrics().distanceM(), greenWave.metrics().distanceM());
    }

    @Test void onlyVisibleSignalsCanChangeTheSuffixOfAMovingVehicle() {
        var first = edge(A, B, 600);
        var incumbent = List.of(first, edge(B, C, 50), edge(C, D, 100));
        var detour = List.of(first, edge(B, E, 85), edge(E, D, 100));
        var network = new Network("test", "v1", List.of(A, B, C, D, E),
                List.of(first, incumbent.get(1), incumbent.get(2), detour.get(1), detour.get(2)));
        var scenario = new SimulationSignals.Scenario(Set.of(), Map.of(C.id(),
                new SimulationSignals.Definition(SignalProgram.Type.FIXED, 0)));
        var engine = new BaselineSimulation(incumbent, 42, scenario, BaselineSimulation.Algorithm.GREENWAVE, network);
        engine.status(BaselineSimulation.Status.RUNNING);
        engine.advance();
        assertEquals(List.of(0.0, 600.0, 650.0, 750.0), engine.snapshot("test", 1).routeCumulativeMeters());

        BaselineSimulation.Snapshot current;
        do {
            engine.advance();
            current = engine.snapshot("test", 1);
        } while (current.routeCumulativeMeters().get(2) == 650 && current.simulationTimeSeconds() < 30);

        assertEquals(List.of(0.0, 600.0, 685.0, 785.0), current.routeCumulativeMeters());
        assertTrue(current.metrics().distanceM() > 0 && current.metrics().distanceM() < 600);
        assertEquals(A.lon(), current.vehicle().position().getFirst(), 1e-10);
        complete(engine);
    }

    @Test void pedestrianTimelineIsIndependentOfObservationsAndFutureIsHidden() {
        var scenario = new SimulationSignals.Scenario(Set.of(), Map.of(B.id(),
                new SimulationSignals.Definition(SignalProgram.Type.PEDESTRIAN, 0)));
        var polled = new SimulationSignals(scenario, 42);
        var skipped = new SimulationSignals(scenario, 42);
        var differentSeed = new SimulationSignals(scenario, 43);
        boolean differentFuture = false;
        for (int tick = 0; tick <= 1200; tick++) {
            var current = polled.at(B.id(), tick);
            if (tick % 37 == 0) assertEquals(current, skipped.at(B.id(), tick));
            differentFuture |= !current.equals(differentSeed.at(B.id(), tick));
        }
        assertTrue(differentFuture);
        var route = List.of(edge(A, B, 120), edge(B, C, 130));
        var network = new Network("test", "v1", List.of(A, B, C), route);
        var first = new BaselineSimulation(route, 42, scenario, BaselineSimulation.Algorithm.GREENWAVE, network);
        var second = new BaselineSimulation(route, 43, scenario, BaselineSimulation.Algorithm.GREENWAVE, network);
        first.status(BaselineSimulation.Status.RUNNING);
        second.status(BaselineSimulation.Status.RUNNING);
        for (int i = 0; i < 100; i++) {
            first.advance();
            second.advance();
            assertEquals(first.snapshot("test", 1), second.snapshot("test", 1));
        }
    }

    private BaselineSimulation.Snapshot complete(BaselineSimulation engine) {
        engine.status(BaselineSimulation.Status.RUNNING);
        for (int i = 0; i < 2500 && engine.status() == BaselineSimulation.Status.RUNNING; i++) engine.advance();
        var result = engine.snapshot("test", 1);
        assertEquals(BaselineSimulation.Status.COMPLETED, result.status(), result.failure());
        return result;
    }
}
