# A* navigation

Status: feature specification v0.2, draft for review; not implemented.

## Goal

Provide a stable routing baseline without signal-aware optimization.

## Data model

RoadNetwork, VehicleConfig and edgeId route lists; weights and heuristic in backend navigation rules.

Definitions: [backend models](../backend/01_domain_models.md), [database](../database/01_schema.md).

## Business invariants

The heuristic does not overestimate. Route stays fixed; shared physics still obeys signals. Stable tie-breaking.

Also apply the [shared invariants](../shared/02_business_invariants.md).

## API contract

POST /api/v1/comparisons prepares routes; snapshots expose routeEdgeIds. Unreachable routes return 422.

Fields, units, validation and errors follow the [shared REST contract](../shared/01_api_contracts.md). Do not maintain a competing contract here.

## Given–When–Then acceptance criteria

### AST-01

- Given: a small graph with known costs.
- When: A* finds a route.
- Then: its cost agrees with Dijkstra.

### AST-02

- Given: two equal-cost routes.
- When: search is repeated.
- Then: stable ties yield the same route.

### AST-03

- Given: disconnected endpoints.
- When: comparison preparation is attempted.
- Then: 422 is returned without starting the engine.

## Scope and verification

Graph algorithm and comparison integration. No external routing service.

Use these criteria for at least three meaningful automated tests for the feature. The implementation plan will identify concrete files and tools. Verify UI integration where applicable. Review proposed defaults in the [decision register](../decisions.md) before implementation.
