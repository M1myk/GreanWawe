# AI assistance and SDD report

## 2026-11-10 — requirements and specification

ChatGPT/Codex helped organize the GreenWave concept and draft specifications for backend, frontend, database and shared contracts. After the original Xelerit assignment was provided, saved-trip CRUD and a submission checklist were added.

Numerical defaults and the detailed heuristic remain proposals. No application implementation or application tests have been completed.

## SDD review and instructions

At the user's request, AGENTS.md now defines engineering/architecture/mentoring responsibilities, evidence-based debugging, quality boundaries and the SDD process: gap analysis, plan, acceptance, implementation/tests, review and synchronization.

Identified gaps included missing feature-level models/contracts/Given–When–Then criteria, unclear approval status, missing Git history and missing OpenAPI. Seven feature specifications and explicit status rules were added. Git setup and OpenAPI remain pending.

A physics conflict was identified: a vehicle near the line may be unable to obey a no-entry-from-YELLOW rule within its braking limit. It is recorded in decisions.md and simulation rules; no business decision was made on the user's behalf.

At the previous documentation review, local links and required sections were checked, including at least three GWT scenarios per feature. Changes were compared with a local before-copy. git status confirmed that this folder was not a Git repository, so no git diff, branch or commit was performed.

Manual user code fix: none, because application code does not exist yet. Application test output and successful-test screenshots: not available. Add actual commands, results and evidence after implementation rather than inventing workshop examples.

## Signal simulation and repository privacy

Confirmed that phases, cycles and pedestrian requests are fully simulated, without live ITS data. Added a common .gitignore for Java, Node/Vite, local SQLite files, environment settings and private conversation archives. Removed private conversation links and public-specification dependencies on ignored notes. Agent instructions and the factual AI report remain intended for version control.

The ignore rules were checked against 17 ignored/retained path examples in a temporary Git repository. The conversation export remained valid JSON and no private conversation links were found in project Markdown/JSON at that check.

## English documentation and root README

The user requested English for all project documentation and code comments. Updated the agent instruction, translated maintained project documentation and added a root README describing the idea, planned layers, current status, SDD stages and actual versus planned AI assistance. Original private conversation exports remain local historical records, not maintained project documentation.

Further report entries must describe real work, human decisions, manual interventions, diff reviews and executed checks. Keep the short AI-use section in README consistent with this report.

## 2026-11-10 — stage 2: executable project setup

The user authorized the described setup stage. AI read current specifications and environment, created a dedicated chore/project-setup branch, recorded a bounded specification, then implemented Java/Spring Boot and React/Vite skeletons. The backend provides a typed health response and configurable explicit-origin CORS. The frontend checks the actual cross-origin API, times out safely, validates the response and offers retry without pretending the simulation exists.

Dependency versions were checked through the official package registry and compatibility documentation. Existing environment: Java 21.0.11, Maven 3.9.16, Node 24.14.1 and npm 11.11.0. No system-wide installation, database, CRUD or simulation was added.

Verification performed:

- `mvn verify`: BUILD SUCCESS; 3 HTTP integration tests, zero failures/errors/skips. Tests use a real embedded server and cover health JSON, configured custom-origin preflight and denied unapproved origin.
- `npm install` and `npm run build`: successful dependency installation, strict TypeScript check and Vite production build. npm reported zero vulnerabilities at installation time.
- Chrome at localhost:5173 displayed Connected to the GreenWave API. After stopping the owned API process and clicking Retry, it displayed API unavailable. Restarting the API and retrying restored Connected.
- Local server execution and dependency downloads required sandbox escalation; these were allowed. No automatic approval rejection occurred.

README now contains setup and verification commands and distinguishes completed setup from planned business features. The yellow-light modelling conflict is still open and unrelated to this stage. No manual user code correction occurred; no commit or publication was performed.

Final setup review: npm ci --offline followed by npm run build also passed from frontend/, confirming lockfile installation. Documentation links and manifest/lockfile consistency passed. git diff --check passed; generated build/dependency folders are ignored and private archives are not tracked. One attempted npm ci from the repository root failed because package.json is intentionally inside frontend/; it was rerun successfully in the correct directory.

## 2026-11-10 — stage 3: layered saved-trip CRUD

The user authorized the next stage with full domain, DTO, entity, service, repository and controller structure. Before implementation, a concrete plan and dependency list were added to features/trip_presets.md. The user explicitly approved a synthetic four-node scenario, deferring the Gdańsk extract to stage 4.

Implemented immutable domain values, separate request/response DTOs, an explicit mapper, JPA entity/repository, transactional service, controllers, structured exceptions and central advice. Added read-only scenario layers and a recursive directed-reachability query. Flyway migrations create the minimal reference schema and trip_preset, then insert a clearly labelled synthetic fixture. Hibernate schema updates are disabled. JDBC bundles SQLite; no separate database installation is required.

React now provides creation, paginated reading, editing, deletion confirmation, numeric configuration, field-level feedback, conflict recovery and cancellation/timeouts. Editing failures preserve form values. OpenAPI documents only implemented endpoints; simulation remains a draft in the broader contract.

### Findings and corrections

Initial CRUD tests exposed a nullable optimistic-version getter unboxing before insert. Changed the entity getter to retain nullable Long during new-entity detection; public responses still expose one-based versions. Reran the tests successfully. Tightened JSON parsing for fractional integer fields and unknown properties. Normalized names in the service and used fixed-precision UTC timestamps for stable ordering. Avoided exposing entities or domain configuration objects as HTTP responses.

A test JAR was rebuilt while its older instance was running, causing a class-loading error during that old instance's shutdown. Restarted with the final artifact and verified persistence; README now instructs stopping a running JAR before rebuilding. Commands accidentally invoked from the repository root were rerun from the correct backend/frontend directories (or with explicit project paths). No application failures were concealed.

### Actual verification

- Maven verify: 10 tests, zero failures/errors/skips (3 existing health/CORS and 7 new CRUD/scenario/database integration tests). Coverage includes nested validation, malformed/unknown JSON, integer constraints, unreachable and identical endpoints, pagination, concurrent/stale edits, FK enforcement, migration history and reopening a file database.
- Frontend typecheck and Vite production build passed after the final UI changes.
- Chrome on a separate test origin created and edited a trip, displayed version 2, rejected isolated node n4 while preserving the form, and retrieved the valid saved trip after page reload and backend restart.
- DELETE of the owned disposable test trip was verified through HTTP; the empty list was then confirmed in Chrome. The browser's final deletion-confirmation action was not exercised.
- Existing user servers on 8080/5173 were not stopped. Browser checks used 8081/5174 and an isolated temporary SQLite file.
- Documentation links, OpenAPI internal references and git diff --check passed. No external OpenAPI conformance validator or frontend automation framework was installed; do not present structural reference checks as full schema conformance testing.

Work is on feat/trip-presets. No commit, push, publication or simulation implementation was performed. PRE-03's future comparison-copy behaviour cannot yet be exercised because comparisons are not implemented. Manual user code intervention and a successful-test screenshot were not supplied; this report records text test results without inventing either artifact.

## 2026-11-10 — stage 4 road network

The user authorized stage 4 after sharing a reference map. AI selected an approximate rectangular boundary, documented the plan, and combined smaller OpenStreetMap API extracts after the full request failed. The importer produced a versioned Flyway migration with 6,958 nodes and 11,410 directed edges. The API exposes network versions and geometry. React renders the graph with labels, attribution, and endpoint selection. No new package dependencies were required.

Maven verification passed 12 HTTP integration tests. The offline importer passed two tests for reversed one-way direction and missing references. SQLite foreign key validation returned no violations. The frontend typecheck and production build passed. In the browser, both map clicks populated the endpoint selectors and a Gdańsk trip was saved through the form into a disposable SQLite database.

The data provenance document records the approximate bounds, the selected road classes, the default speed for missing limits, and exclusions such as turn restrictions. Signal placement and simulation belong to later stages.

## Full-screen map picker

At the user's request, AI made the Gdańsk road map the landing view, added street search and endpoint pins, and moved named preset management into a dialog. The existing API and storage remain in use. The frontend production build and git diff check passed. In the browser, both street searches selected their endpoints, two pins rendered, the SVG matched the viewport height, and the saved-trip dialog opened and closed.

## Map pin interactions and yellow-light decision

AI added pointer dragging, independent clear buttons, and an explicit active endpoint indicator. The frontend build and diff check passed. A desktop browser check verified moving both pins, retaining the original point after an off-road drop, and clearing B without removing A. Touch uses pointer events and was not tested on a physical phone. The user approved a 30 m threshold for yellow-light entry. The rule is recorded for stage 5.

## Wider map and coverage overlay

AI added visible OpenStreetMap tiles, zoom controls and a coverage overlay around the existing graph. The frontend build and diff check passed. A browser check confirmed that road labels, greenery, and the coastline rendered, that zoom in and out changed and restored the viewport, and that street selection still displayed a pin. The blue circle approximates coverage and the thin rectangle marks the imported graph bounds.

## Map navigation gestures

AI added background panning, cursor-anchored wheel zoom, two-pointer pinch zoom, and wider endpoint controls on large displays. The production build and diff check passed. Desktop browser checks confirmed dragging changed the view without adding a pin, wheel zoom changed the scale, and the panel measured 440 px on the test display. Touch gestures were not exercised on a physical phone.

## Free pins and car access

The full-screen picker preserves arbitrary pin locations within the coverage circle and shows a distinct car access marker for the nearest eligible graph node. Directed reachability accounts for the other selected endpoint. Saved-trip node IDs remain compatible. The production build, diff check and four direct checks of the access-node algorithm passed. Parking availability and pedestrian movement are outside the simulation scope.

## Parking and residential access

AI generated Gdańsk v2 from the existing extracts, added access filters to the importer and selected v2 for new trips. The importer tests covered a one-way parking aisle, a private road, a gate and a permissive driveway. Three importer tests, 12 backend tests, the frontend build and SQLite foreign key checks passed. The prior graph remains available for saved v1 presets.

## Eastern road connections and route preview

AI identified that the previous eastern boundary cut off Łozy. The v3 migration adds the missing eastern roads, and the map previews a directed car route between the selected access nodes. The frontend build and 14 backend tests passed. The regression test checks that the route from Łozy to Aleksandra Zelwerowicza crosses the former boundary and that every geometry segment corresponds to a directed graph edge. API tests also cover missing and disconnected endpoints. The updated line was not checked in a browser.

## Stage 5 — baseline simulation

Codex drafted the feature contract, adapted the route search to A*, implemented the vehicle and signal models in Java and added the session lifecycle and map controls. The runtime defaults and simulated signal placement are recorded in the baseline specification. No real signal data, database migration or new dependency was added.

The first vehicle tests showed that a continuous stopping-distance estimate underestimated the distance of the last partial braking increment when movement is sampled every 0.1 s. The stopping envelope now accounts for that final full tick. The tests check red-light entry, acceleration and braking bounds, downstream speed limits, arrival, seed replay and the yellow threshold. The HTTP test covers creation, ticking, pause, resume, reset, command replay and deletion.

The browser check ran Łozy → Gdańska on the imported graph, paused and resumed at 5×, reached the destination in 109.3 simulation seconds with one stop and 6.3 seconds stopped, reset to zero and cleared the run when the destination changed. Those numbers describe the synthetic signal scenario, not measured traffic.

Final verification: Maven verify passed all 19 backend tests, the frontend production build passed with TypeScript checking, and git diff --check found no whitespace issues.

## Baseline driving view

Codex added a closer follow camera, interpolated vehicle positions along confirmed route progress, and exposed the current street and upcoming junction or simulated signal in the baseline snapshot. The view can close and reopen during the same run. The arrival estimate uses current speed, and signal phase changes have a separate countdown where known.

Maven verify passed 21 tests and the frontend production build passed TypeScript checking. In the browser, Łozy to Gdańska opened Driving View, the car moved while centered, deeper zoom worked, and the overview restored without pausing the run. Driving View reopened from the overview controls.

## Stage 6 — GreenWave navigation and maneuver limits

Codex implemented the GreenWave planner in the existing single-run API and added shared maneuver speed limits. The planner receives nearby signal observations and can update the route beyond the committed edge. The fixed signal speed forecast uses the same acceleration, braking and maneuver constraints as the active vehicle. Per-node pedestrian timelines are reproducible regardless of route or observation order.

An imported Gdańsk route exposed a late yellow decision when the vehicle braked for a turn. The engine now forecasts the crossing before committing to yellow entry, using the maneuver constraints and a conservative downstream stop assumption. Once committed, it holds the route and suppresses advisory slowdown until the line is crossed.

Maven verify passed 27 tests, including 18 imported origin–destination pairs driven with both algorithms, the three maneuver caps, the fixed-light speed recommendation, replanning with a preserved current edge, pedestrian timeline isolation and HTTP algorithm selection. The frontend production build and git diff --check passed.

## Stage 7 — paired comparison

Codex integrated both engines under one comparison clock, added revision-aware controls and a single atomic SQLite row for the terminal snapshot. The map renders both routes and vehicles and can follow either one. Saved trip settings can populate the comparison configuration.

Maven verify passed 30 tests, including shared timing, replay at 1× and 5×, frozen arrival metrics, timeout, stale commands, stored result retrieval and reset. The frontend production build and git diff --check passed. The browser run from Łozy to Gdańska exercised both vehicles, follow switching, pause, playback speed and the final table. A* arrived in 110.2 s and GreenWave in 109.2 s. A backend restart using the same temporary SQLite database returned the identical completed result and reported BACKEND_RESTARTED for an interrupted comparison.

## Arrival-time routing

Codex replaced fixed signal penalties with route-specific arrival estimates at directed graph vertices. Waiting at each observed signal shifts later arrival estimates. Equal estimated arrivals favor lower predicted waiting when comparing candidate routes. The committed edge and braking approach remain protected.

Maven verify passed 33 tests, including the 30 s versus 40 s example, a delay that changes the phase at a second signal, and the signal at the end of the committed edge. The imported Gdańsk route regression and git diff --check also passed.

## Green-window acceleration and estimated gasoline use

Codex added a GreenWave-only target speed to reach the current green, with a simulation allowance of at most 20 km/h above the ordinary speed cap on the current segment. The shared vehicle engine still enforces acceleration, braking, junction and turn limits. The gasoline estimate uses a Mazda SKYACTIV-G 2.0 L reference model, with EPA engine and road-load data and a no-load idle figure reported by ORNL.

Maven verify passed 38 tests. The tests cover feasible boost on 30 and 50 km/h roads, unreachable green, A* isolation, fuel consumption through stop and relaunch, legacy result compatibility and imported Gdańsk routes. The frontend production build passed. On the browser run from Łozy to Gdańska the comparison displayed 93.5 mL for A* and 73.4 mL for GreenWave.

## Wider GreenWave planning and coasting

Codex raised the default directed observation range to 1,500 m and added up to ten distinct route candidates. The planner reuses structural alternatives while evaluating fresh signal observations. The approach controller uses the full available green-window allowance across compatible segments. Where waiting is expected, it approaches at ordinary speed and then coasts within the available distance. Instant fuel flow and the last route candidate count are visible in the comparison.

The imported-network smoke test exposed a braking-envelope boundary where the final fractional braking tick required more distance than continuous stopping distance predicted. Adding the discrete margin to the lookahead resolved the regression. Maven verify passed 40 tests, including ten distinct routes, extended observation, acceleration across split road segments and zero fuel flow after reaching cruising speed. The frontend production build passed. In the browser on Łozy to Gdańska, the panel displayed eight alternatives at departure and 0 L/h for GreenWave while coasting.

## Simulated traffic modes

Codex added three selectable traffic modes, deterministic speed caps shared by both vehicles, and a map layer that uses the same network, mode and seed as the comparison. A* follows its original route under the shared caps. GreenWave uses observed slowdowns when scoring routes. Saved presets retain the mode and missing modes default to free flow.

Maven verify passed 44 tests. Coverage includes deterministic and nested profiles, shared braking limits, routing around observed traffic and saved configuration round trips. The frontend production build passed. In the browser, switching modes toggled the orange and red overlays and a peak-hour comparison started with both cars and the selected mode shown in the panel.

## Stop-and-go traffic

Codex replaced the constant congested speed with a deterministic shared stop-and-go corridor cycle. GreenWave estimates travel time with the mean corridor speed. Both engines follow the same braking and acceleration bounds, and gasoline consumption is integrated from the actual motion and idle time.

Maven verify passed 45 tests, including repeated queue stops, both engines producing the same motion on an identical route, and over 30% higher estimated gasoline use on the same 800 m test road in the heaviest queue. The frontend production build passed. In a browser run using the synthetic peak-hour route, both vehicles alternated between standstill and 10 km/h, and their fuel counters increased.

## Transient gasoline use and maneuver-aware routing

Codex added the EPA Mazda 2.0 acceleration transient to the fuel model using an approximate engine-speed proxy. The comparison shows the transient contribution within total fuel. The original OSM extracts supplied the available motorcar turn restrictions and roundabout metadata. Both route searches now carry turn state, and GreenWave retains the committed prefix and adds an estimated cost for significant street changes. Conditional and untagged restrictions remain outside the model.

Maven verify passed 49 tests, including imported Gdańsk journeys, committed via-way restrictions, roundabout re-entry rejection, a small detour decision and transient gasoline from launch. Five offline Python importer tests passed and regenerating V7 from the cached source extracts produced the same bytes. The frontend production build and git diff --check passed.

## Congestion-aware route selection and map alternatives

Codex traced a missing active-edge queue observation and a route switch driven by marginal projected gains. The planner now applies the queue moving fraction to the effective cap, includes the committed approach in the forecast, and adds half the congestion delay as a ranking preference. Significant turns and side-road transitions contribute estimated time. The routing margin is five seconds or five percent, whichever is greater, in the cases defined by the navigation specification. These values are simulation heuristics.

The comparison API publishes the distinct paths from the latest evaluation and their estimated remaining time, signal wait and congestion delay. React draws the alternatives in violet and lets the operator highlight one. Added regression checks for active-edge queue observations, the congestion bypass, path publication, a marginal side-road gain and the comparison response.

Maven test passed all 51 tests, including 18 sampled Gdańsk journeys and HTTP serialization of the route alternatives. The frontend production build, OpenAPI JSON parsing and git diff --check passed. The new overlays were not checked in a browser during this change.

## Driving view and alternative time differences

Codex updated the driving layout, route outlines and heading marker on the existing basemap. The UI compares each alternative's remaining-time estimate with the selected GreenWave route from the same snapshot. This estimate is shown separately from the measured comparison result.

Three Node tests passed for faster, slower, similar and unavailable estimates. The frontend production build and git diff --check passed. In the local browser, a Kołobrzeska-to-Pomorska run showed the street, next junction and route overlays at desktop size and at 390 × 844. Highlighting a route updated the bottom panel. Closing and reopening driving view preserved the paused comparison.

## Decision explanations and heading-up driving view

Codex added backend switching diagnostics and temporary explanations for previously faster options that disappear. React shows those reasons, a demonstration preset, signed final differences in travel time, estimated fuel and stop count, and a heading-up driving map. The local browser confirmed that the example starts, the camera angle changes as the vehicle turns, the forecast-loss message appears, and exiting returns to north-up with the comparison still paused.

Three deterministic runs on the imported Gdańsk network used seed 42, the 1,500 m observation range and a one-second decision interval. Initial ETA is the selected GreenWave forecast in the first snapshot. Actual time is the simulated arrival time.

| Run | Initial GreenWave ETA | GreenWave actual | A* actual | GreenWave mid-drive reroutes |
| --- | ---: | ---: | ---: | ---: |
| Kołobrzeska to Pomorska, free flow | 276.6 s | 374.0 s | 421.4 s | 0 |
| Demonstration, ordinary traffic, 7.9 km | 778.2 s | 1,185.3 s | 1,185.3 s | 0 |
| Demonstration endpoints, free flow, 7.9 km | 778.2 s | 885.4 s | 885.3 s | 0 |

The initial forecast underestimated all three journeys. Beyond the observation horizon, the estimator uses free flow and includes no unobserved signal wait. That is consistent with both long runs starting at the same ETA and finishing at different times. The ordinary-traffic run reproduced forecast loss at 61.1 s and a committed turn at 178.1 s. The explanations use the existing routing rule and do not recalibrate the switching threshold.

Maven passed 52 tests, including the replay regression for both removal reasons, the switching threshold and notice expiry. Three frontend forecast tests, the production build, OpenAPI JSON parsing and git diff --check passed.

## Trip limit, playback and signal timing

The default run limit is 3,000 simulation seconds, and both playback APIs accept 10×. Both signal programs use a three-second yellow phase. The fixed program retains its sixty-second cycle and thirty-second green phase, giving it twenty-seven seconds of red. The driving banner displays the current segment's speed limit from the road network independently of actual vehicle speed.

Replaying the ordinary-traffic demonstration with the new signal timing showed the Chłopska alternative becoming unavailable at 176.1 s. The earlier forecast-loss event documented above did not recur in this run. The regression now checks the faster alternative immediately before its turn is committed and checks that the notice expires.

Maven passed all 55 tests, including the default timeout, both yellow phases, 10× command acceptance and playback invariance, and the speed limit update on entering another segment. The frontend build, three forecast tests, OpenAPI JSON parsing and git diff --check passed. The new sign was not inspected in the browser.

## Camera, decision notices, addresses and launch fuel

The camera now eases toward the route heading on the shortest arc using frame time. Recent decision messages are retained in the browser for eight wall-clock seconds after first reception, including at 10×. The short example runs from Kołobrzeska to Pomorska. The rush-hour example reuses the exact road endpoints, traffic mode and seed from the comparison with arrival times of 1,960.5 s and 1,404.3 s.

The revised gasoline model charges extra for low-speed acceleration. Replaying the rush-hour example produced 1,082.0 mL for A* over 7.29 km and 1,449.0 mL for GreenWave over 12.51 km. A* stopped 89 times and GreenWave 14 times. The low-speed and transient correction totals were 202.7 mL and 165.6 mL, respectively. The difference in total fuel fell from 388.6 mL to about 367 mL. The low-speed efficiency is a modeling assumption.

The browser confirmed address selection, the rush-hour example, heading changes at 10×, a visible decision notice and equal 14 px side margins in the overview and driving mode at 390 × 844. The Maven suite, frontend build, three forecast tests, OpenAPI JSON parsing and git diff --check passed.

The playback selector now opens an anchored browser popover. Browser checks verified placement 5 px above its trigger at 390 × 844 and 5 px below it on desktop, selection of 10×, and Escape dismissing the menu while Driving View remains open. The frontend production build passed.

## Gemini saved-time examples

Codex implemented asynchronous Gemini prefetch, a V8 SQLite migration for duration bands and duplicate history, and a short Polish sentence under the final comparison. The full Maven suite verified prefetch during the A* run with a mocked provider, immediate local fallback for a pending response, threshold and band boundaries, duplicate handling, rotation, persisted reads and reset. The frontend production build and existing forecast tests passed. A live Google request was not made because the development process had no Gemini key.

## Docker Compose setup

Codex added separate images for the backend and the built frontend. Compose serves the browser and proxied API on port 3000. The root environment file supplies runtime configuration and passes the Gemini key only to the backend. The existing SQLite database was copied into the Docker data directory using the SQLite backup API.

Both images built and the services started through Compose. The frontend HTML, health endpoint and saved-trip API responded through the published frontend port. The two saved presets remained accessible after a backend restart. The environment check confirmed that the backend received a Gemini key and the frontend did not. This check did not call Gemini.


## Stage 8 clean-source verification

Codex expanded the README with the application purpose, use cases, feature inventory and simulation limitations. The demo uses a separate browser profile and a new local SQLite database. Playwright and the video encoder were temporary recording tools outside the project dependencies.

A Git export of implementation commit `efda14a` started through Docker Compose with the example environment, a new SQLite directory and an isolated host port. The same-origin API health endpoint returned `UP`. Maven passed 58 tests. The frontend passed `npm ci`, the production build with type checking, and all three route forecast tests.

The browser recording shows saved-trip creation, editing and deletion, then a completed Kołobrzeska–Pomorska comparison with the GreenWave driving view and the final metrics. [Watch the MP4](greenwave-demo.mp4).

Two separate Docker instances ran the same synthetic rush-hour comparison with seed 42. Both persisted a 145.9 s GreenWave saving. The instance with the configured Gemini key returned a generated Polish example with source `GEMINI`. The instance with the example environment returned a SQLite example with source `LOCAL`. The browser demo uses the instance without a key.
