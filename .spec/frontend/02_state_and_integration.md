# Frontend state and integration

Use React and strict TypeScript. Proposed modules: map, configuration, simulation, comparison and api. Keep draft form state, authoritative backend snapshots and view state separate.

## Fetching and commands

Poll REST every 250 ms during RUNNING and after commands; poll every 2 s while paused. Allow only one active snapshot request per comparison. Changing the ID cancels old requests. A lower revision must not replace newer state.

Disable command controls while sending. Retry a network-failed command with the same commandId. On 409, fetch current state and explain the conflict rather than automatically changing the user's intent. After more than 2 s without a response, stop interpolation and mark the view stale.

Interpolate along route geometry between received positions with up to one polling interval of delay. Do not extrapolate through signals or implement independent physics. Reset clears the buffer; do not animate time moving backwards. Playback-rate changes require backend confirmation.

## Types and responsibilities

API types follow the shared contract. Centralize HTTP errors and unit conversions. Views do not use database entities. Backend computes comparison outcomes; UI only formats/rounds. Null metrics display no result rather than zero.

Verify form validation, control states, TIMEOUT, network failure, stale-snapshot rejection, reset buffering and the complete configuration-to-result journey. Avoid tests tied to internal component layout.
