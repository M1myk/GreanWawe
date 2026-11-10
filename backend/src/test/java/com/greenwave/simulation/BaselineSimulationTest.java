package com.greenwave.simulation;

import com.greenwave.network.NetworkController.Edge;
import com.greenwave.network.NetworkController.Network;
import com.greenwave.network.NetworkController.Node;
import com.greenwave.network.RoadRoute;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BaselineSimulationTest {
    private static final Node A = new Node("a", 54.350, 18.65);
    private static final Node B = new Node("b", 54.351, 18.65);
    private static final Node C = new Node("c", 54.352, 18.65);

    private static Edge edge(String id, Node from, Node to, double length, double speed) {
        return new Edge(id, from.id(), to.id(), length, speed, id,
                List.of(List.of(from.lon(), from.lat()), List.of(to.lon(), to.lat())));
    }

    @Test void routeUsesTravelTimeAndRespectsDirection() {
        var aToB = edge("ab", A, B, 120, 12);
        var bToC = edge("bc", B, C, 120, 12);
        var direct = edge("ac", A, C, 230, 8);
        var reverse = edge("ca", C, A, 230, 100);
        var network = new Network("test", "v1", List.of(A, B, C), List.of(aToB, bToC, direct, reverse));
        // The only two directed choices cost 20 s and 28.75 s.
        assertEquals(List.of(aToB, bToC), RoadRoute.findEdges(network, "a", "c", 13));
        assertEquals(List.of(direct), RoadRoute.findEdges(network, "a", "c", 5));
    }

    @Test void waitsForGreenAndStopsAtDestinationWithoutBreakingMotionLimits() {
        var engine = new BaselineSimulation(
                List.of(edge("ab", A, B, 120, 13.89), edge("bc", B, C, 120, 6)),
                List.of(new BaselineSimulation.StopLine("b", 120, List.of(B.lon(), B.lat()), SignalProgram.Type.FIXED, 400)), 42);
        engine.status(BaselineSimulation.Status.RUNNING);
        var previous = engine.snapshot("test", 1);
        for (int i = 0; i < 600 && engine.status() == BaselineSimulation.Status.RUNNING; i++) {
            engine.advance();
            var next = engine.snapshot("test", 1);
            double change = next.vehicle().speedMps() - previous.vehicle().speedMps();
            assertTrue(change >= -0.300001 && change <= 0.200001);
            if (previous.signals().getFirst().phase() == SignalProgram.Phase.RED && previous.metrics().distanceM() <= 120) {
                assertTrue(next.metrics().distanceM() <= 120.000001);
            }
            if (next.metrics().distanceM() >= 120) assertTrue(next.vehicle().speedMps() <= 6.000001);
            previous = next;
        }
        assertEquals(BaselineSimulation.Status.COMPLETED, previous.status(), previous.failure());
        assertEquals(List.of(C.lon(), C.lat()), previous.vehicle().position());
        assertEquals(1, previous.metrics().stopCount());
        assertTrue(previous.metrics().stoppedTimeSeconds() >= 1);
        engine.advance();
        assertEquals(previous, engine.snapshot("test", 1));
    }

    @Test void navigationReportsRouteDistanceAndSkipsOrdinaryBends() {
        var edges = List.of(edge("First street", A, B, 120, 8), edge("Second street", B, C, 120, 8));
        var ordinary = new BaselineSimulation(edges, List.of(), 42);
        assertNull(ordinary.snapshot("test", 1).navigation().upcoming());

        var engine = new BaselineSimulation(edges, List.of(), 42, java.util.Set.of("b"));
        var ready = engine.snapshot("test", 1);
        assertEquals(List.of(0.0, 120.0, 240.0), ready.routeCumulativeMeters());
        assertEquals("First street", ready.navigation().currentStreet());
        var junction = ready.navigation().upcoming();
        assertEquals(BaselineSimulation.JunctionType.JUNCTION, junction.type());
        assertEquals("Second street", junction.nextStreet());
        assertEquals(120, junction.distanceM());
        assertNull(junction.estimatedSeconds());
        engine.status(BaselineSimulation.Status.RUNNING);
        engine.advance();
        var moving = engine.snapshot("test", 1);
        var next = moving.navigation().upcoming();
        assertTrue(next.distanceM() < junction.distanceM());
        assertEquals(next.distanceM() / moving.vehicle().speedMps(), next.estimatedSeconds(), 1e-9);
        for (int i = 0; i < 200 && engine.snapshot("test", 1).navigation().upcoming() != null; i++) engine.advance();
        assertEquals("Second street", engine.snapshot("test", 1).navigation().currentStreet());
        assertNull(engine.snapshot("test", 1).navigation().upcoming());
    }

    @Test void navigationKeepsRedLightVisibleDuringAStop() {
        var engine = new BaselineSimulation(
                List.of(edge("First street", A, B, 120, 13.89), edge("Second street", B, C, 120, 8)),
                List.of(new BaselineSimulation.StopLine("b", 120, List.of(B.lon(), B.lat()), SignalProgram.Type.FIXED, 350)), 42);
        engine.status(BaselineSimulation.Status.RUNNING);
        for (int i = 0; i < 200; i++) engine.advance();
        var waiting = engine.snapshot("test", 1).navigation().upcoming();
        assertEquals(BaselineSimulation.JunctionType.SIGNAL, waiting.type());
        assertEquals(SignalProgram.Phase.RED, waiting.phase());
        assertEquals(0, waiting.distanceM(), 1e-6);
        assertEquals(5, waiting.remainingPhaseSeconds(), 1e-6);
        assertNull(waiting.estimatedSeconds());
    }

    @Test void yellowThresholdIsInclusiveAndRequiresMovement() {
        assertTrue(BaselineSimulation.yellowPermits(30, 8));
        assertFalse(BaselineSimulation.yellowPermits(30.01, 8));
        assertFalse(BaselineSimulation.yellowPermits(10, 0));
    }

    @Test void resetReplaysSeededSignalsAndMotionIndependentlyOfPlaybackRate() {
        var engine = new BaselineSimulation(
                List.of(edge("ab", A, B, 200, 8), edge("bc", B, C, 200, 8)),
                List.of(new BaselineSimulation.StopLine("b", 200, List.of(B.lon(), B.lat()), SignalProgram.Type.PEDESTRIAN, 0)), 42);
        engine.status(BaselineSimulation.Status.RUNNING);
        var snapshots = java.util.stream.IntStream.range(0, 650).mapToObj(i -> {
            engine.advance();
            return engine.snapshot("test", 1);
        }).toList();
        assertTrue(snapshots.stream().anyMatch(s -> s.signals().getFirst().phase() == SignalProgram.Phase.YELLOW));
        engine.reset();
        assertEquals(0, engine.snapshot("test", 5).simulationTimeSeconds());
        engine.status(BaselineSimulation.Status.RUNNING);
        for (var expected : snapshots) {
            engine.advance();
            var actual = engine.snapshot("test", 5);
            assertEquals(expected.metrics(), actual.metrics());
            assertEquals(expected.signals(), actual.signals());
            assertEquals(expected.vehicle(), actual.vehicle());
        }
        assertEquals(BaselineSimulation.Status.COMPLETED, engine.status());
    }
}
