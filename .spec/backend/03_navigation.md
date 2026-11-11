# Backend navigation

## Baseline A*

Edge cost: lengthM / min(speedLimitMps, vehicle.maxSpeedMps). Heuristic: geographic distance to destination divided by the highest admissible speed in the network for that vehicle. Import must verify that edge length is at least the endpoint distance. The heuristic must not overestimate remaining cost.

Choose the route once before starting. Ignore signal waiting and future events in routing, but use the common physics model to obey actual signals. Break ties consistently by edge ID. Reject an unreachable destination with validation before creating the comparison.

## GreenWave heuristic

The planner knows the graph. It observes stop lines reachable in the directed graph within observationRangeM from its current position. Visible fixed programs allow phase prediction; pedestrian programs expose current phase, established countdown and already disclosed requests, but no future random trials.

Current defaults: observationRangeM=1500 and decisionIntervalSeconds=1. Observe and decide once per simulated second. The current edge and the braking approach to the next turn remain committed. See the [stage 6 implementation plan](../features/greenwave_navigation.md#stage-6-implementation-plan).

1. Treat each directed road node as a vertex. When a path reaches a visible signal vertex, estimate the wait using the predicted arrival for that path. Carry the resulting crossing time into the next edge. Unobserved signals have no estimated wait.
2. Search for the earliest estimated arrival using time-dependent A* with free-flow remaining travel time as a lower bound. Compare the candidate with the current suffix using the same estimator. At equal estimated arrival, prefer the route with less predicted waiting. Retain the current suffix when both values tie.
3. Recalculate from the current simulation time at each navigation decision. Include the remaining travel time on the committed edge and any estimated delay at its end. Do not count a light at the departure or arrival point. Keep the active edge and lock the next turn during the braking approach.
4. Use the nearest visible signal to choose the approach profile below. For pedestrian signals, use the disclosed phase and known countdown. Future requests remain hidden.

The planner must not wait indefinitely just to avoid stopCount. Time limits and metrics expose such consequences. Recommendations control a virtual vehicle, not a real driver.

## Verification

Compare A* with Dijkstra on small graphs. Test GreenWave on hand-checkable cases: feasible green wave, necessary stop, alternative route, no signals and a pedestrian event changing conditions. Identical observation histories must yield identical decisions even with different hidden future events.

## GreenWave green-window acceleration

For the nearest observable fixed signal with a known current green countdown, when the ordinary cap is insufficient, forecast crossing at the upper allowance before green ends, allowing a one-tick reserve. Try the normal speed first. If that is too slow, GreenWave can use up to 20 km/h above the normal cap on the current directed road segment. This is a simulation-only allowance. The road limit and configured normal vehicle cap govern ordinary travel. The extra speed never changes the acceleration, braking, turn or junction limits. The common safety envelope limits the approach so the car can still stop if needed. A* has no signal speed recommendation or extra allowance. When that green is unreachable, use the ordinary approach and coast when waiting is predicted.

Publish greenApproach with the target speed, forecast crossing time and remaining green time while the recommendation applies. Test a feasible extra-speed case, the 20 km/h cap, an infeasible approach and A* isolation.

## Wider observation and driving profile

The default observation range is 1,500 m of directed road distance. Saved configurations can specify their own range. Evaluate up to ten distinct route suffixes when the network offers them. Include the incumbent and a time-dependent search candidate, then add alternatives branching within the observation horizon. Cache the structural alternatives at the planning anchor and rescore them with the latest phases. Keep the committed prefix until a vertex beyond the braking approach.

A feasible current green can use the full simulation allowance when ordinary pace would miss it. The allowance continues across consecutive compatible road segments approaching the same signal. Each segment retains its own ordinary cap plus at most 20 km/h, and all maneuver and stopping constraints still apply. A route with predicted waiting approaches at its ordinary cap, then releases the throttle within a distance where it can coast without crawling. The shared fuel model estimates zero injection while coasting in gear above 20 km/h. Safety braking takes precedence.
