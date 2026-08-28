# Working process — minimal-token development workflow

Purpose: keep the main conversation thread cheap by pushing bulk work (implementation, search, fixing, review) into subagents that return only a diff or a verdict, not their raw work. This doc is what a session should read first instead of re-deriving "how do we work" every time.

**This doc vs. `AGENTS.md`**: `AGENTS.md` (repo root) is the cross-tool standard — any AI coding tool (Codex, Cursor, Copilot, Gemini CLI, Claude Code) reads it for build commands, conventions, and hard constraints. This doc is Claude-Code-session-specific: which subagent to delegate which task shape to, to keep *this* session's token usage low. Keep `AGENTS.md` tool-agnostic and under ~150 lines; put Claude-specific delegation strategy here, not there.

## Session start ritual (read these three, nothing else, before doing anything)
1. `docs/STATUS.md` — where the project actually stands right now
2. `docs/PLAN.md` — the phase we're in and its "done when" criteria
3. `docs/adr/*.md` — only if a decision in this session touches one of them

Do not re-scan the whole repo to "get oriented" — STATUS.md exists so that's unnecessary.

## Delegation rules by task shape

| Task shape | Use | Why |
|---|---|---|
| `core`/`server` implementation (any size) | `afnaihisab-backend` (`.claude/agents/`) | Project-scoped agent, pre-loaded with module boundaries, JDK path, TDD discipline, and which guideline docs to read — the delegation prompt only needs to state the task, not re-brief the whole project every time (this is what `smh:executor`/`smh:executor-high` required, at real token cost, before this agent existed) |
| `web/` implementation (any size) | `afnaihisab-web` (`.claude/agents/`) | Same idea, pre-loaded with ADR-0020's client-side-fetch constraint and the Next.js/React guidelines |
| Single-file trivial edit (typo, one-line config tweak) | Do it inline | Delegation itself isn't free — an agent call for a two-line change costs more than it saves |
| Compile/type errors after a change | `smh:build-fixer` | Iterating on build errors directly in the main thread burns tokens on repeated error output; this agent loops until green and reports back |
| "Where is X / which files reference Y" | `Explore` | Read-only, fast, doesn't burn a full agent's context budget on a search |
| After finishing a feature slice, before moving to the next | `/kotlin-expert-review` or `/web-expert-review` | Project-specific gates (see below) — catches issues while the diff is small, not after three more features stack on top |
| Genuinely ambiguous multi-step research | `Agent` with `subagent_type: "fork"` in parallel | Keeps raw search/tool output out of the main thread; only the synthesis lands here |

Default posture: **the main thread plans and reviews summaries; subagents touch files and run commands.** `smh:executor`/`smh:executor-high`/`smh:tdd-guide` still exist as fallbacks if a task doesn't fit either project agent's scope, but for anything in `core`/`server`/`web/`, the project-scoped agents are cheaper and more consistent by default.

## Human-review-required lanes (ADR-0017)

Regardless of which agent generated the diff, changes touching these areas get flagged explicitly during `smh:code-reviewer` / `/kotlin-expert-review` and are never auto-approved on an agent's say-so alone:
- Auth/token handling (ADR-0008, ADR-0011, ADR-0013)
- Balance/settle-up money math (ADR-0007) — silently wrong financial data is the worst failure mode this app has
- Deletion/anonymization logic (ADR-0014)
- The audit log's append-only guarantee (ADR-0012)
- Ledger-membership authorization checks on any route (ADR-0024) — a missing check is an IDOR vulnerability, invisible in a happy-path review
- Idempotency-key handling on mutating financial endpoints (ADR-0023) — a bug here reintroduces exactly the duplicate-record risk the feature exists to prevent

Everything else follows the delegation table above without extra ceremony.

## Skills already available and when they apply
- `/sc:implement`, `/sc:build`, `/sc:test`, `/sc:troubleshoot` — SuperClaude command set already configured; use over ad-hoc prompting for their named purpose (implementation, build, test, debug) since they carry their own workflow discipline.
- `/sc:design` — for any API or schema design pass before implementation (e.g., before Phase 1's Expense/Split endpoints).
- `/code-review` — generic correctness/simplification pass; run at `high` effort at the end of each phase, `low`/`medium` for smaller mid-phase diffs.
- `/kotlin-expert-review` — this project's own, stricter, project-specific gate (`docs/EXPERT_GUIDELINES.md`, plus `docs/guidelines/exposed-koin.md` for repository-layer code) — run this, not (or in addition to) generic `/code-review`, before merging any `core`/`server` change, and always for an ADR-0017 human-review-lane diff (money math, auth, deletion, audit log).
- `/web-expert-review` — the `web/` sibling (`docs/guidelines/nextjs-react-typescript.md`) — run before merging any `web/` change, especially anything touching how it calls the backend (ADR-0020).
- `/security-review` — run once before Phase 2 deploy (auth, input handling, SQL/XSS) and again before any Phase 6 accounting data goes live.

## Token optimization policy

Three persistence mechanisms exist for this project, each with a different cost profile — using the wrong one for a given fact wastes tokens every future session, not just this one.

| Mechanism | Cost profile | What belongs here |
|---|---|---|
| Repo docs (`docs/*.md`, `AGENTS.md`, ADRs) | Zero standing cost — paid only when a session actually reads the file | All project state and decisions. Visible to *any* tool (Codex, Cursor, Copilot, Claude Code) per `AGENTS.md`'s whole design, not just this session. |
| Claude memory (`~/.claude/projects/.../memory/`) | Small standing cost (loaded into context automatically); invisible to any other tool or human reading this repo | Only facts about *the user* or *how to collaborate with them* that generalize beyond this one repo (e.g. "prefers plain questions over structured UI," "wants fresh research before technical decisions"). Never project state — `docs/STATUS.md` already serves that role, and duplicating it into memory creates two sources of truth that can silently drift. |
| Claude Code Skills (`.claude/skills/*/SKILL.md`) | The `name`+`description` is loaded into **every single turn of every session**, forever, regardless of whether it's invoked — the body is paid only on invocation | Keep the *count* of skills small (each one adds to that per-turn tax) and each `description` a single dense sentence — full detail belongs in the body (paid once, on invocation) or in a referenced doc, not in the always-loaded description. Don't fragment one review skill into several narrower ones; add depth via referenced docs (`docs/guidelines/*.md`) instead. |
| Custom Agents (`.claude/agents/*.md`) | No standing per-turn cost — the whole file's content is paid only when that agent is actually invoked | The right place for project context that's currently re-typed into every delegation prompt (module boundaries, JDK path, which docs to read, process discipline). `afnaihisab-backend`/`afnaihisab-web` exist specifically so delegation prompts can shrink to "do X," not "do X, and also here's the whole project." |

## Project-root CLAUDE.md
Created during Phase 0 scaffolding — module boundaries (ADR-0001), naming/package conventions, dev-stack commands, where tests live. Keep it current as conventions evolve; this is what stops every future session from re-deriving repo conventions from scratch.

## What NOT to do
- Don't ask a fresh (non-fork) agent to "continue the plan" — it has no context and will re-derive everything already decided here, burning tokens re-solving solved problems.
- Don't hand large multi-domain work to one big agent call when it can be a `pipeline`/parallel breakdown — but per this session's tooling, only use the `Workflow` tool if explicitly requested; otherwise sequential `smh:executor` calls per feature slice are the default.
- Don't let STATUS.md go stale — update it at the end of every work session, not "eventually."
