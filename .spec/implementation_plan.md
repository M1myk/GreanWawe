# SDD implementation plan

Status: stages 2–5 implemented. The GreenWave comparison remains planned.

1. Review the MVP and open decisions; agree on map scope and algorithm parameters in the specification.
2. Create separate backend/frontend skeletons, pin compatible dependencies, configure CORS and document local setup.
3. Implement SQLite migrations and saved-trip CRUD, including a simple UI. Verify UI → REST → database operations.
4. Prepare a small test graph and Gdańsk extract; validate directions, geometry and attribution.
5. Implement the clock, simulated signals, vehicle physics and A*, with a single-run map view. Verify invariants and hand-calculated cases.
6. Add the GreenWave heuristic; verify restricted knowledge and equal comparison conditions.
7. Integrate map, controls, polling and results; check errors, reset and reproducibility.
8. Complete README, factual AI report and GIF/MP4 demonstration. Verify setup from a clean checkout and the Xelerit checklist.

Before each stage, read relevant specifications, identify gaps and present affected files, scope and checks. Use the [SDD workflow](sdd_workflow.md): Interview Mode, a concrete plan and its acceptance. A direct request to execute an accepted plan is sufficient authorization. This roadmap alone does not authorize implementing the entire application.

Verify and pin dependencies before using them. After implementation, review the diff, relevant tests and documentation consistency. Keep the solution within a realistic, small MVP.

Stage 2 delivered: Java/React skeletons, pinned manifests and npm lockfile, health endpoint, explicit configurable CORS, connection/retry UI and local setup guide. Maven verification passed 3 tests; frontend typecheck/build and browser connection/failure/recovery checks passed. No stage-3 features were implemented.

Stage 3 delivered a complete working preset module and synthetic scenario, SQLite/Flyway integration, JPA entity/repository, domain and DTO models, mapper, service, controller, validation/errors and React CRUD. The user approved the synthetic fixture; Gdańsk import remains stage 4.

Stage 4 delivered the versioned Gdańsk road extract, network REST reads and map-based endpoint selection. See [road data provenance](../docs/road-network-data.md).

Stage 5 delivered one A* simulation, in-memory session controls, deterministic signal programs, vehicle motion, journey metrics and the single-run map view. See the [baseline specification](features/baseline_simulation.md). No new dependency or migration was required.
