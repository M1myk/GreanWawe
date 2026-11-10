# Business invariants

| ID | Rule | Verification |
| --- | --- | --- |
| INV-01 | Planner cannot access future random events or seed | Same observations, different hidden futures |
| INV-02 | Both algorithms share physics, conditions and clock | Comparison integration |
| INV-03 | Same versions/configuration/seed yield the same result | Repeat at different playback rates |
| INV-04 | No entry during RED. YELLOW entry follows the 30 m rule | Check 30 m, distances above 30 m and the RED boundary |
| INV-05 | Movement respects speed, position and acceleration bounds | Physics ticks and edge transitions |
| INV-06 | Frontend cannot change authoritative position/decisions | Integration review and snapshots |
| INV-07 | Pause freezes time and metrics | Lifecycle test |
| INV-08 | Unreachable destinations never start simulation | API 422 |
| INV-09 | Arrived-run metrics remain frozen | Different arrival times |
| INV-10 | Results never guarantee GreenWave superiority | Negative savings and TIMEOUT |
| INV-11 | Result persistence is atomic | Transaction test |
| INV-12 | Replanning preserves the current edge | Junction-selection test |

Changing an invariant requires updating this document and assessing tests and stored historical versions. The approved YELLOW threshold is recorded in decisions.md.
