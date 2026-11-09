# GreenWave navigation

Status: feature specification v0.2, draft for review; not implemented.

## Goal

Adapt speed and route using permitted nearby signal observations.

## Data model

Observation, NavigationConfig, RunState and graph. The planner interface exposes neither seed nor future-event schedule.

Definitions: [backend models](../backend/01_domain_models.md), [database](../database/01_schema.md).

## Business invariants

INV-01 and INV-12. Change outgoing route at a node; retain the current edge. Share A* physics. No guarantee of improvement or global optimality.

Also apply the [shared invariants](../shared/02_business_invariants.md).

## API contract

navigation fields in POST /api/v1/comparisons; routeEdgeIds and targetSpeedMps in snapshots. Detailed heuristic remains proposed in backend navigation rules.

Fields, units, validation and errors follow the [shared REST contract](../shared/01_api_contracts.md). Do not maintain a competing contract here.

## Given–When–Then acceptance criteria

### GW-01

- Given: identical observations with different hidden futures.
- When: the planner is invoked.
- Then: it produces identical decisions.

### GW-02

- Given: a feasible green crossing at a lower speed.
- When: speed planning runs.
- Then: the recommendation respects limits and physics.

### GW-03

- Given: an advantageous alternative while travelling on an edge.
- When: replanning runs.
- Then: the current edge remains and the new choice applies at the next node.

## Scope and verification

Simple heuristic with parameters reviewed before coding. No ML or promises of improving every trip.

Use these criteria for at least three meaningful automated tests for the feature. The implementation plan will identify concrete files and tools. Verify UI integration where applicable. Review proposed defaults in the [decision register](../decisions.md) before implementation.
