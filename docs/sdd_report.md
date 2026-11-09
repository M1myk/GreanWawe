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
