# GreenWave SDD workflow

Status: process adopted from the user's instruction. Functional v0.2 documents remain drafts for review. Documentation and code comments are in English.

## 1. Specification and Interview Mode

Read the specification index, recruitment requirements, architecture and relevant feature document. Check models, contracts, invariants, edge cases and concurrency. Record gaps in decisions.md. Ask up to three concrete questions before planning when answers materially affect behaviour; explicitly label minor assumptions.

Each feature document contains its goal, model, business invariants, API contract, scope and Given–When–Then criteria. Link shared definitions instead of creating competing sources of truth.

## 2. Plan and boundaries

List affected files/components, migrations, dependencies, tests and exclusions. Do not change the agreed stack or requirements independently. Begin implementation after acceptance of the concrete plan; a direct instruction to execute it counts as acceptance. A request for a specification or plan alone is not a coding request.

## 3. Implementation and tests

Update the specification before implementing an accepted behaviour change, then update code and relevant tests. Workshop examples use Django/DRF, but GreenWave uses appropriate Java/Spring and React tests. Do not implement EventHub, PromoCode or WaitlistEntry.

Plan at least three meaningful automated tests per significant feature: success, validation/edge case, and an invariant or conflict. This adapts the workshop process; it is not an additional company requirement. Tests may cover several criteria and must not mirror implementation details.

## 4. Review and Git

Use small tasks on dedicated branches when a Git repository exists. Inspect git status and diffs for specification, code and tests. Run suitable checks and present actual results for human review. Passing tests do not prove the absence of all defects. Without Git, compare files locally and report the missing history. Commit and publish only within the requested scope.

## 5. Manual changes and spec drift

After a user's manual correction, inspect the current code, identify the behaviour change, synchronize the specification and add relevant regression coverage. Check compatibility. Do not revert the correction without understanding and resolving a conflict.

## 6. Reporting

Record actual gaps, human decisions, manual fixes, diff review and command results in docs/sdd_report.md. Add a successful-test screenshot only after real execution. Do not invent a manual intervention to fill a template. README contains a concise AI-use explanation; the report provides details.

## Feature Definition of Done

- Accepted requirements and plan; specification matches behaviour.
- Complete implementation within scope; API, UI and persistence agree.
- Acceptance criteria verified by appropriate checks, with actual results recorded.
- Diff reviewed, limitations disclosed and unrelated changes avoided.
- Setup documentation and AI report updated where relevant.

Submission additionally requires the Xelerit checklist. Documentation alone does not constitute a working MVP.
