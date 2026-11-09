# Saved-trip CRUD

Status: feature specification v0.2, draft for review; not implemented.

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
