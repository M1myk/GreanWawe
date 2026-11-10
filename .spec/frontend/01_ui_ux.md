# Frontend UI and UX

## Main view

A desktop-first map layout: configuration panel on the left, map in the centre, results below or to the right. Narrow windows stack panels without obscured controls or horizontal form scrolling. Apple Maps is an inspiration for simplicity and legibility.

Configuration includes scenario, start/destination selected from nodes or map clicks, maximum speed in km/h, acceleration, braking, seed and observation range. Advanced settings include decision interval and time limit. Fields show units, ranges, defaults and validation errors.

Map clicks select the nearest visible node within 20 pixels; otherwise ask for a more precise selection. Do not create arbitrary points on edges. Separate start/destination selection modes and a node list provide a keyboard alternative.

Show both routes and vehicles, endpoints, signals and a legend. Distinguish ASTAR and GREENWAVE using labels, line styles and colours. Signal state also has text; do not rely on colour alone. Update highlighting after replanning.

## Controls and results

“Prepare comparison” validates and creates READY. Enable Start, Pause, Resume and Reset according to state. Changing configuration after creation requires a new comparison. Playback speed can change in READY, RUNNING and PAUSED.

During travel, show simulation time, current speed, distance and stops. The result table shows both runs with units and statuses. Negative time savings mean GreenWave took longer. TIMEOUT is not arrival. Mark interim values as in progress.

## Errors and accessibility

Loading disables Start. Missing tiles show an explanation while the graph and results remain usable if loaded. Invalid input retains values and shows field errors. Unreachable destinations allow changing endpoints. Connection loss marks the last state stale without fictional continued travel. Engine failures show the reason and allow reset.

Use labels, visible focus and keyboard controls. Announce status changes rather than every clock tick to screen readers. Respect reduced-motion preferences and keep map attribution visible.

## Saved-trip CRUD

List saved names and update dates with pagination. Allow naming and saving the form, loading a preset, editing its name/parameters and deleting with UI confirmation. Deletion does not affect an active comparison's copied configuration. Conflicts offer a reload; failures retain edits. Verify the full UI → REST → SQLite flow.

## Full-screen trip picker

The landing view fills the viewport with the Gdańsk road map. A compact overlay contains origin and destination fields. Users can search the imported street names or focus a field and pick a nearby road node on the map. Origin and destination use distinct pin colors and labels. The landing view requires no trip name. Saved-trip management is available in a separate dialog. The picker uses the existing network API and introduces no new packages or migrations.

The active endpoint is shown in the panel and in the map instructions. Either pin can be dragged with a mouse or touch and snaps to a nearby road node on release. Releasing away from a node or cancelling the gesture retains its previous position. Each field has a separate clear button that removes only its pin and activates that field for the next selection.
