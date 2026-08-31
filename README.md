# AfnaiHisab

[![CI](https://github.com/demonsantosh/afnaiHisab/actions/workflows/ci.yml/badge.svg)](https://github.com/demonsantosh/afnaiHisab/actions/workflows/ci.yml)

A Splitwise-style expense splitter evolving into a multipurpose accounting app — a Kotlin
Multiplatform (KMP) learning project: Ktor backend, a shared Kotlin `core` module, Next.js web
(Phase 1), Android/iOS via KMP (Phase 3/4).

## Where to start

- **`AGENTS.md`** — tool-agnostic contract: module boundaries, build/test commands, conventions,
  "never do" rules.
- **`CLAUDE.md`** — the same, for Claude Code sessions.
- **`docs/INDEX.md`** — documentation map + change-impact table; check this if unsure what else
  needs updating alongside a change.
- **`docs/STATUS.md`** — current phase and what's actually done, updated every session.
- **`docs/adr/README.md`** — every architecture decision, with rationale.

## Quick start

Requires JDK 17 (`docs/TOOLING.md` has a corporate-proxy caveat if dependency resolution fails).

```bash
cp .env.example .env
cd web && npm ci && cd ..

# terminal 1 — Ktor on http://localhost:8080
./gradlew :server:run

# terminal 2 — Next.js on http://localhost:3000
cd web && npm run dev
```

Full setup and gotchas (JDK export, CORS, local database) are in `CLAUDE.md`.

## Status

Phase 1, in progress — see `docs/STATUS.md` for the current state.
