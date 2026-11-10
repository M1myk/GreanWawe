# Saved-trip CRUD

Status: stage-3 CRUD implemented and verified. Comparison-copy behaviour remains an acceptance requirement for the later comparison stage.

## Goal

Save, retrieve, edit and delete named trip configurations through UI and REST.

## Data model

TripPreset: id, name, config, version, createdAt, updatedAt. Config follows the shared comparison contract.

Definitions: [backend models](../backend/01_domain_models.md), [database](../database/01_schema.md).

## Business invariants

Comparisons retain copied configurations after preset edits/deletion. PUT is atomic and checks version. Trimmed names contain 1–80 characters.

Also apply the [shared invariants](../shared/02_business_invariants.md).

## API contract

Full /api/v1/trip-presets CRUD: POST 201, GET/PUT 200, DELETE 204; errors 400/404/409/422.

Fields, units, validation and errors follow the [shared REST contract](../shared/01_api_contracts.md). Do not maintain a competing contract here.

## Given–When–Then acceptance criteria

### PRE-01

- Given: a valid configuration and name.
- When: the user saves through the UI.
- Then: POST returns 201 and the preset survives retrieval and restart.

### PRE-02

- Given: a preset at version 1.
- When: two edits submit version 1.
- Then: one writes version 2 and the other gets 409 without overwriting.

### PRE-03

- Given: a preset used by a comparison.
- When: the preset is deleted.
- Then: preset GET returns 404 and the comparison retains its configuration.

### PRE-04

- Given: an empty name or unreachable destination.
- When: saving is attempted.
- Then: 422 is returned, field errors are shown and no partial row exists.

## Scope and verification

Backend preset API/service/repository, trip_preset migration, frontend form/list. No navigation-engine changes.

Use these criteria for at least three meaningful automated tests for the feature. The implementation plan will identify concrete files and tools. Verify UI integration where applicable. Review proposed defaults in the [decision register](../decisions.md) before implementation.

## Authorized stage-3 implementation plan

The user authorized stage 3 with complete working layers and approved a four-node synthetic scenario; the Gdańsk map remains stage 4. Implement domain records TripConfig/TripPreset, transport DTOs, explicit mapper, JPA entity/repository, transactional service and REST controller. Shared exceptions and advice enforce the documented error contract. No empty future-simulation classes.

Dependencies: Boot-managed Spring Data JPA, validation and Flyway starters; Hibernate community dialects 7.4.13.Final and Flyway 12.4.0 managed by Boot 4.1.2; Xerial sqlite-jdbc 3.54.1.0. Verify actual SQLite migration/JPA compatibility. Use versioned SQL, ddl-auto=none, foreign_keys per connection, busy timeout and a one-connection local pool. Store config as JSON with a scenario FK. Public version starts at 1, mapped from Hibernate's zero-based optimistic version. PUT flushes inside the transaction and stale writes return 409.

Expose scenario/node read DTOs for form selection and use directed reachability validation, not a fake positive check. The synthetic graph is n1→n2→n3, n1→n3 and isolated n4. Reject identical, unknown or unreachable endpoints. Signal stopping feasibility remains deferred until the engine exists; creating a preset does not claim it is safe to execute before that later validation.

Frontend modules: api client, typed trip models, TripPresets page, reusable fields and list controls. Provide create/read/edit/delete, numeric settings, field errors, loading/error states, pagination and deletion confirmation. Preserve edits on failure and distinguish conflict from success. No new frontend dependencies.

Checks: existing health tests, SQLite migration/FK/CRUD/version-conflict/validation integration tests, temporary-file persistence reopening, actual restart retrieval, frontend typecheck/build and a browser CRUD journey. Contract lives in shared/openapi.yaml for implemented endpoints; future simulation paths remain Markdown drafts. Update README and report only actual results.

Verification completed: 10 backend integration tests pass (7 CRUD/scenario/database tests plus 3 health/CORS tests), frontend typecheck/build pass, browser create/edit/reload/validation pass, and data survives a backend restart. PRE-03 comparison-copy verification is deferred because comparisons do not exist yet; deletion itself is tested now.
