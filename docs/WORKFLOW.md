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
| Multi-file feature implementation within a phase | `smh:executor` (or `smh:executor-high` if it spans backend+shared+web) | Keeps the main thread from holding every file's full contents; it returns a summary + diff |
| Single-file trivial edit (typo, one function, config tweak) | `smh:executor-low` | Cheapest tier, no need for a bigger model on mechanical work |
| Compile/type errors after a change | `smh:build-fixer` | Iterating on build errors directly in the main thread burns tokens on repeated error output; this agent loops until green and reports back |
| New domain logic (split math, balance calc, debt simplification) | `smh:tdd-guide` first, then `smh:executor` | This is the highest-leverage, highest-risk code in the whole project (per ADR-0001) — write tests first, deliberately, not as an afterthought |
| "Where is X / which files reference Y" | `Explore` | Read-only, fast, doesn't burn a full agent's context budget on a search |
| After finishing a feature slice, before moving to the next | `smh:code-reviewer` (or `-low` for a small diff) | Catches issues while the diff is small and cheap to review, not after three more features stack on top |
| Genuinely ambiguous multi-step research (like this planning phase) | `Agent` with `subagent_type: "fork"` in parallel | Keeps raw search/tool output out of the main thread; only the synthesis lands here |

Default posture: **the main thread plans and reviews summaries; subagents touch files and run commands.** If a task is small enough to just do inline (one Edit call), do it inline — delegation itself isn't free, don't use an agent for a two-line change.

## Human-review-required lanes (ADR-0017)

Regardless of which agent generated the diff, changes touching these areas get flagged explicitly during `smh:code-reviewer` / `/kotlin-expert-review` and are never auto-approved on an agent's say-so alone:
- Auth/token handling (ADR-0008, ADR-0011, ADR-0013)
- Balance/settle-up money math (ADR-0007) — silently wrong financial data is the worst failure mode this app has
- Deletion/anonymization logic (ADR-0014)
- The audit log's append-only guarantee (ADR-0012)

Everything else follows the delegation table above without extra ceremony.

## Skills already available and when they apply
- `/sc:implement`, `/sc:build`, `/sc:test`, `/sc:troubleshoot` — SuperClaude command set already configured; use over ad-hoc prompting for their named purpose (implementation, build, test, debug) since they carry their own workflow discipline.
- `/sc:design` — for any API or schema design pass before implementation (e.g., before Phase 1's Expense/Split endpoints).
- `/code-review` — generic correctness/simplification pass; run at `high` effort at the end of each phase, `low`/`medium` for smaller mid-phase diffs.
- `/kotlin-expert-review` — this project's own, stricter, project-specific gate (`docs/EXPERT_GUIDELINES.md`) — run this, not (or in addition to) generic `/code-review`, before merging any `core`/`server` change, and always for an ADR-0017 human-review-lane diff (money math, auth, deletion, audit log).
- `/security-review` — run once before Phase 2 deploy (auth, input handling, SQL/XSS) and again before any Phase 6 accounting data goes live.

## Project-root CLAUDE.md (to create once code exists)
Once `core/`, `server/`, `web/` are scaffolded (Phase 0), add a project-level `CLAUDE.md` documenting: module boundaries (link to ADR-0001), naming/package conventions, how to run the dev stack locally, and where tests live. This is what stops every future session from re-deriving repo conventions from scratch — the single highest-leverage token-saver for a long-lived project.

## What NOT to do
- Don't ask a fresh (non-fork) agent to "continue the plan" — it has no context and will re-derive everything already decided here, burning tokens re-solving solved problems.
- Don't hand large multi-domain work to one big agent call when it can be a `pipeline`/parallel breakdown — but per this session's tooling, only use the `Workflow` tool if explicitly requested; otherwise sequential `smh:executor` calls per feature slice are the default.
- Don't let STATUS.md go stale — update it at the end of every work session, not "eventually."
