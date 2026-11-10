package com.greenwave.network;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/networks")
public class NetworkController {
    private final RoadNetworkReader networks;

    public NetworkController(RoadNetworkReader networks) {
        this.networks = networks;
    }

    @GetMapping
    public NetworkList list() { return networks.list(); }

    @GetMapping("/{id}")
    public Network get(@PathVariable String id) { return networks.get(id); }

    @GetMapping("/{id}/route")
    public RoadRoute route(@PathVariable String id, @RequestParam String start, @RequestParam String destination) {
        return RoadRoute.between(get(id), start, destination);
    }

    public record Bounds(double south, double west, double north, double east) { }
    public record Summary(String id, String version, String name, Bounds bounds) { }
    public record NetworkList(List<Summary> items) { }
    public record Node(String id, double lat, double lon) { }
    public record Edge(String id, String fromNodeId, String toNodeId, double lengthM, double speedLimitMps, String name, List<List<Double>> geometry) { }
    public record Network(String id, String version, List<Node> nodes, List<Edge> edges) { }
}
