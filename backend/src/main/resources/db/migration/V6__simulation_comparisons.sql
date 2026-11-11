CREATE TABLE comparison (
    id TEXT PRIMARY KEY,
    scenario_id TEXT NOT NULL REFERENCES scenario(id),
    status TEXT NOT NULL CHECK (status IN ('READY', 'COMPLETED', 'FAILED')),
    snapshot_json TEXT NOT NULL,
    interrupted_at TEXT
);
