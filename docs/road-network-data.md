# Gdańsk road extract

The `gdansk-v1` scenario uses an approximately rectangular interpretation of the supplied reference map. Requested bounds are 54.383–54.426 N and 18.559–18.635 E. The extract contains 6,958 nodes and 11,410 directed edges.

Road geometry was obtained from the public OpenStreetMap map API on 2026-11-10 using smaller bounding boxes, then merged by object ID and version. The versioned SQL migration is the distributable derived road dataset. © OpenStreetMap contributors, ODbL 1.0. [Source and licence](https://www.openstreetmap.org/copyright)

Run `python3 scripts/import-road-network.py extract.osm [more.osm ...]` to generate SQL from complete OSM XML extracts. The script rejects eligible ways with missing referenced nodes before writing SQL. Flyway applies the bundled migration without network access. Once a migration is deployed, publish a new network version through a new migration instead of regenerating it in place.

The importer selects primary, secondary, tertiary, their links, residential and unclassified roads. It excludes explicit private or prohibited vehicle access and segments with endpoints outside the bounds. Explicit one-way directions and roundabout defaults affect directed reachability. It uses a simulation default of 50 km/h where a numeric speed limit is unavailable. The graph omits turn restrictions, conditional access and roads outside the selected classes. The map shows the imported roads and a selection of road names.

Signal programs and pedestrian events will be defined in the simulation stage.
