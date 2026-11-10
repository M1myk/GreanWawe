# Road network

Status: stage 4 implemented.

## Goal

Expose a small prepared Gdańsk road network and select reachable endpoints.

## Data model

RoadNetwork, RoadNode, RoadEdge and source metadata. Directed edges and WGS84 geometry.

Definitions: [backend models](../backend/01_domain_models.md), [database](../database/01_schema.md).

## Business invariants

Positive lengths/limits, matching geometry endpoints and references within one network version. Networks used in results are immutable.

Also apply the [shared invariants](../shared/02_business_invariants.md).

## API contract

GET /api/v1/networks and /networks/{id}: 200 or 404 as specified in the shared contract.

Fields, units, validation and errors follow the [shared REST contract](../shared/01_api_contracts.md). Do not maintain a competing contract here.

## Given–When–Then acceptance criteria

### NET-01

- Given: a valid extract.
- When: it is imported and retrieved.
- Then: all edges reference existing nodes and matching geometry.

### NET-02

- Given: an edge with a missing node.
- When: import runs.
- Then: the whole import is rejected with a reason.

### NET-03

- Given: a one-way road without an alternative.
- When: a reverse trip is selected.
- Then: validation reports no directed route.

## Scope and verification

Prepared-data import, read API and map display. No road editor, geocoding or runtime whole-city download.

Use these criteria for at least three meaningful automated tests for the feature. The implementation plan will identify concrete files and tools. Verify UI integration where applicable. Review proposed defaults in the [decision register](../decisions.md) before implementation.

## Stage 4 scope

Use a versioned OpenStreetMap road extract within approximate bounds 54.383–54.426 N and 18.559–18.635 E. Retain the synthetic fixture for existing saved trips. Generate a Flyway migration offline and serve the resulting graph through read-only endpoints. Straight edges connect consecutive OSM geometry nodes. Apply explicit one-way tags and roundabout defaults. The simplified graph excludes turn restrictions and conditional access rules. Unknown speed limits use a simulation default of 50 km/h. Render the network as an SVG in the existing React form without a new dependency. Endpoint validation continues to use directed reachability. Signal placement and simulation belong to later stages.
