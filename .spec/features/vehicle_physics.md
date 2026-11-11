# Vehicle physics

Status: shared vehicle physics is implemented for single-run simulations. The paired comparison remains planned.

## Goal

Apply identical acceleration and braking to both runs.

## Data model

VehicleConfig and RunState in SI units, directed-edge position and defined metrics.

Definitions: [backend models](../backend/01_domain_models.md), [database](../database/01_schema.md).

## Business invariants

INV-04, INV-05 and INV-09. Respect stop lines, speed limits and edge positions. Freeze metrics after arrival; Validate braking feasibility outside the 30 m YELLOW threshold.

Apply the [junction and maneuver limits](../backend/02_simulation_rules.md#junction-and-maneuver-limits) to both algorithms. Also apply the [shared invariants](../shared/02_business_invariants.md).

## API contract

Configuration in POST /api/v1/comparisons; position, speed and metrics in snapshots. No standalone physics endpoint.

Fields, units, validation and errors follow the [shared REST contract](../shared/01_api_contracts.md). Do not maintain a competing contract here.

## Given–When–Then acceptance criteria

### PHY-01

- Given: a vehicle starting at 0 m/s.
- When: an acceleration tick runs.
- Then: speed change and distance follow the same physics model.

### PHY-02

- Given: a red signal and a feasible braking distance.
- When: the vehicle approaches.
- Then: it stops before the line.

### PHY-03

- Given: remaining distance exceeds an edge end.
- When: the vehicle enters the next edge.
- Then: remaining distance is preserved while respecting its speed limit.

## Scope and verification

One vehicle per run; no collisions, queues or fuel model.

Use these criteria for at least three meaningful automated tests for the feature. The implementation plan will identify concrete files and tools. Verify UI integration where applicable. Review proposed defaults in the [decision register](../decisions.md) before implementation.
