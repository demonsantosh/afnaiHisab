# ADR-0017: Git hygiene, CI test gate, dependency pinning, human-review lanes

## Status
Accepted

## Context
Research into AI-assisted development handoff quality converges on a consistent set of operational conventions that separate maintainable AI-assisted codebases from ones that decay: technical debt is reported to rise 30-41% after AI-tool adoption without these guardrails, with code duplication up ~48% and refactoring activity down ~60%. None of AfnaiHisab's existing ADRs establish git/commit conventions, an *enforced* (not just stated) test gate, a dependency-pinning policy, or which categories of change require deliberate human review rather than being auto-approved as "just another agent task."

## Decision

**Git/commit hygiene:**
- Conventional Commits format (`feat:`, `fix:`, `refactor:`, etc.), referencing the spec clause or ADR a commit implements per ADR-0016.
- One commit per logical layer within a feature (e.g. domain logic, then API route, then UI), not one giant commit — reviewability and bisectability.
- Squash trial-and-error commits before merging to main so history stays legible.

**CI test gate — enforced, not just stated:**
- ADR-0009 defines the test strategy; this ADR makes it a gate: CI must run `commonTest` + `server` integration tests and **block merge on failure**, not just report status. Phase 0's CI scaffolding must wire this in from the start, not add it later.

**Dependency/version pinning:**
- Gradle version catalog (`libs.versions.toml`) with pinned versions, not floating ranges.
- `web`'s `package-lock.json`/`pnpm-lock.yaml` committed, not gitignored.
- Upgrades are a deliberate, reviewed action — not automatic.

**Human-review-required lanes** — categories of change that must be explicitly reviewed by a person before merge, regardless of how the code was generated:
- Auth/token handling (ADR-0008, ADR-0011, ADR-0013)
- Balance/settle-up money math (ADR-0007) — an error here is silently wrong financial data, the worst failure mode for this app specifically
- Deletion/anonymization logic (ADR-0014) — getting this wrong either corrupts shared-ledger data or fails a real compliance request
- Anything touching the audit log's append-only guarantee (ADR-0012)

Everything else follows `docs/WORKFLOW.md`'s existing delegation table.

## Consequences
- Phase 0's CI setup task now explicitly includes wiring the merge-blocking gate, not just "compile" — a scope addition to `docs/PLAN.md` §5 Phase 0.
- The four human-review lanes are a checklist to apply during `smh:code-reviewer` passes (`docs/WORKFLOW.md`) — flag explicitly when a diff touches one of them.
- No process overhead added for everything outside these lanes — most feature work stays as lightweight as `docs/WORKFLOW.md` already describes.
