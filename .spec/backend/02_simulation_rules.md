# Backend simulation rules

## Clock and lifecycle

Proposed fixed step: 0.1 s. Store time as integer ticks and convert to seconds to avoid clock drift. Playback rates 0.5×/1×/2×/5× change tick throughput in real time, not tick length or outcomes.

READY → RUNNING ↔ PAUSED → COMPLETED. Execution failure leads to FAILED. START requires READY; PAUSE requires RUNNING; RESUME requires PAUSED. RESET from any state restores READY, time zero and both initial run states, and increases revision. Configuration is immutable; changing it creates another comparison.

A tick is atomic across both runs: update signals/events, construct observations, request decisions when due, advance both vehicles, update metrics, publish a snapshot. Serialize commands and ticks per comparison. Never publish a partial tick.

## Signals and randomness

Fixed programs use GREEN → YELLOW → RED → GREEN with an offset from time zero. Intervals include the left boundary and exclude the right. On YELLOW, apply the 30 m stop-line rule below.

Pedestrian signals start GREEN. A request waits until minimum green has elapsed, then triggers YELLOW and RED for the crossing duration, then GREEN. Requests during waiting/service are merged and do not extend the current RED. New requests can be accepted after service ends.

For each pedestrian signal, generate a Bernoulli trial with program probability p every whole simulation second, starting at t=1 s. Derive the timeline from the seed and stable signal ID independently of vehicle visits. The generator and its version belong to engineVersion. Planners never receive future trials, the seed or the schedule.

## Movement

Vehicles start at 0 m/s. Target speed is bounded by the road limit, vehicle maximum and planner recommendation. Per-tick changes respect acceleration and braking; compute distance from initial and final speeds. Crossing edge boundaries preserves remaining distance and checks every stop line and the next speed limit.

Braking distance is v²/(2b). Determine allowable speed from distance to an obstacle, destination or a lower downstream limit. A position guard handles numerical overshoot, but must not conceal impossible deceleration. A vehicle already beyond a stop line continues. The 30 m permission does not change acceleration or braking limits.

No collisions, queues, overtaking or vehicle interactions. Both algorithms use the same physics. “Native” is not an API mode until its meaning is clarified.

## Completion and metrics

Proposed time limit: 1800 simulation seconds. Reaching the limit gives TIMEOUT, not arrival. A comparison completes when both runs are ARRIVED or TIMEOUT; an engine error gives FAILED with a reason code.

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
