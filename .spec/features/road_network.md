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

Prepared-data import, read API and map display. The Gdańsk frontend searches a bundled partial address index. Road editing and runtime whole-city downloads are outside scope.

Use these criteria for at least three meaningful automated tests for the feature. The implementation plan will identify concrete files and tools. Verify UI integration where applicable. Review proposed defaults in the [decision register](../decisions.md) before implementation.

## Stage 4 scope

Use a versioned OpenStreetMap road extract within approximate bounds 54.383–54.426 N and 18.559–18.635 E. Retain the synthetic fixture for existing saved trips. Generate a Flyway migration offline and serve the resulting graph through read-only endpoints. Straight edges connect consecutive OSM geometry nodes. Apply explicit one-way tags and roundabout defaults. The simplified graph excludes turn restrictions and conditional access rules. Unknown speed limits use a simulation default of 50 km/h. Render the network as an SVG in the existing React form without a new dependency. Endpoint validation continues to use directed reachability. Signal placement and simulation belong to later stages.

## Car access roads

Gdańsk v2 adds mapped service roads, parking aisles and living streets. Include private-property roads with explicit public or permissive motorcar access. Exclude restricted access, conditional access, emergency-only roads, and barriers without explicit car permission. Exclude unmarked individual driveways. Select access nodes through the same directed graph. A parking polygon alone cannot supply a drivable connection. Existing presets retain their original network version.

## Road route preview and eastern extension

Gdańsk v3 extends the graph east to 18.652 E to include the rest of Łozy and nearby connections. Existing v1 and v2 presets keep their network versions. For two selected car access nodes, GET /api/v1/networks/{id}/route returns an ordered road geometry and total distance. The preview minimizes travel time at the imported edge speeds and respects edge direction. Identical endpoints return a zero-length route. Unknown endpoints return 404 and disconnected endpoints return 422. Display the route between road access markers. Keep the free pin at the requested location.
