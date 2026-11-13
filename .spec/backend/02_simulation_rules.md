# Backend simulation rules

The implemented single-run lifecycle and defaults are documented in the [stage 5 baseline specification](../features/baseline_simulation.md). Stage 7 implements the two-run lifecycle, shared scenarios and comparison persistence below.

## Clock and lifecycle

Proposed fixed step: 0.1 s. Store time as integer ticks and convert to seconds to avoid clock drift. Playback rates 0.5×/1×/2×/5×/10× change tick throughput in real time, not tick length or outcomes.

READY → RUNNING ↔ PAUSED → COMPLETED. Execution failure leads to FAILED. START requires READY; PAUSE requires RUNNING; RESUME requires PAUSED. RESET from any state restores READY, time zero and both initial run states, and increases revision. Configuration is immutable; changing it creates another comparison.

A tick is atomic across both runs: update signals/events, construct observations, request decisions when due, advance both vehicles, update metrics, publish a snapshot. Serialize commands and ticks per comparison. Never publish a partial tick.

## Signals and randomness

Fixed programs use GREEN → YELLOW → RED → GREEN with an offset from time zero. The 60 s cycle contains 30 s green, 3 s yellow and 27 s red. Intervals include the left boundary and exclude the right. On YELLOW, apply the 30 m stop-line rule below.

Pedestrian signals start GREEN. A request waits until minimum green has elapsed, then triggers 3 s of YELLOW and 10 s of RED for the crossing duration, then GREEN. Requests during waiting/service are merged and do not extend the current RED. New requests can be accepted after service ends.

For each pedestrian signal, generate a Bernoulli trial with program probability p every whole simulation second, starting at t=1 s. Derive the timeline from the seed and stable signal ID independently of vehicle visits. The generator and its version belong to engineVersion. Planners never receive future trials, the seed or the schedule.

## Movement

Vehicles start at 0 m/s. Target speed is bounded by the road limit, vehicle maximum and planner recommendation. Per-tick changes respect acceleration and braking; compute distance from initial and final speeds. Crossing edge boundaries preserves remaining distance and checks every stop line and the next speed limit.

Braking distance is v²/(2b). Determine allowable speed from distance to an obstacle, destination or a lower downstream limit. A position guard handles numerical overshoot, but must not conceal impossible deceleration. A vehicle already beyond a stop line continues. The 30 m permission does not change acceleration or braking limits.

No collisions, queues, overtaking or vehicle interactions. Both algorithms use the same physics. “Native” is not an API mode until its meaning is clarified.

## Completion and metrics

Default time limit: 3000 simulation seconds. Reaching the limit gives TIMEOUT, not arrival. A comparison completes when both runs are ARRIVED or TIMEOUT; an engine error gives FAILED with a reason code.

- travelTimeSeconds: start-to-arrival time; null before arrival and for TIMEOUT.
- elapsedSeconds: elapsed run time up to the current or terminal state.
- distanceM: cumulative travelled distance.
- stopCount: transition from >0.1 m/s to ≤0.1 m/s lasting at least 1 s; exclude the initial state and destination stop.
- stoppedTimeSeconds: time at ≤0.1 m/s before the destination, excluding the initial tick.
- speedVariationMps: sum of |v(t+1)−v(t)|; an operational speed-change metric, not a comfort or fuel estimate.

Time saved = A* time minus GreenWave time; percentage uses A* time as the denominator. Compute only if both arrived and A* time is positive. Otherwise show an incomplete comparison.

## Yellow-light entry threshold

Measure distance along the chosen route from the vehicle to the next applicable stop line. When a moving vehicle first encounters YELLOW for that approach, it may cross while YELLOW remains active if the distance is at most 30 m. Otherwise it must stop and wait for GREEN. Hold the decision for that YELLOW phase so braking into the 30 m zone does not change a stop decision. RED always prohibits new entry. A vehicle already beyond the line continues. A stopped vehicle waits for GREEN.

When configuring the simulation, validate that approaches outside the threshold can brake in time under the configured speed and braking limits. Position clamping must not hide discontinuous physics.

## Junction and maneuver limits

Both algorithms use the same maneuver caps. Intermediate graph junctions with at least three distinct neighbors and simulated stop lines have a 30 km/h maximum. Any intermediate vertex with a heading change of at least 30° has a 20 km/h maximum. A change of at least 60°, including sharp bends and U-turn geometry, has a 15 km/h maximum. These are simulation defaults based on geometry. The applicable cap is the smallest of the maneuver, vehicle and road limits. Brake before the maneuver and hold the cap from 2 m before the vertex until 2 m after it, allowing one additional tick of braking margin before the zone. Stop lines and the destination retain priority.

When yellow entry is considered, forecast the crossing under the active road and maneuver limits before committing. For an allowed crossing, hold the route and suspend advisory slowdown until the line is crossed. A conservative forecast also accounts for the next stop line and destination, so a nearby downstream stop cannot invalidate the commitment.

## Estimated gasoline use

Integrate one shared warmed-up gasoline 2.0 L reference model over successful vehicle ticks. Use rolling and aerodynamic road load, positive tractive work and a load-dependent conversion to fuel. Stationary ticks use idle flow. Low-speed braking without positive traction uses idle flow. Above the model cutoff threshold, deceleration without positive traction uses zero injection. A new acceleration after a stop consumes fuel through positive kinetic work. Fuel remains frozen after arrival and resets with the trip. Two trips with different routes or speeds may differ for reasons other than stop count.

See [fuel model inputs](../../docs/fuel-model.md). The metrics field fuelLiters is nullable for older stored results. No database migration is needed.

## Transient acceleration fuel

Add a shared positive engine-speed transient correction to the wheel-work gasoline estimate. Use the EPA Mazda 2.0 calibration coefficient and a continuous low-gear road-speed approximation. Idle and fuel cut retain their existing behavior. Expose cumulative transient fuel separately in both runs. Historical snapshots without the new metric remain readable.
