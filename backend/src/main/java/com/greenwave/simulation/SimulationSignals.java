package com.greenwave.simulation;

import com.greenwave.network.NetworkController.Edge;
import com.greenwave.network.NetworkController.Network;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SimulationSignals {
    record Definition(SignalProgram.Type type, int offset) {}
    record Scenario(Set<String> junctions, Map<String, Definition> signals) {
        static Scenario from(Network network) {
            Map<String, Set<String>> neighbors = new HashMap<>();
            for (var edge : network.edges()) {
                neighbors.computeIfAbsent(edge.fromNodeId(), ignored -> new HashSet<>()).add(edge.toNodeId());
                neighbors.computeIfAbsent(edge.toNodeId(), ignored -> new HashSet<>()).add(edge.fromNodeId());
            }
            var junctions = new HashSet<String>();
            neighbors.forEach((node, adjacent) -> { if (adjacent.size() >= 3) junctions.add(node); });
            Map<String, Definition> signals = new HashMap<>();
            if (network.id().equals("synthetic-v1")) {
                signals.put("n2", new Definition(SignalProgram.Type.FIXED, 450));
            } else {
                for (String node : junctions) {
                    int hash = node.hashCode();
                    if (Math.floorMod(hash, 3) != 0) continue;
                    var type = hash % 2 == 0 ? SignalProgram.Type.FIXED : SignalProgram.Type.PEDESTRIAN;
                    signals.put(node, new Definition(type, Math.floorMod(hash, SignalProgram.FIXED_CYCLE_TICKS)));
                }
            }
            return new Scenario(Set.copyOf(junctions), Map.copyOf(signals));
        }

        static Scenario fixture(Set<String> junctions, List<BaselineSimulation.StopLine> lines) {
            var definitions = new HashMap<String, Definition>();
            for (var line : lines) definitions.put(line.id(), new Definition(line.type(), line.offset()));
            return new Scenario(junctions, Map.copyOf(definitions));
        }
    }

    private record Program(SignalProgram clock, int advancedTo) {}
    private final Map<String, Program> programs = new HashMap<>();
    private final Scenario scenario;
    private final long seed;

    SimulationSignals(Scenario scenario, long seed) {
        this.scenario = scenario;
        this.seed = seed;
    }

    SignalProgram.State at(String node, int tick) {
        var definition = scenario.signals().get(node);
        var program = programs.computeIfAbsent(node, key ->
                new Program(new SignalProgram(definition.type(), definition.offset(), seed ^ key.hashCode()), 0));
        if (definition.type() == SignalProgram.Type.PEDESTRIAN && tick / 10 > program.advancedTo() / 10) {
            // Pedestrian transitions and requests occur at whole simulated seconds.
            for (int next = (program.advancedTo() / 10 + 1) * 10; next <= tick; next += 10) {
                program.clock().advance(next);
            }
            programs.put(node, new Program(program.clock(), tick));
        }
        return program.clock().at(tick);
    }

    List<BaselineSimulation.StopLine> routeLines(List<Edge> route) {
        var lines = new java.util.ArrayList<BaselineSimulation.StopLine>();
        double distance = 0;
        for (int i = 0; i < route.size() - 1; i++) {
            var edge = route.get(i);
            distance += edge.lengthM();
            var definition = scenario.signals().get(edge.toNodeId());
            if (definition != null) {
                lines.add(new BaselineSimulation.StopLine(edge.toNodeId(), distance, edge.geometry().getLast(),
                        definition.type(), definition.offset()));
            }
        }
        return List.copyOf(lines);
    }
}
