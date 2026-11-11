# GreenWave navigation

Status: the single-run GreenWave algorithm is implemented in stage 6. Paired runs are implemented in stage 7.

## Goal

Adapt speed and route using permitted nearby signal observations.

## Data model

Observation, NavigationConfig, RunState and graph. The planner interface exposes neither seed nor future-event schedule.

Definitions: [backend models](../backend/01_domain_models.md), [database](../database/01_schema.md).

## Business invariants

INV-01 and INV-12. Change outgoing route at a node; retain the current edge. Share A* physics. No guarantee of improvement or global optimality.

Also apply the [shared invariants](../shared/02_business_invariants.md).

## API contract

The single-run implementation accepts algorithm in POST /api/v1/simulations. The comparison contract describes configuration and paired snapshots.

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

## Stage 6 implementation plan

The user authorized the algorithm and shared maneuver speed limits. Update the Java simulation engine, signal observations and weighted A* search, the simulation API contract and backend tests. Use the existing session lifecycle. The optional `algorithm` field on POST /simulations accepts ASTAR (default) or GREENWAVE. The snapshot names the algorithm. Independent runs use the same physics and the same deterministic per-node signal timeline for identical seed and network. The side-by-side session and UI remain stage 7. No dependencies or database migration are needed.

GreenWave observes signals within the configured directed distance, 1,500 m by default, including the remaining distance on its current edge. At each decision, it evaluates each signal at its path-specific estimated arrival time. Any waiting shifts the estimated arrival at later vertices. It may replace the suffix beyond the current edge for an earlier arrival, or for less waiting when the estimated arrival ties. Previously traversed nodes are excluded from alternatives. Keep the route prefix within the braking lookahead, so a newly selected turn cannot impose an infeasible speed limit. At departure, GreenWave can also choose a different first edge. Only the fixed cycle position or the already-established pedestrian phase and countdown are exposed to the planner.

For the next visible fixed signal on the chosen route, evaluate speeds in 1 m/s increments from 2 m/s and the normal vehicle maximum, using the same acceleration, braking and route limits as the engine. Choose the earliest predicted green crossing, favoring the smallest speed change on ties. The search does not infer future pedestrian requests. If no candidate helps, normal vehicle physics enforces the stop. The safety envelope can override every recommendation.

Verify the shared 30/20/15 km/h limits with approach and crossing tests, a fixed-cycle speed recommendation, route switching with the current edge locked, deterministic pedestrian observations and HTTP selection of the algorithm.

## Time-dependent routing acceptance

Given a 30 s route with 10 s or more of predicted signal delay and a 40 s route without waiting, GreenWave chooses the 40 s route. If the delay falls below 10 s, it chooses the first route again when the turn is still available. Given two signals on the same route, a wait at the first changes the estimated arrival used to evaluate the second. The comparison uses the current observation range, existing signal information and shared route commitment rules. Update the Java planner and tests. No API, migration or dependency change is needed.
