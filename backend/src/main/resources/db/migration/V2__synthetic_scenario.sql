-- Synthetic coordinates and roads for CRUD validation, not a Gdansk map extract.
INSERT INTO road_network VALUES ('synthetic-v1', 'v1', 'Synthetic test network');
INSERT INTO road_node VALUES ('synthetic-v1', 'n1', 54.350, 18.650);
INSERT INTO road_node VALUES ('synthetic-v1', 'n2', 54.351, 18.650);
INSERT INTO road_node VALUES ('synthetic-v1', 'n3', 54.352, 18.650);
INSERT INTO road_node VALUES ('synthetic-v1', 'n4', 54.355, 18.655);
INSERT INTO road_edge VALUES ('synthetic-v1', 'e1', 'n1', 'n2', 120, 13.89);
INSERT INTO road_edge VALUES ('synthetic-v1', 'e2', 'n2', 'n3', 120, 13.89);
INSERT INTO road_edge VALUES ('synthetic-v1', 'e3', 'n1', 'n3', 260, 13.89);
INSERT INTO scenario VALUES ('synthetic-v1', 'v1', 'synthetic-v1', 'Synthetic scenario (test only)');
