# Gdańsk road extract

The `gdansk-v1` scenario uses an approximately rectangular interpretation of the supplied reference map. Requested bounds are 54.383–54.426 N and 18.559–18.635 E. The extract contains 6,958 nodes and 11,410 directed edges.

Road geometry was obtained from the public OpenStreetMap map API on 2026-11-10 using smaller bounding boxes, then merged by object ID and version. The versioned SQL migration is the distributable derived road dataset. © OpenStreetMap contributors, ODbL 1.0. [Source and licence](https://www.openstreetmap.org/copyright)

Run `python3 scripts/import-road-network.py extract.osm [more.osm ...]` to generate SQL from complete OSM XML extracts. The script rejects eligible ways with missing referenced nodes before writing SQL. Flyway applies the bundled migration without network access. Once a migration is deployed, publish a new network version through a new migration instead of regenerating it in place.

The importer selects primary, secondary, tertiary, their links, residential and unclassified roads. It excludes explicit private or prohibited vehicle access and segments with endpoints outside the bounds. Explicit one-way directions and roundabout defaults affect directed reachability. It uses a simulation default of 50 km/h where a numeric speed limit is unavailable. The graph omits turn restrictions, conditional access and roads outside the selected classes. The map shows the imported roads and a selection of road names.

Signal programs and pedestrian events will be defined in the simulation stage.

## Gdańsk v2 access roads

V4 adds a separate snapshot with 14,384 nodes and 25,733 directed edges from the same dated OSM extracts. It adds service roads, parking aisles and living streets. The importer follows the most specific motorcar access tag and omits conditional or restricted access, unmarked individual driveways, emergency roads and barrier nodes without explicit permission. The defaults for missing speed limits are 15 km/h on service roads and 20 km/h on living streets. Road tags govern this snapshot. Parking polygons and live gate or space availability are outside its data model.

New map selections use v2. Previously saved v1 scenarios still resolve to their original graph. Reproduce V4 with `python3 scripts/import-road-network.py --version v2 extract.osm [more.osm ...]`. [OpenStreetMap service road tags](https://wiki.openstreetmap.org/wiki/Service_road)

## Gdańsk v3 eastern extension

V5 extends the eastern edge to 18.652 E. Four additional OpenStreetMap XML extracts were downloaded on 2026-11-10 to complete the Łozy corridor and neighboring streets, including Aleksandra Zelwerowicza. The graph has 29,433 directed edges. New map selections use v3. The route preview minimizes imported edge travel time and uses ordered road geometry. Existing access filters apply. The original v3 geometry import omitted turn restrictions. V7 adds the available rules as described below. Conditional access remains outside the model. [OpenStreetMap map API](https://wiki.openstreetmap.org/wiki/API_v0.6)

## Turn and roundabout metadata

V7 loads maneuver metadata from the same November 2026 OSM extracts and keeps the existing geometry and edge IDs. The v3 graph has 129 prohibited directed edge sequences and 257 edges in roundabout groups. Mandatory turns are translated to prohibited competing transitions. Via-node and via-way restrictions are supported for cars when the relation can be resolved on the imported graph. Conditional restrictions, missing OSM tags and lane-level traffic rules are outside the current model. Reproduce the additive migration with python3 scripts/import-road-rules.py followed by the original OSM extracts.
