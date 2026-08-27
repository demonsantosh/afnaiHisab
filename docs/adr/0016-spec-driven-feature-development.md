# ADR-0016: Spec-driven development — EARS acceptance criteria before implementation

## Status
Accepted

## Context
Research into disciplined AI-assisted ("vibe coding") development converges on one finding across independent sources: the single most-cited failure mode in AI-generated codebases isn't bad architecture — it's shipping code with no testable definition of "done," which is exactly what lets a feature quietly diverge from what was intended. AfnaiHisab already has ADRs (architectural decisions) and `docs/FEATURES.md` (one-line feature descriptions), but nothing sits between them: no per-feature acceptance criteria written *before* implementation starts. This is what named frameworks (GitHub Spec Kit, BMAD-METHOD, OpenSpec) converge on as Spec-Driven Development (SDD) — and its load-bearing property isn't the document, it's **bidirectional traceability**: every implementation task cites the spec clause it satisfies, so a future engineer (or agent) can answer "why does this code exist" by reading one line, not by archaeology through commit history.

## Decision
Before implementing any feature from `docs/FEATURES.md`, write a short spec file in `docs/specs/<feature-name>.md` using **EARS format** (Easy Approach to Requirements Syntax: "WHEN \<trigger\>, THE system SHALL \<response\>") for acceptance criteria, plus a short test plan. Template at `docs/specs/TEMPLATE.md`. Specs are spec-anchored — updated alongside the code they describe, not written once and abandoned. Implementation tasks/commits reference the spec clause they implement (e.g. `refs specs/equal-split.md#AC-2`).

This is lightweight, not the full seven-phase SDD pipeline (Constitution → Specify → Clarify → Plan → Tasks → Implement → Analyze) — that's overkill for a solo learning project. The one piece adopted is the part that actually prevents drift: acceptance criteria before code, referenced by the code.

## Consequences
- Adds a small step before each feature's implementation — a spec file, typically 20-40 lines, not a heavyweight process.
- `commonTest` cases (ADR-0009) should map directly to a spec's acceptance criteria — if a criterion has no corresponding test, that's visible, not silently missing.
- This is the mechanism that makes ADR-0017's CI test gate meaningful — a gate that blocks on "tests pass" only matters if the tests actually check something specific, which specs make explicit.
- Not retroactive — existing planning docs (PLAN/FEATURES/ARCHITECTURE/ADRs) aren't rewritten as specs; this applies going forward, starting with Phase 1's first feature.
