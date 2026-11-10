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
