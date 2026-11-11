package com.greenwave.simulation;

import com.greenwave.network.NetworkController.Edge;
import com.greenwave.network.RoadNetworkReader;
import com.greenwave.network.RoadRoute;
import com.greenwave.shared.api.ApiException;
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
