# Cross-layer acceptance criteria

Verify behaviour rather than implementation details. Feature specifications provide the detailed Given–When–Then scenarios.

| ID | Given / When / Then |
| --- | --- |
| AC-01 | Valid network / select endpoints / READY contains both routes |
| AC-02 | No directed route / prepare comparison / API 422 and readable error |
| AC-03 | READY / Start, Pause, Resume / time advances only in RUNNING |
| AC-04 | Same seed, configuration and versions / run at 1× and 10× / identical metrics |
| AC-05 | Closing signal and feasible stopping distance / approach / stop before the line when the YELLOW approach exceeds 30 m |
| AC-06 | Reachable green phase / GreenWave selects speed / limits and physics are respected |
| AC-07 | Same observation, different hidden future / planner decision / identical choice |
| AC-08 | Alternative before a junction / replan / current edge is preserved |
| AC-09 | One vehicle arrived / other continues / first vehicle metrics remain frozen |
| AC-10 | TIMEOUT / present results / no fabricated arrival time or saving percentage |
| AC-11 | Reset / restart / initial state and the same events |
| AC-12 | API unavailable / more than 2 s / animation stops and view is stale |
| AC-13 | Completed result / backend restart / persisted result is available |
| AC-14 | Trip configuration / create, read, update, delete / all operations work through UI and REST |
| AC-15 | Preset used by a comparison / delete preset / historical configuration remains |
| AC-16 | Allowed frontend origin / request and preflight / correct CORS; unapproved origins receive no CORS permission |

Final verification includes domain tests, REST/SQLite integration, frontend typecheck/build and a manual end-to-end comparison/CRUD journey. No application test execution is claimed before implementation.
