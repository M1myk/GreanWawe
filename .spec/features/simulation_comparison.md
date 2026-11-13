# Simulation control and comparison

Status: paired comparisons and result persistence implemented in stage 7.

## Goal

Run both algorithms and present fair results.

## Data model

ComparisonConfig, Comparison, two RunState objects and metrics; final persistence follows the database specification.

Definitions: [backend models](../backend/01_domain_models.md), [database](../database/01_schema.md).

## Business invariants

INV-02, INV-03, INV-07, INV-10 and INV-11. One clock and event timeline. Reset atomically invalidates results. Animation is never authoritative.

Also apply the [shared invariants](../shared/02_business_invariants.md).

## API contract

POST /api/v1/comparisons; GET /comparisons/{id}; POST /comparisons/{id}/commands; GET /comparisons/{id}/result.

Fields, units, validation and errors follow the [shared REST contract](../shared/01_api_contracts.md). Do not maintain a competing contract here.

## Given–When–Then acceptance criteria

### CMP-01

- Given: READY.
- When: Start, Pause and Resume are used.
- Then: time advances only in RUNNING.

### CMP-02

- Given: identical configuration, versions and seed.
- When: runs execute at 1× and 10×.
- Then: results are identical.

### CMP-03

- Given: one run times out.
- When: final results are read.
- Then: no fictional travel-time saving is reported.

### CMP-04

- Given: an already executed commandId and identical body.
- When: the command is retried.
- Then: it is not executed twice.

### CMP-05

- Given: a saved result.
- When: Reset is applied.
- Then: state becomes READY at time zero and the old result is removed.

## Scope and verification

Controls, polling, results and persistence. No full telemetry or distributed execution.

Use these criteria for at least three meaningful automated tests for the feature. The implementation plan will identify concrete files and tools. Verify UI integration where applicable. Review proposed defaults in the [decision register](../decisions.md) before implementation.

## Stage 7 implementation plan

Add comparison REST and SQLite storage alongside the existing single-run API. Reuse the Java vehicle engine, route planner, signal scenario and trip configuration. Advance both active engines on one 0.1 s comparison tick. A finished engine keeps its position and metrics. Both engines use the same deterministic seed and per-node timeline regardless of route or polling.

Render both routes and cars on the map, support saved-trip configurations, offer follow mode for either vehicle, and show live metrics and a signed final time difference. Only report savings when both cars arrive.

A single SQLite comparison row stores its metadata and serialized initial or terminal snapshot. Replacing the row atomically changes the result on reset or completion. Interrupted runs report BACKEND_RESTARTED after a restart. A release endpoint discards unfinished work and preserves terminal results. The last 128 successful commands are idempotent within the live session.

Verify lifecycle, revision conflicts, identical timelines, replay, timeout, persisted result retrieval, reset and the browser flow. No new dependency is needed.

## Simulated traffic modes

The configuration accepts NONE, NORMAL and RUSH_HOUR, with NONE for omitted values and historical configurations. Apply the same seed-based corridor profile to both cars. Select named higher-speed corridors deterministically. RUSH_HOUR includes the NORMAL subset and more corridors. The synthetic network has two explicitly slowed edges for tests. The selected corridors use the stop-and-go model below. There are no simulated surrounding vehicles or live traffic inputs.

A* chooses its static route using ordinary road costs and obeys the simulated traffic speeds during movement. GreenWave includes observed congestion in its route estimates. The directed observation range and route commitment rules remain in force. Both cars brake in advance for slower edges. GreenWave boost cannot exceed the traffic cap on a congested edge.

Show a mode selector and a colored traffic layer on the full-screen map. Keep the selection in saved configurations. Changing it prepares a new comparison. Expose a read-only traffic layer for a network, mode and seed. Use the same backend model as the paired run. No migration or dependency is needed.

## Stop-and-go traffic

Selected traffic corridors use a deterministic shared cycle of crawling, braking, standing and restarting. Ordinary traffic crawls up to 20 km/h with a 2 s standstill. Peak traffic crawls up to 14 km/h with a 4 s standstill, or up to 10 km/h with a 6 s standstill on the busiest corridors. Adjacent edges with the same road name and both directions share the same clock offset. Cap and acceleration constraints still apply, and signals and turns retain priority. The planner estimates congestion cost using mean cycle speed; the overlay uses the same estimate and labels the busiest corridors separately.

Integrate fuel using the existing vehicle model, including idle and each acceleration. Surface the actual current queue motion in the comparison. Verify repeated stops, restart, fuel increase on a comparable route, fairness, and deterministic replay. No migration or dependency is needed.
