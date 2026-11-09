# Database schema and persistence

SQLite serves one local backend instance. Store scenarios, configurations and results, not every tick. Active simulation state is in memory. Restart marks unfinished comparisons FAILED with BACKEND_RESTARTED; automatic resume is out of scope.

## Logical schema

| Table | Keys and main fields |
| --- | --- |
| road_network | id PK, version, name, source_metadata_json, bounds_json |
| road_node | network_id FK, node_id, lat, lon; PK(network_id,node_id) |
| road_edge | network_id, edge_id, from_node_id, to_node_id, length_m, speed_limit_mps, geometry_json; PK(network_id,edge_id) |
| scenario | id PK, version, network_id FK, name, signal_programs_json |
| comparison | id PK, scenario_id FK, scenario_version, network_version, engine_version, config_json, status, created_at, finished_at, failure_code |
| run_result | comparison_id FK, algorithm, status, travel_time_seconds nullable, elapsed_seconds, distance_m, stop_count, stopped_time_seconds, speed_variation_mps; PK(comparison_id,algorithm) |
| comparison_result | comparison_id PK/FK, final_snapshot_json, time_saved_seconds nullable, time_saved_percent nullable |
| trip_preset | id PK, name, scenario_id FK, config_json, version, created_at, updated_at |

Network/scenario IDs identify immutable versions; data changes create new IDs, while version is a readable label. UNIQUE(id,version) is unnecessary when id is already unique. Edge-to-node foreign keys are composite and include network_id. Index road_edge(network_id,from_node_id) and comparison(created_at). Program JSON is versioned and validated during import.

## Integrity and writes

Enable foreign_keys per connection. CHECK positive lengths/limits, nonnegative counters and allowed enums. Validate program and geometry during import; geometry endpoints must match nodes. Prevent deleting networks/scenarios referenced by comparisons.

Creating a comparison persists configuration and versions. Serialize operations per comparison; avoid holding long database locks during computation. Completion atomically stores status, both run_result rows and comparison_result. RESET atomically removes old results and restores READY before publishing the snapshot. Persistence failure must not be reported as successful completion/reset.

## Migrations and reproducibility

Version numbered SQL migrations and run them before services start. Do not use automatic ORM schema updates as migration management. Verify the migration tool and SQLite dialect before implementation.

Replay requires graph, scenario, engine and generator versions plus full configuration; seed alone is insufficient. Stored results are not frame recordings. Full telemetry export is outside the MVP.

Use temporary SQLite databases for integration tests: empty-database migrations, foreign keys, composite references, rollback, reset and result retrieval after restart.

## Preset CRUD

Store the full configuration and name. UPDATE checks id and version; no match means conflict or missing resource. Comparisons keep independent config_json and no preset dependency. Deleting a preset never deletes comparisons. Its scenario must exist. Test CRUD, version conflicts and historical-result preservation.
