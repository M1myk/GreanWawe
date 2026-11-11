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

DELETE /comparisons/{id} releases an active session and discards unfinished work. Terminal results remain available by ID. Unlimited history browsing and scenario deletion through the UI are outside the MVP. Completed results survive backend restart; snapshot retrieval for a persisted completed comparison returns the saved final snapshot.

## Baseline simulation

The single-run simulation under `/simulations` accepts an optional `algorithm` field at creation. The allowed values are `ASTAR` (default) and `GREENWAVE`. The snapshot reports the selected algorithm. See the [baseline lifecycle and snapshot](../features/baseline_simulation.md) and the [implemented OpenAPI contract](openapi.yaml).

## Create comparison

```json
{
  "scenarioId": "synthetic-v1",
  "startNodeId": "n1",
  "destinationNodeId": "n3",
  "randomSeed": 42,
  "vehicle": {"maxSpeedMps": 13.89, "accelerationMps2": 2, "brakingMps2": 3},
  "navigation": {"observationRangeM": 500, "decisionIntervalSeconds": 1},
  "maxDurationSeconds": 1800
}
```

All fields are required. Proposed bounds: maxSpeedMps (0,40]; accelerationMps2 and brakingMps2 (0,10]; observationRangeM [50,2000]; decisionIntervalSeconds [0.1,10], a multiple of the tick; maxDurationSeconds [10,7200]; integer seed [0,2147483647]. Numbers must be finite. Nodes must belong to the scenario network and have a directed route. Identical endpoints return 422. The configured vehicle maximum caps ordinary travel. The shared stopping envelope limits speed near signals. GreenWave may advise up to 20 km/h above its ordinary cap on the current segment to reach a known green window. Duration is rounded down to a complete tick.

The optional trafficMode accepts NONE, NORMAL and RUSH_HOUR. Missing or null values default to NONE, including older saved configurations. GET /traffic?networkId={id}&mode={mode}&seed={seed} returns affected edge IDs and speed factors for the selected scenario. The seed defaults to 42. The overlay and both cars use the same static simulated traffic model.

## Snapshot

The response contains id, revision, status, simulationTimeSeconds, playbackRate, config, networkVersion, scenarioVersion, engineVersion, createdAt, finishedAt, failure, runs, timeSavedSeconds and timeSavedPercent. Runs are ordered ASTAR then GREENWAVE and reuse the single-run snapshot shape, including current route geometry, vehicle position, route signal phases and metrics. Signal phases are evaluated at shared comparison time. An individual run uses COMPLETED for arrival and TIMEOUT for exceeding the time limit. A comparison with two finished runs has status COMPLETED even if a run timed out. An engine failure gives comparison status FAILED and failure with code and message.

## Commands

Body: `{commandId,expectedRevision,type,playbackRate?}`. Types: START, PAUSE, RESUME, RESET, SET_PLAYBACK_RATE. playbackRate is required only for SET_PLAYBACK_RATE and must be one of 0.5, 1, 2, 5. Reusing commandId with identical body returns the cached response without execution; a different body returns 409. Idempotency covers the last 128 successful commands of a live session.

Revision increases after every tick and command. expectedRevision guards against intervening control commands, not ordinary ticks: reject if another command occurred after that revision. Ticks alone do not create conflicts. Snapshots carry a monotonic revision within the live session. Recovery of an interrupted comparison uses the persisted READY revision and returns the terminal BACKEND_RESTARTED failure. Reject revisions newer than the current state as conflicts.

## Results

GET /comparisons/{id}/result returns the immutable terminal snapshot. Savings are null unless both vehicles arrived. RESET atomically replaces the previous result with a READY snapshot.

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

## Implemented setup endpoint

GET /api/v1/health returns 200 with {"status":"UP","application":"greenwave"}. This availability endpoint is implemented in stage 2; business endpoints above remain planned. CORS now allows GET, POST, PUT, DELETE and OPTIONS for configured origins and exposes Location.

## Stage-3 implemented contract

The machine-readable contract for implemented endpoints is [openapi.yaml](openapi.yaml). Scenario detail currently returns id/version/name/networkId/networkVersion, four nodes as {id,lat,lon}, and an empty signals array for the synthetic fixture. No real map or signal program is claimed. CRUD validates numeric bounds, integer seed, tick interval, scenario membership and directed reachability. Physical signal-stopping feasibility is deferred to the execution stage, pending resolution of YELLOW semantics. Unknown JSON properties and fractional integer fields return 400; semantic validation returns 422. Unsupported HTTP methods return 405. Unexpected failures use the same structured error shape without leaking exception details.

## Stage 4 network reads

GET /networks lists immutable network versions with actual bounds. GET /networks/{id} returns nodes and directed edges, including optional road names represented by empty strings when unavailable and geometry as two [longitude, latitude] coordinates. An unknown ID returns 404. The synthetic fixture is retained. The implemented schemas are in [OpenAPI](openapi.yaml).
