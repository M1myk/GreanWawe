package com.greenwave.simulation;

import com.greenwave.network.RoadNetworkReader;
import com.greenwave.shared.api.ApiException;
import com.greenwave.trip.domain.TrafficMode;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/traffic")
public class TrafficController {
    private final RoadNetworkReader networks;

    public TrafficController(RoadNetworkReader networks) { this.networks = networks; }

    @GetMapping
    List<SimulationTraffic.Segment> layer(@RequestParam String networkId, @RequestParam TrafficMode mode,
                                          @RequestParam(defaultValue = "42") long seed) {
        if (seed < 0 || seed > Integer.MAX_VALUE) throw ApiException.invalid("seed", "Seed is outside the allowed range.");
        var network = networks.get(networkId);
        return SimulationTraffic.create(network, mode, seed).segments(network);
    }
}
