CREATE TABLE road_network (
    id TEXT PRIMARY KEY, version TEXT NOT NULL, name TEXT NOT NULL
);
CREATE TABLE road_node (
    network_id TEXT NOT NULL REFERENCES road_network(id),
    node_id TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL,
    PRIMARY KEY (network_id, node_id)
);
CREATE TABLE road_edge (
    network_id TEXT NOT NULL, edge_id TEXT NOT NULL,
    from_node_id TEXT NOT NULL, to_node_id TEXT NOT NULL,
    length_m REAL NOT NULL CHECK (length_m > 0),
    speed_limit_mps REAL NOT NULL CHECK (speed_limit_mps > 0),
    PRIMARY KEY (network_id, edge_id),
    FOREIGN KEY (network_id, from_node_id) REFERENCES road_node(network_id, node_id),
    FOREIGN KEY (network_id, to_node_id) REFERENCES road_node(network_id, node_id)
);
CREATE INDEX idx_edge_from ON road_edge(network_id, from_node_id);
CREATE TABLE scenario (
    id TEXT PRIMARY KEY, version TEXT NOT NULL,
    network_id TEXT NOT NULL REFERENCES road_network(id), name TEXT NOT NULL
);
CREATE TABLE trip_preset (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 80),
    scenario_id TEXT NOT NULL REFERENCES scenario(id),
    config_json TEXT NOT NULL,
    version INTEGER NOT NULL CHECK (version >= 0),
    created_at TEXT NOT NULL, updated_at TEXT NOT NULL
);
CREATE INDEX idx_preset_updated ON trip_preset(updated_at DESC, id);
