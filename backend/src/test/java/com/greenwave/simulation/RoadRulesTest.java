package com.greenwave.simulation;

import com.greenwave.network.NetworkController.Edge;
import com.greenwave.network.NetworkController.Network;
import com.greenwave.network.NetworkController.Node;
import com.greenwave.network.RoadRoute;
import com.greenwave.network.RoadRules;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoadRulesTest {
    private static final Node A = new Node("a", 54.35, 18.65);
    private static final Node B = new Node("b", 54.351, 18.65);
    private static final Node C = new Node("c", 54.352, 18.65);
    private static final Node D = new Node("d", 54.353, 18.65);
    private static final Node X = new Node("x", 54.352, 18.648);

    @Test void forbiddenViaSequenceAppliesToBothPlannersAndTheCommittedPrefix() {
        var ab = edge("ab", A, B, 100, "Main");
        var bc = edge("bc", B, C, 100, "Main");
        var cd = edge("cd", C, D, 100, "Main");
        var cx = edge("cx", C, X, 180, "Local");
        var xd = edge("xd", X, D, 180, "Local");
        var rules = new RoadRules(List.of(List.of("ab", "bc", "cd")), Map.of());
        var network = new Network("test", "1", List.of(A, B, C, D, X), List.of(ab, bc, cd, cx, xd), rules);
        var legal = List.of(ab, bc, cx, xd);
        assertEquals(legal, RoadRoute.findEdges(network, "a", "d", 14));
        assertEquals(List.of(bc, cd), RoadRoute.findEdges(network, "b", "d", 14));
        var planner = new GreenWavePlanner(network, "d", SimulationSettings.DEFAULT);
        assertEquals(legal, planner.chooseSuffix("a", 0, false, legal, Set.of(), Map.of()));
        var prefix = rules.enter(rules.enter(RoadRules.START, ab), bc);
        var observed = new GreenWavePlanner.Observations(Map.of(), Map.of());
        assertEquals(List.of(cx, xd), planner.chooseSuffix("c", 0, false,
                List.of(cx, xd), Set.of("a", "b"), observed, prefix));
    }

    @Test void aRouteCannotLeaveAndReenterTheSameRoundabout() {
        var ring = edge("ring-in", A, B, 90, "");
        var exit = edge("exit", B, X, 110, "");
        var reenter = edge("ring-back", X, C, 110, "");
        var around = edge("around", B, C, 340, "");
        var finish = edge("finish", C, D, 110, "");
        var rules = new RoadRules(List.of(), Map.of("ring-in", "r", "ring-back", "r", "around", "r"));
        var first = rules.enter(RoadRules.START, ring);
        assertNotNull(rules.enter(first, around));
        assertNull(rules.enter(rules.enter(first, exit), reenter));
        assertNull(rules.enter(first, edge("reverse", B, A, 90, "")));
        var network = new Network("test", "1", List.of(A, B, C, D, X), List.of(ring, exit, reenter, around, finish), rules);
        var planner = new GreenWavePlanner(network, "d", SimulationSettings.DEFAULT);
        var legal = List.of(ring, around, finish);
        assertEquals(legal, RoadRoute.findEdges(network, "a", "d", 14));
        assertEquals(legal, planner.chooseSuffix("a", 0, false, legal, Set.of(),
                Map.of(B.id(), new GreenWavePlanner.Observation(490, null, null))));
    }

    @Test void greenWaveKeepsTheMainRoadWhenTheAlternativeOnlySavesASmallWait() {
        var incoming = edge("ab", A, B, 100, "Main");
        var forward = List.of(edge("bc", B, C, 100, "Main"), edge("cd", C, D, 100, "Main"));
        var detour = List.of(edge("bx", B, X, 100, "Local"), edge("xd", X, D, 100, "Local"));
        var network = new Network("test", "1", List.of(A, B, C, D, X),
                List.of(incoming, forward.get(0), forward.get(1), detour.get(0), detour.get(1)));
        var planner = new GreenWavePlanner(network, "d", SimulationSettings.DEFAULT);
        var context = network.rules().enter(RoadRules.START, incoming);
        var smallWait = new GreenWavePlanner.Observations(Map.of("c", new GreenWavePlanner.Observation(398, null, null)), Map.of());
        assertEquals(forward, planner.chooseSuffix("b", 0, false, forward, Set.of("a"), smallWait, context));
        var candidates = planner.evaluated();
        assertEquals(detour, candidates.getLast().edges());
        assertTrue(candidates.getLast().estimate().arrival() < candidates.getFirst().estimate().arrival());
        var longWait = new GreenWavePlanner.Observations(Map.of("c", new GreenWavePlanner.Observation(340, null, null)), Map.of());
        assertEquals(detour, planner.chooseSuffix("b", 0, false, forward, Set.of("a"), longWait, context));
    }

    private Edge edge(String id, Node from, Node to, double length, String name) {
        return new Edge(id, from.id(), to.id(), length, 50 / 3.6, name,
                List.of(List.of(from.lon(), from.lat()), List.of(to.lon(), to.lat())));
    }
}
