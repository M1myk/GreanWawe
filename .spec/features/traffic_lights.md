# Traffic lights

Status: feature specification v0.2, draft for review; not implemented.

## Goal

Simulate fixed cycles and pedestrian requests without disclosing future events.

## Data model

SignalGroup, FixedSignalProgram, PedestrianProgram and PedestrianEvent; phases GREEN/YELLOW/RED.

Definitions: [backend models](../backend/01_domain_models.md), [database](../database/01_schema.md).

## Business invariants

INV-01 and INV-04. Random timelines depend on seed/version, never vehicle routes. Planners receive only permitted observations. Apply the approved 30 m YELLOW rule in the simulation rules.

Also apply the [shared invariants](../shared/02_business_invariants.md).

## API contract

GET /api/v1/scenarios and /scenarios/{id} expose configuration; GET /comparisons/{id} exposes current signals. No controller CRUD.

Fields, units, validation and errors follow the [shared REST contract](../shared/01_api_contracts.md). Do not maintain a competing contract here.

## Given–When–Then acceptance criteria

### SIG-01

- Given: a fixed program and offset.
- When: a tick reaches a phase boundary.
- Then: exactly one phase applies using the specified interval boundaries.

### SIG-02

- Given: the same seed and scenario.
- When: signals are visited in different orders.
- Then: the event timeline remains identical.

### SIG-03

- Given: a pedestrian request already being served.
- When: another request occurs during RED.
- Then: it is merged without extending that RED.

## Scope and verification

Prepared simulated programs and visualization. No ITS, complete conflict-aware intersection control or real signal operation.

Use these criteria for at least three meaningful automated tests for the feature. The implementation plan will identify concrete files and tools. Verify UI integration where applicable. Review proposed defaults in the [decision register](../decisions.md) before implementation.
