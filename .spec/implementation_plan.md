# SDD implementation plan

Status: stage 2 completed and verified. Subsequent stages remain planned.

1. Review the MVP and open decisions; agree on map scope and algorithm parameters in the specification.
2. Create separate backend/frontend skeletons, pin compatible dependencies, configure CORS and document local setup.
3. Implement SQLite migrations and saved-trip CRUD, including a simple UI. Verify UI → REST → database operations.
4. Prepare a small test graph and Gdańsk extract; validate directions, geometry and attribution.
5. Implement the clock, simulated signals, vehicle physics and A*. Verify invariants and hand-calculated cases.
6. Add the GreenWave heuristic; verify restricted knowledge and equal comparison conditions.
7. Integrate map, controls, polling and results; check errors, reset and reproducibility.
8. Complete README, factual AI report and GIF/MP4 demonstration. Verify setup from a clean checkout and the Xelerit checklist.

Before each stage, read relevant specifications, identify gaps and present affected files, scope and checks. Use the [SDD workflow](sdd_workflow.md): Interview Mode, a concrete plan and its acceptance. A direct request to execute an accepted plan is sufficient authorization. This roadmap alone does not authorize implementing the entire application.

Verify and pin dependencies before using them. After implementation, review the diff, relevant tests and documentation consistency. Keep the solution within a realistic, small MVP.

Stage 2 delivered: Java/React skeletons, pinned manifests and npm lockfile, health endpoint, explicit configurable CORS, connection/retry UI and local setup guide. Maven verification passed 3 tests; frontend typecheck/build and browser connection/failure/recovery checks passed. No stage-3 features were implemented.
