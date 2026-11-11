# Stage 5 baseline simulation

The user authorized implementation of the next stage. This stage exposes a single ASTAR simulation through /api/v1/simulations. Comparison endpoints remain assigned to a later stage.

## Plan and boundaries

Add a pure A* pathfinder and baseline simulation model, a session service with a fixed tick scheduler, controller/request/response DTOs, and domain and HTTP tests. React polls the server, renders signals and a vehicle on the map, and provides start/pause/resume/reset and playback-rate controls. Changing endpoints disposes of the previous session. Dependencies remain those already installed.

## Request and lifecycle

POST /simulations accepts {networkId,startNodeId,destinationNodeId,randomSeed}. Validate identifiers, integer seed 0–2147483647, distinct endpoints and directed reachability. Return 201 READY with Location. GET /simulations/{id} returns a snapshot. POST /simulations/{id}/commands accepts {commandId,type,playbackRate?}. Command IDs are limited to 64 characters. Within the last 128 successful commands, duplicate IDs with identical content return the original response. Reusing an ID with different content returns 409. START requires READY, PAUSE requires RUNNING, RESUME requires PAUSED. RESET restores the same scenario and seed to READY and retains the playback rate. SET_PLAYBACK_RATE accepts 0.5,1,2,5. DELETE disposes of a session and returns 204. Unknown IDs return 404.

The local MVP holds at most 32 in-memory sessions. POST returns 409 when full. A browser disposes its session on endpoint change or component cleanup. Sessions expire after 30 minutes without an API read or command. Backend restart also discards these ephemeral sessions. Result persistence belongs to the comparison stage. Serialize commands, ticks and reads within each session. Tick scheduling advances in integer 0.1 s steps. A fractional accumulator implements 0.5× without changing the physics step.

## Route and physics

A* minimizes sum(length / min(edge speed, vehicle maximum)). Use straight-line geographic distance divided by the global maximum admissible speed as the heuristic, scaled by the smallest ratio of stored to geographic edge length, at most 1, with a small numerical tolerance. Stable edge ordering resolves ties. The fixed baseline route does not include signal waiting costs.

Runtime defaults for this stage are 13.89 m/s desired maximum, 2 m/s² acceleration and 3 m/s² braking. The configured maximum and the road limit govern ordinary travel. Near a signal, a shared braking envelope limits speed using the 29 m stopping margin when approaching the 30 m YELLOW threshold. The snapshot reports the configured ordinary vehicle maximum. Look ahead to all lower edge limits, the destination and blocking stop lines. Movement uses the average initial/final speed over each step, preserves remaining distance across edges, and records a failure rather than silently clamping an impossible movement. The stopping envelope accounts for the final fractional braking increment occupying a full tick.

When first observing YELLOW for a phase, a moving vehicle at distance ≤30 m may proceed only while YELLOW remains active. If crossing before RED cannot be achieved at its current speed, brake while feasible. Latch a stop decision for the phase. A stopped vehicle waits for GREEN. RED forbids new entry. A vehicle already beyond the line continues.

## Simulated signals

Select stop lines at network junction nodes having at least three distinct adjacent neighbors and a stable node-ID hash selection. The synthetic fixture places one fixed light at n2. Real-map candidates have alternating fixed and pedestrian programs based on node-ID hash. Signals have stable IDs and appear on the selected route. This placement is a simulation scenario and does not assert real signal locations.

Fixed program: GREEN 30 s, YELLOW 5 s, RED 25 s with stable per-node offsets. Pedestrian program: minimum GREEN 12 s, YELLOW 5 s, RED 10 s, Bernoulli request probability 0.08 per full simulation second. RNG seed is derived from randomSeed and node ID. Events are internal to the engine. RESET recreates the generators. No navigation decision can read future random events.

## Snapshot and metrics

Snapshot contains id, status, simulationTimeSeconds, playbackRate, routeGeometry, vehicle position/speed, effectiveMaxSpeedMps, signals (id,position,phase,remainingPhaseSeconds,type), and metrics (distanceM,elapsedSeconds,travelTimeSeconds nullable,stopCount,stoppedTimeSeconds,speedVariationMps). COMPLETED indicates arrival, TIMEOUT indicates the 1800 s limit, FAILED records a physical/model inconsistency. Stops count only when a moving vehicle has remained at ≤0.1 m/s for at least one second. Exclude initial and destination stops.

## Acceptance and checks

- Given a directed graph with a faster alternative, when A* runs, then it agrees with hand-calculated costs and returns contiguous directed edges.
- Given a closed stop line, when the vehicle approaches, then it remains before the line until permitted while respecting acceleration/braking bounds.
- Given YELLOW at 30 m and at distances above 30 m, when evaluated, then only the near moving vehicle can receive entry permission.
- Given identical configuration and seed, when replayed at different playback rates or reset, then simulation-time snapshots and results agree.
- Given a paused session, when wall time passes, then simulation time and metrics remain fixed.
- Given endpoint change, when a previous response arrives, then React rejects that stale response.
- Given a complete trip, when displayed, then the vehicle ends at the destination and terminal metrics remain unchanged.

Run backend tests, frontend typecheck/build and an actual browser journey. Update the SDD report with actual results and unresolved limits.

## Driving view update

The snapshot includes `routeCumulativeMeters`, aligned with the route coordinates, and `navigation` with the current street and the next junction or simulated light. A junction has at least three distinct neighbors in the road graph. Intermediate route nodes that are only bends are skipped. Distance is measured along the route. The time estimate is distance divided by current speed, without signal delays. It is null when the vehicle is stopped. A light can have a known phase countdown even when the time to reach it is unknown. The display follows the vehicle at a closer zoom and can return to the overview without changing the session.

Update the Java snapshot and graph metadata, the React map and controls, and the OpenAPI contract. No migration or dependency is needed. Verify street and junction transitions, stopped estimates, route interpolation, and switching camera modes during a run.
