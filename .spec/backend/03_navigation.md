# Backend navigation

## Baseline A*

Edge cost: lengthM / min(speedLimitMps, vehicle.maxSpeedMps). Heuristic: geographic distance to destination divided by the highest admissible speed in the network for that vehicle. Import must verify that edge length is at least the endpoint distance. The heuristic must not overestimate remaining cost.

Choose the route once before starting. Ignore signal waiting and future events in routing, but use the common physics model to obey actual signals. Break ties consistently by edge ID. Reject an unreachable destination with validation before creating the comparison.

## GreenWave heuristic

The planner knows the graph. It observes stop lines reachable in the directed graph within observationRangeM from its current position. Visible fixed programs allow phase prediction; pedestrian programs expose current phase, established countdown and already disclosed requests, but no future random trials.

Current defaults: observationRangeM=1500 and decisionIntervalSeconds=1. Observe and decide once per simulated second. The current edge and the braking approach to the next turn remain committed. See the [stage 6 implementation plan](../features/greenwave_navigation.md#stage-6-implementation-plan).

1. Treat each directed road node as a vertex. When a path reaches a visible signal vertex, estimate the wait using the predicted arrival for that path. Carry the resulting crossing time into the next edge. Unobserved signals have no estimated wait.
2. Search for the lowest routing cost using time-dependent A* with free-flow remaining travel time as a lower bound. Compare each candidate with the incumbent using the same estimator and apply the switching margin below.
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

## Turn legality and practical route choice

Import available motorcar OSM turn restrictions from the versioned extracts, including via-node and via-way sequences and mandatory turns. Both planners and the route preview share the same transition rules. Reject immediate reversal and re-entry into a roundabout already exited as simulation routing policies. Carry transition history across the committed GreenWave prefix. Apply realistic time costs to significant turns between named streets and leaving a main road for a low-speed side street. Preserve direction, access, and movement constraints, including the existing simulation-only green-window allowance.

Verify prohibited and mandatory turns, via-way context, roundabout re-entry, a sensible main-road choice, and offline migration reproduction. OSM coverage determines which legal restrictions the map can represent.

## Congestion-aware and stable replanning

Estimate visible queue travel at the expected moving fraction of the lowest applicable speed cap, including the queue's ramps and stops. Observe the active edge explicitly and include estimated queue delay and junction waiting in the committed prefix. Traffic farther than the observation boundary uses free-flow estimates. The planner receives aggregate queue observations, without future queue phases.

Rank candidates by projected remaining travel time plus half the expected queue delay. Keep projected time separate from this ranking cost so the queue preference does not shift downstream signal phases. Charge significant turn costs even on unnamed or sharply bending same-name roads, and charge a main-to-side-road transition across different road names even at a shallow angle. When a candidate adds costly maneuvers or enters more congestion, require an improvement of at least five ranking seconds or five percent of the incumbent's remaining ranking cost, whichever is greater. The same margin applies to any mid-drive reroute. At departure, ties without added maneuvers or congestion may still be resolved by lower signal waiting.

Look for a queue-aware winner and keep up to ten distinct legal candidates, including the incumbent and best bounded structural alternatives. Publish the actual evaluated paths, selected path, remaining time estimate, signal waiting and queue delay as part of the run snapshot. Geometry starts at the position used for the latest evaluation. React displays all alternate paths over the traffic layer with a selectable highlight and estimated metrics. The selected route keeps its existing styling. A* and the simulation's movement limits retain their existing behavior.

Verify that an open main road beats a marginal side-road detour, a meaningful bypass can win against a congested incumbent, and the published paths match the distinct options compared under the same observations. A limited search and partial observations can leave some legal paths unexplored.

## Decision explanations

Publish the routing score gain and required switching gain for each evaluated alternative, using the same eligibility rules as selection. Publish recent removals of previously faster alternatives for ten simulation seconds. Identify a selected alternative, a divergence inside the committed prefix, or a slower recalculated ETA when the old path still shares the prefix. Use a neutral explanation for other removals from the bounded search. These diagnostics do not affect route selection.
