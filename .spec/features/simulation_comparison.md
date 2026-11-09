# Simulation control and comparison

Status: feature specification v0.2, draft for review; not implemented.

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
- When: runs execute at 1× and 5×.
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
