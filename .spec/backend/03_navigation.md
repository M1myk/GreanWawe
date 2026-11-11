# Backend navigation

## Baseline A*

Edge cost: lengthM / min(speedLimitMps, vehicle.maxSpeedMps). Heuristic: geographic distance to destination divided by the highest admissible speed in the network for that vehicle. Import must verify that edge length is at least the endpoint distance. The heuristic must not overestimate remaining cost.

Choose the route once before starting. Ignore signal waiting and future events in routing, but use the common physics model to obey actual signals. Break ties consistently by edge ID. Reject an unreachable destination with validation before creating the comparison.

## GreenWave heuristic

The planner knows the graph. It observes stop lines reachable in the directed graph within observationRangeM from its current position. Visible fixed programs allow phase prediction; pedestrian programs expose current phase, established countdown and already disclosed requests, but no future random trials.

Stage 6 defaults: observationRangeM=500 and decisionIntervalSeconds=1. Observe and decide once per simulated second. The current edge and the braking approach to the next turn remain committed. See the [stage 6 implementation plan](../features/greenwave_navigation.md#stage-6-implementation-plan).

1. Estimate edge travel time as in A*.
2. For visible signals, add estimated waiting at arrival. Outside observation range, use zero penalty without reading hidden programs.
3. Freeze weights for each decision using current observations and preliminary baseline arrival estimates. For other visible edges, use graph travel time from the current position ignoring signals. Run A* on those nonnegative weights. This heuristic has no time-optimality guarantee.
4. Keep the current edge. Select a new route at its end rather than turning around mid-edge. Switch when estimated improvement exceeds 2 s and the vehicle is outside the braking approach.
5. For the nearest visible fixed signal on the selected route, evaluate target speeds every 1 m/s from 2 m/s to the limit, also including the exact limit. Forecast with the same physics. Choose the earliest line crossing; break ties by smaller speed change. If no candidate reaches GREEN, the shared physics handles the approach and any stop. For unpredictable pedestrian requests, react to disclosed state without promising a green crossing.

The planner must not wait indefinitely just to avoid stopCount. Time limits and metrics expose such consequences. Recommendations control a virtual vehicle, not a real driver.

## Verification

Compare A* with Dijkstra on small graphs. Test GreenWave on hand-checkable cases: feasible green wave, necessary stop, alternative route, no signals and a pedestrian event changing conditions. Identical observation histories must yield identical decisions even with different hidden future events.
