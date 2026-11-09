# Backend domain models

## Units and identifiers

Simulation time: seconds; distance: metres; speed: m/s; acceleration: m/s². The API uses these units; the UI displays speed in km/h. Coordinates use WGS84 `{lat, lon}`; GeoJSON uses `[lon, lat]`. IDs are opaque strings.

## Network and scenario

| Model | Fields and meaning |
| --- | --- |
| RoadNetwork | id, version, bounds, sourceMetadata |
| RoadNode | id, lat, lon; edge connection point |
| RoadEdge | id, fromNodeId, toNodeId, lengthM, speedLimitMps, geometry |
| SignalGroup | id, controlledEdgeIds; stop lines at the ends of those edges |
| FixedSignalProgram | greenSeconds, yellowSeconds, redSeconds, offsetSeconds |
| PedestrianProgram | minVehicleGreenSeconds, yellowSeconds, pedestrianRedSeconds, requestProbabilityPerSecond |
| Scenario | id, version, networkVersion, signals and programs |

Edges are directed; a two-way road has two edges. Lengths and speed limits are positive. Programs define full cycles. MVP signals represent simplified directional groups and crossings, not a complete conflict-aware intersection controller. All signal behaviour is simulated.

## Comparison and runs

| Model | Fields and meaning |
| --- | --- |
| ComparisonConfig | scenarioId, startNodeId, destinationNodeId, randomSeed, vehicle, navigation, maxDurationSeconds |
| VehicleConfig | maxSpeedMps, accelerationMps2, brakingMps2 |
| NavigationConfig | observationRangeM, decisionIntervalSeconds |
| Comparison | id, config, status, simulationTimeSeconds, revision, engineVersion, two runs |
| RunState | algorithm, routeEdgeIds, edgeId, positionOnEdgeM, speedMps, status, metrics |
| Observation | current time, own state, known graph, in-range signals and disclosed programs |
| PedestrianEvent | signalGroupId, simulationTimeSeconds; private engine data |

Algorithms: ASTAR and GREENWAVE. Comparison states: READY, RUNNING, PAUSED, COMPLETED, FAILED. Run states: PENDING, RUNNING, ARRIVED, TIMEOUT, FAILED.

Each run has an independent vehicle and navigation state; vehicles do not interact. Both share the clock and signal-event timeline. An arrived vehicle remains at the destination with frozen metrics.

## Service responsibilities

- NetworkService: validate and expose graph and geometry.
- ComparisonService: lifecycle, configuration validation and result persistence.
- SimulationEngine: deterministic ticks, phases, physics and metrics.
- AStarPathfinder: baseline route.
- GreenWavePlanner: target speed and route decisions from observations.
- Repository: persist/retrieve versioned data without domain decisions.
