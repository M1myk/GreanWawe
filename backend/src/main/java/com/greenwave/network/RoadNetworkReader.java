package com.greenwave.network;

import com.greenwave.network.NetworkController.*;
import com.greenwave.shared.api.ApiException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RoadNetworkReader {
    private final JdbcTemplate jdbc;

    public RoadNetworkReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public NetworkList list() {
        return new NetworkList(jdbc.query("SELECT n.id, n.version, n.name, min(p.lat), min(p.lon), max(p.lat), max(p.lon) FROM road_network n JOIN road_node p ON p.network_id=n.id GROUP BY n.id, n.version, n.name ORDER BY n.id",
                (row, index) -> new Summary(row.getString(1), row.getString(2), row.getString(3),
                        new Bounds(row.getDouble(4), row.getDouble(5), row.getDouble(6), row.getDouble(7)))));
    }

    public Network get(String id) {
        var summary = list().items().stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Road network not found"));
        var nodes = jdbc.query("SELECT node_id, lat, lon FROM road_node WHERE network_id=? ORDER BY node_id",
                (row, index) -> new Node(row.getString(1), row.getDouble(2), row.getDouble(3)), id);
        var nodesById = nodes.stream().collect(Collectors.toMap(Node::id, node -> node));
        var edges = jdbc.query("""
                SELECT edge_id, from_node_id, to_node_id, length_m, speed_limit_mps, name
                FROM road_edge WHERE network_id=? ORDER BY edge_id
                """, (row, index) -> new Edge(row.getString(1), row.getString(2), row.getString(3),
                row.getDouble(4), row.getDouble(5), row.getString(6),
                List.of(coordinate(nodesById.get(row.getString(2))), coordinate(nodesById.get(row.getString(3))))), id);
        return new Network(summary.id(), summary.version(), nodes, edges);
    }

    private static List<Double> coordinate(Node node) { return List.of(node.lon(), node.lat()); }

}
