# AGENTS.md

AfnaiHisab — Splitwise-style expense splitter evolving into a multipurpose accounting app. KMP learning project: Ktor backend, shared Kotlin `core` module, Next.js web (Phase 1), Android/iOS via KMP (Phase 3/4).

## Where to look before making changes
- `docs/STATUS.md` — current phase and what's actually done
- `docs/PLAN.md` — phased roadmap, "done when" criteria per phase
- `docs/adr/` — one file per architectural/process decision, with rationale (read before contradicting one)
- `docs/FEATURES.md` — scoped feature list per phase
- `docs/specs/<feature>.md` — per-feature acceptance criteria (ADR-0016), write one before implementing a feature
- `docs/EXPERT_GUIDELINES.md` — this project's own distilled expert-level standard (Kotlin idioms, domain-layer purity, testing discipline, Ktor conventions, build hygiene, money-math correctness), earned from a real best-practice audit — read before writing `core`/`server` code, not just before reviewing it. Enforced by the `kotlin-expert-review` skill.

## Module boundaries (ADR-0001) — do not violate
- `core/` — domain logic, data layer, validation, MVI presentation state. Framework-agnostic. This is the load-bearing module: business logic here is shared by server + Android + iOS.
- `server/` — Ktor backend. Business rules live in `core`, never in route handlers.
- `web/` — Next.js. Talks to `server` over HTTP; not a KMP target (ADR-0003).
- `app/androidApp/`, `app/iosApp/` — Phase 3/4, consume `core` directly.

## Build & test
- `./gradlew build` — compiles `core` + `server`
- `./gradlew test` — runs `commonTest` (domain logic, ADR-0009) + server integration tests
- `cd web && npm run dev` — Next.js dev server
- CI blocks merge on failing tests (ADR-0017) — not advisory.

## Conventions
- Kotlin: official Kotlin style guide.
- Commits: Conventional Commits, referencing the spec/ADR a change implements (ADR-0017).
- Dependencies: pinned versions (Gradle version catalog, committed lockfiles) — no floating ranges (ADR-0017).
- Split/balance domain logic: exhaustively unit-tested in `commonTest` before any UI consumes it (ADR-0009).

## Never do
- Put business logic in `server` route handlers (ADR-0001).
- Hardcode secrets — `.env` locally, platform secret store in prod (ADR-0015).
- Hard-delete user data — anonymize instead (ADR-0014).
- Silently change the audit log's append-only guarantee (ADR-0012).
- Merge changes to auth, settle-up money math, deletion logic, or the audit log without explicit human review (ADR-0017) — these lanes are never auto-approved regardless of how the change was generated.

## Model
Planned and reviewed using Claude Sonnet 5 (`claude-sonnet-5`) via Claude Code.
