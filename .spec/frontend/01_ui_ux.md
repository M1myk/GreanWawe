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

The full-screen Gdańsk view displays a wider OpenStreetMap basemap with street names, green areas and the coastline. Zoom controls change the visible scale. An outlined circle indicates the approximate simulation coverage and the network bounds show the extent of the imported graph. Endpoint selection and dragging continue to snap to nodes from that graph. Fetch only visible map tiles and retain visible attribution. The road drawing remains available if map tiles are unavailable.

Drag the map background with a mouse or one finger to pan. Zoom with the wheel around the cursor or pinch around the midpoint of two fingers. Panning and pinching must not select an endpoint. Pin dragging stays separate. The endpoint panel grows on large displays and retains its compact mobile layout.

The full-screen map accepts free pin placement inside the marked coverage circle. The displayed pin stays at the selected location. The app resolves a separate car access node on the imported graph, using directed reachability from the other endpoint when available. It shows a separate access marker and label when the selected location differs from the road node. This is a simulation access point. Pedestrian routing and parking availability are outside the scope. Saved presets continue to store road node IDs.

## Driving view

Starting a baseline run opens Driving View. The camera follows the vehicle at street scale. The panel shows the current street, the next junction or simulated light on the route, its distance and an estimated time at current speed. A stopped vehicle has no time estimate. A signal displays its current phase and the phase countdown when available. The close button restores the previous overview camera without changing the run. The user can reopen Driving View from the simulation controls. Wheel, pinch and buttons allow deeper zoom, with the vehicle as the zoom anchor while following.

The frontend interpolates along the route between confirmed progress samples and snaps to the latest confirmed state on pause, reset, or arrival. Reduced motion shows confirmed positions directly.

During a GreenWave comparison, draw the alternatives from the latest route evaluation as violet dashed overlays beneath the selected routes. Provide a collapsible list of their remaining travel time, signal waiting and congestion delay. Selecting a variant highlights its path for inspection and leaves the simulation's routing decision to the backend.

### Navigation layout during a comparison

Starting the comparison opens a full-screen driving layout on the existing basemap. A green banner at the top shows the followed vehicle's current street and the next junction or signal, with its street, distance, time estimate and signal phase when available. The camera follows the vehicle below screen center and the vehicle marker indicates its route heading. Route strokes use a light outline and distinct weights and patterns so the routes remain distinguishable when they overlap. A compact dark panel at the bottom keeps the vehicle selector, exit button, alternatives and simulation controls available. The full comparison metrics can be expanded during driving.

Show the estimated time gain or loss for each GreenWave alternative relative to the selected GreenWave route from the same evaluation. Use the rounded difference of the remaining travel time estimates, with a similar-time label at zero. The bottom panel summarizes the highlighted alternative, or the alternative with the lowest estimated remaining time when none is highlighted. Do not report a forecast without a selected route and an alternative. The measured comparison result remains separate and continues to require both runs to arrive. Highlighting and closing the driving view preserve the active simulation.

Verify the live banner, route contrast, overview restoration and panel controls at mobile and desktop sizes. Test positive, negative, zero and unavailable forecast differences. No backend contract, dependency or migration is required.

### Heading and decision explanations

In Driving View, keep the followed vehicle heading up by rotating the map around the vehicle. Keep interface controls upright and restore the north-up overview on exit. Alternatives distinguish estimated time from routing score and switching threshold. Show recent removal explanations reported by the backend even when no alternatives remain.

The example button loads the versioned Gdańsk demonstration configuration and lets the user start it with the usual controls. After both vehicles arrive, show the signed GreenWave differences for time, estimated fuel and stops above the detailed results. Verify the heading and compare projected and realized times on short signal, congested and long runs.

The driving banner shows the followed vehicle's current road speed limit in km/h in a sign separate from its actual speed. Hide the sign for older snapshots with no limit.

The driving camera eases toward route heading on the shortest angular arc using real frame time. Reduced-motion preferences apply. Recent route decisions remain readable for at least eight wall-clock seconds after first reception, independently of playback rate, and are cleared on reset or trip replacement. Center the overview panel, driving dock and banner with equal mobile gutters.

The Gdańsk picker offers a short Kołobrzeska–Pomorska example and a separate rush-hour Strzyża–Drzewieckiego example based on the reproduced comparison. It searches a bundled, partial OpenStreetMap address index by street and house number. An address places the map pin and uses the existing reachable road access selection. Coverage depends on imported addresses.

Anchor the playback rate menu to its trigger in the overview and Driving View. Open below the trigger when space permits and above it near the bottom edge. Keep the options within the viewport and above the scrollable panel. Support keyboard focus, Escape and outside dismissal.
