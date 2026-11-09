# Proposed REST contract v1

Prefix `/api/v1`; UTF-8 JSON; persisted timestamps in UTC ISO 8601. Simulation time uses seconds and IDs are strings. No authentication in the local MVP. Restrict CORS to the configured frontend origin.

## Endpoints

| Method and path | Response |
| --- | --- |
| GET /networks | 200 `{items:[{id,version,name,bounds}]}` |
| GET /networks/{id} | 200 `{id,version,nodes,edges}` following domain models, or 404 |
| GET /scenarios | 200 `{items:[{id,version,name,networkId,networkVersion}]}` |
| GET /scenarios/{id} | 200 scenario metadata and signal-group locations, or 404 |
| POST /comparisons | 201 READY snapshot and Location header |
| GET /comparisons/{id} | 200 current snapshot |
| POST /comparisons/{id}/commands | 200 snapshot after command |
| GET /comparisons/{id}/result | 200 terminal result; 409 while unfinished |

Unlimited history browsing and scenario deletion through the UI are outside the MVP. Completed results survive backend restart; snapshot retrieval for a persisted completed comparison returns the saved final snapshot.

## Create comparison

```json
{
  "scenarioId": "gdansk-demo-v1",
  "startNodeId": "n1",
  "destinationNodeId": "n8",
  "randomSeed": 42,
  "vehicle": {"maxSpeedMps": 13.89, "accelerationMps2": 2, "brakingMps2": 3},
  "navigation": {"observationRangeM": 500, "decisionIntervalSeconds": 1},
  "maxDurationSeconds": 1800
}
```

All fields are required. Proposed bounds: maxSpeedMps (0,40]; accelerationMps2 and brakingMps2 (0,10]; observationRangeM [50,2000]; decisionIntervalSeconds [0.1,10], a multiple of the tick; maxDurationSeconds [10,7200]; integer seed [0,2147483647]. Numbers must be finite. Nodes must belong to the scenario network and have a directed route. Identical endpoints return 422. Configuration must satisfy the scenario's stopping constraints, after resolution of the YELLOW issue in decisions.md.

## Snapshot

```json
{
  "id": "cmp-1",
  "revision": 12,
  "status": "RUNNING",
  "simulationTimeSeconds": 1.2,
  "playbackRate": 1,
  "config": {},
  "networkVersion": "v1",
  "scenarioVersion": "v1",
  "engineVersion": "0.1",
  "runs": [
    {
      "algorithm": "ASTAR",
      "status": "RUNNING",
      "routeEdgeIds": ["e1", "e2"],
      "edgeId": "e1",
      "positionOnEdgeM": 1.44,
      "position": {"lat": 54.35, "lon": 18.65},
      "speedMps": 2.4,
      "targetSpeedMps": 13.89,
      "metrics": {"travelTimeSeconds": null, "elapsedSeconds": 1.2, "distanceM": 1.44, "stopCount": 0, "stoppedTimeSeconds": 0, "speedVariationMps": 2.4}
    }
  ],
  "signals": [{"id": "s1", "phase": "GREEN", "remainingPhaseSeconds": 12}],
  "failure": null
}
```

The example is abbreviated: config contains the full submitted configuration, and runs always contains exactly ASTAR and GREENWAVE. remainingPhaseSeconds is null when the current phase end is not established. Keep the route after arrival; edgeId identifies the last edge. failure is null or `{code,message}`. Snapshot signals support visualization; the backend planner receives a separate filtered observation. Never expose future random-event schedules.

## Commands

Body: `{commandId,expectedRevision,type,playbackRate?}`. Types: START, PAUSE, RESUME, RESET, SET_PLAYBACK_RATE. playbackRate is required only for SET_PLAYBACK_RATE and must be one of 0.5, 1, 2, 5. Reusing commandId with identical body returns the cached response without execution; a different body returns 409. Idempotency lasts for the backend session.

Revision increases after every tick and command. expectedRevision guards against intervening control commands, not ordinary ticks: reject if another command occurred after that revision. Ticks alone do not create conflicts. Snapshots still carry one monotonic revision. Reject revisions newer than the current state as conflicts.

## Results

`{id,config,networkVersion,scenarioVersion,engineVersion,status,runs,timeSavedSeconds,timeSavedPercent,createdAt,finishedAt}`. Runs include terminal statuses and metrics. Savings are null when travel times cannot be fairly compared. RESET invalidates the previous result atomically; use another comparison ID to retain a separate result.

## Errors

`{code,message,fieldErrors:[{field,message}]}`. 400: malformed JSON/format; 404: resource missing; 409: state/command conflict or result not ready; 422: invalid values, unreachable destination or incompatible scenario; 500: internal failure. Return stable codes and readable messages without stack traces.

Before endpoint implementation, express the contract in OpenAPI and validate DTOs and examples against it.

## Saved-trip CRUD

trip-presets are named configurations independent of active simulations.

| Method and path | Contract |
| --- | --- |
| POST /trip-presets | `{name,config}`; 201 `{id,name,config,version,createdAt,updatedAt}` and Location |
| GET /trip-presets | 200 `{items,total,limit,offset}`; default limit 20, maximum 100; offset ≥0; order by updatedAt descending, then id |
| GET /trip-presets/{id} | 200 full resource or 404 |
| PUT /trip-presets/{id} | `{name,config,version}`; full replacement; 200 resource with version+1; 409 for stale version |
| DELETE /trip-presets/{id} | 204; missing resource 404 |

Trimmed name length is 1–80 characters; duplicates are allowed. config uses the comparison-create structure and validation. version is a positive integer starting at 1. Editing/deleting a preset never changes comparisons, each of which owns a copied configuration. Launching a preset submits that copy to POST /comparisons.
