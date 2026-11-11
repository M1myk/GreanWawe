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

    @Test void thirtySecondRouteLosesAtTenSecondsOfWaitingAndWinsBelowIt() {
        var viaB = List.of(edge(A, B, 100), edge(B, C, 200));
        var firstBend = new Node("first-bend", 54.3505, 18.65003);
        var secondBend = new Node("second-bend", 54.351, 18.65004);
        var viaDE = List.of(edge(A, firstBend, 100), edge(firstBend, secondBend, 100), edge(secondBend, C, 200));
        var planner = planner(viaB, viaDE);
        assertEquals(viaDE, planner.chooseSuffix(A.id(), 0, false, viaB, Set.of(),
                Map.of(B.id(), fixed(400))));
        assertEquals(viaDE, planner.chooseSuffix(A.id(), 0, false, viaB, Set.of(),
                Map.of(B.id(), fixed(350))));
        assertEquals(viaB, planner.chooseSuffix(A.id(), 0, false, viaDE, Set.of(),
                Map.of(B.id(), fixed(450))));
    }

    @Test void waitingAtTheFirstSignalChangesTheArrivalPhaseAtTheSecond() {
        var firstRoute = List.of(edge(A, B, 100), edge(B, D, 100), edge(D, C, 100));
        var detour = List.of(edge(A, E, 200), edge(E, C, 250));
        var planner = planner(firstRoute, detour);
        assertEquals(firstRoute, planner.chooseSuffix(A.id(), 0, false, detour, Set.of(),
                Map.of(B.id(), fixed(400))));
        assertEquals(detour, planner.chooseSuffix(A.id(), 0, false, firstRoute, Set.of(),
                Map.of(B.id(), fixed(400), D.id(), fixed(50))));
    }

    @Test void arrivalAtTheCommittedJunctionIncludesItsSignalWait() {
        var shortRoute = List.of(edge(B, D, 100), edge(D, C, 100));
        var detour = List.of(edge(B, E, 100), edge(E, C, 170));
        var planner = planner(shortRoute, detour);
        var signals = Map.of(B.id(), fixed(400), D.id(), fixed(100));
        assertEquals(shortRoute, planner.chooseSuffix(B.id(), 5, false, detour, Set.of(A.id()), signals));
        assertEquals(detour, planner.chooseSuffix(B.id(), 5, true, shortRoute, Set.of(A.id()), signals));
    }

    private GreenWavePlanner planner(List<Edge> first, List<Edge> alternative) {
        var edges = new java.util.ArrayList<>(first);
        edges.addAll(alternative);
        var network = new Network("test", "v1", List.of(A, B, C, D, E), edges);
        return new GreenWavePlanner(network, C.id(), new SimulationSettings(10, 2, 3, 500, 10, 18000));
    }

    private GreenWavePlanner.Observation fixed(int cycle) {
        return new GreenWavePlanner.Observation(cycle, null, null);
    }

    @Test void extraSpeedCanCatchGreenOnAThirtyOrFiftyRoad() {
        assertBoost(30, 125, 150);
        assertBoost(50, 155, 150);
    }

    @Test void unreachableCurrentGreenHasNoSpeedUpAdvice() {
        var route = List.of(edge(A, B, 300), edge(B, C, 150));
        var network = new Network("test", "v1", List.of(A, B, C), route);
        var scenario = new SimulationSignals.Scenario(Set.of(B.id()), Map.of(B.id(),
                new SimulationSignals.Definition(SignalProgram.Type.FIXED, 200)));
        var run = new BaselineSimulation(route, 42, scenario, BaselineSimulation.Algorithm.GREENWAVE, network);
        run.status(BaselineSimulation.Status.RUNNING);
        for (int tick = 0; tick < 100; tick++) {
            run.advance();
            var snapshot = run.snapshot("test", 1);
            assertNull(snapshot.greenApproach());
            assertTrue(snapshot.vehicle().speedMps() <= 50 / 3.6 + 1e-7);
        }
    }

    private void assertBoost(double kmh, double length, int greenTicks) {
        double limit = kmh / 3.6;
        var first = new Edge("ab", A.id(), B.id(), length, limit, "",
                List.of(List.of(A.lon(), A.lat()), List.of(B.lon(), B.lat())));
        var route = List.of(first, edge(B, C, 150));
        var network = new Network("test", "v1", List.of(A, B, C), route);
        var scenario = new SimulationSignals.Scenario(Set.of(B.id()), Map.of(B.id(),
                new SimulationSignals.Definition(SignalProgram.Type.FIXED, 300 - greenTicks)));

        for (var algorithm : BaselineSimulation.Algorithm.values()) {
            var run = new BaselineSimulation(route, 42, scenario, algorithm, network);
            run.status(BaselineSimulation.Status.RUNNING);
            double peak = 0;
            boolean advisedExtraSpeed = false;
            for (int tick = 0; tick < greenTicks; tick++) {
                run.advance();
                var snapshot = run.snapshot("test", 1);
                assertNotEquals(BaselineSimulation.Status.FAILED, snapshot.status(), snapshot.failure());
                var advice = snapshot.greenApproach();
                if (algorithm == BaselineSimulation.Algorithm.ASTAR) assertNull(advice);
                if (advice != null) {
                    assertTrue(advice.targetSpeedMps() <= limit + 20 / 3.6 + 1e-7);
                    assertTrue(advice.estimatedCrossingSeconds() < advice.greenRemainingSeconds());
                    advisedExtraSpeed |= advice.targetSpeedMps() > limit + 0.01;
                }
                if (snapshot.metrics().distanceM() >= length) {
                    assertTrue(snapshot.vehicle().speedMps() <= 30 / 3.6 + 1e-7);
                    break;
                }
                peak = Math.max(peak, snapshot.vehicle().speedMps());
            }
            if (algorithm == BaselineSimulation.Algorithm.GREENWAVE) {
                assertTrue(advisedExtraSpeed, "No boost forecast on " + kmh + " km/h road");
                assertTrue(peak > limit, "Car did not use extra speed on " + kmh + " km/h road");
                assertTrue(peak <= limit + 20 / 3.6 + 1e-7);
                assertTrue(run.snapshot("test", 1).metrics().distanceM() >= length, "Missed the current green");
            } else {
                assertTrue(peak <= limit + 1e-7);
            }
        }
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

    @Test void greenWaveReachesNormalSpeedThenCoastsTowardRedWithNoFuelFlow() {
        var edges = List.of(edge(A, B, 120), edge(B, C, 130));
        var network = new Network("test", "v1", List.of(A, B, C), edges);
        var scenario = new SimulationSignals.Scenario(Set.of(), Map.of(B.id(),
                new SimulationSignals.Definition(SignalProgram.Type.FIXED, 400)));
        var baseline = new BaselineSimulation(edges, 42, scenario, BaselineSimulation.Algorithm.ASTAR, network);
        var greenWave = new BaselineSimulation(edges, 42, scenario, BaselineSimulation.Algorithm.GREENWAVE, network);
        baseline.status(BaselineSimulation.Status.RUNNING);
        greenWave.status(BaselineSimulation.Status.RUNNING);
        boolean reachedLimit = false;
        boolean coasted = false;
        for (int i = 0; i < 200; i++) {
            baseline.advance();
            greenWave.advance();
            var snapshot = greenWave.snapshot("test", 1);
            reachedLimit |= snapshot.vehicle().speedMps() >= 49.9 / 3.6;
            assertFalse(baseline.snapshot("test", 1).coasting());
            if (snapshot.coasting()) {
                assertTrue(reachedLimit);
                assertEquals(0, snapshot.fuelFlowLitersPerHour());
                coasted = true;
            }
        }
        assertTrue(coasted);
        complete(greenWave);
    }

    @Test void boostContinuesAcrossShortSegmentsOnTheSameStreet() {
        var p = new Node("p", 54.3503, 18.65);
        var q = new Node("q", 54.3506, 18.65);
        var route = List.of(localEdge(A, p, 35), localEdge(p, q, 40), localEdge(q, B, 50), edge(B, C, 150));
        var network = new Network("test", "v1", List.of(A, p, q, B, C), route);
        var scenario = new SimulationSignals.Scenario(Set.of(B.id()), Map.of(B.id(),
                new SimulationSignals.Definition(SignalProgram.Type.FIXED, 150)));
        var run = new BaselineSimulation(route, 42, scenario, BaselineSimulation.Algorithm.GREENWAVE, network);
        run.status(BaselineSimulation.Status.RUNNING);
        boolean boostedAfterFirst = false;
        for (int i = 0; i < 150; i++) {
            run.advance();
            var state = run.snapshot("test", 1);
            assertNotEquals(BaselineSimulation.Status.FAILED, state.status(), state.failure());
            boostedAfterFirst |= state.metrics().distanceM() > 35 && state.vehicle().speedMps() > 30 / 3.6 + 0.1;
            assertTrue(state.vehicle().speedMps() <= 50 / 3.6 + 1e-7);
        }
        assertTrue(boostedAfterFirst);
        assertTrue(run.snapshot("test", 1).metrics().distanceM() >= 125);
    }

    private Edge localEdge(Node from, Node to, double length) {
        return new Edge(from.id() + to.id(), from.id(), to.id(), length, 30 / 3.6, "Test street",
                List.of(List.of(from.lon(), from.lat()), List.of(to.lon(), to.lat())));
    }

    @Test void comparesTenDistinctRoutesAndObservesBeyondTheOldRange() {
        var nodes = new java.util.ArrayList<>(List.of(A, C));
        var edges = new java.util.ArrayList<Edge>();
        for (int i = 0; i < 12; i++) {
            var mid = new Node("branch" + i, B.lat(), B.lon() + i * 0.00001);
            nodes.add(mid);
            edges.add(edge(A, mid, 700 + i));
            edges.add(edge(mid, C, 900 + i));
        }
        var network = new Network("test", "v1", nodes, edges);
        var planner = new GreenWavePlanner(network, C.id(), SimulationSettings.DEFAULT);
        var observed = planner.observe(A.id(), 0, node -> node.startsWith("branch") ? fixed(400) : null);
        assertEquals(12, observed.size());
        var chosen = planner.chooseSuffix(A.id(), 0, false, edges.subList(0, 2), Set.of(), observed);
        assertEquals(10, planner.candidatesEvaluated());
        assertEquals(10, planner.evaluated().stream().map(GreenWavePlanner.Candidate::edges).distinct().count());
        assertEquals(chosen, planner.evaluated().getFirst().edges());
        assertEquals(edges.subList(0, 2), chosen);
    }

    @Test void onlyVisibleSignalsCanChangeTheSuffixOfAMovingVehicle() {
        var first = edge(A, B, 600);
        var incumbent = List.of(first, edge(B, C, 50), edge(C, D, 100));
        var detour = List.of(first, edge(B, E, 85), edge(E, D, 100));
        var network = new Network("test", "v1", List.of(A, B, C, D, E),
                List.of(first, incumbent.get(1), incumbent.get(2), detour.get(1), detour.get(2)));
        var scenario = new SimulationSignals.Scenario(Set.of(), Map.of(C.id(),
                new SimulationSignals.Definition(SignalProgram.Type.FIXED, 480)));
        var settings = new SimulationSettings(50 / 3.6, 2, 3, 500, 10, 18000);
        var engine = new BaselineSimulation(incumbent, 42, scenario, BaselineSimulation.Algorithm.GREENWAVE, network, settings);
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
