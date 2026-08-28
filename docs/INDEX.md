# Documentation index & change-impact map

**Purpose**: this project has already drifted multiple times — a stale ADR count in three places, stale `shared/`/`backend/` references left over from a rename, a human-review-lane list enumerated in three docs where only two got updated. Each time, the fix was a one-off catch, not a fix for the *pattern*. This file is the fix for the pattern: one place listing every doc and, critically, what else to check when it changes.

Read this when: you're about to change something and aren't sure what else it touches, or you're starting a session and want the map before diving into individual docs.

## How other projects do this (what this is modeled on)

- **ADR index convention** (Michael Nygard's original ADR proposal; formalized by tools like `adr-tools`'s `adr generate toc`): a single `docs/adr/README.md` listing every decision record. `docs/adr/README.md` (this repo) follows that convention directly.
- **Living Documentation** (Cyrille Martraire): the core principle is *don't hand-duplicate a fact that can drift* — reference the single source instead of copying it. That's why `docs/adr/README.md`, not a hardcoded count, is now the thing every other doc points to for "how many ADRs" (see "What NOT to duplicate" below) — copies of a number are exactly what drifted three separate times this session.
- **Docs-as-code with generated indices** (Docusaurus/MkDocs "related pages"/backlink plugins, or a monorepo's generated `SUMMARY.md`): larger orgs automate this — a build step regenerates the index/backlinks so it's structurally impossible for it to go stale. This repo doesn't have that tooling (deliberately — it's a solo learning project, not worth the setup cost yet); this file is the manual equivalent. If this repo ever grows a doc-site build step, regenerating `docs/adr/README.md` from the ADR files would be the natural next step (the extraction command already exists in this session's history: `head -1` for title, `grep -A1 "## Status"` for status, per file).
- **PR/definition-of-done checklists**: some teams encode "if you touch X, update Y" as a PR template checklist rather than a standalone doc — the human-process version of the same idea. Given ADR-0017's amendment that PRs are optional for solo development, that mechanism isn't available here; this file is the checklist instead, consulted by habit (or by an agent reading `AGENTS.md`) rather than enforced by a PR template.

## Every doc, its purpose, and its update trigger

| Doc | Purpose | Update trigger |
|---|---|---|
| `docs/STATUS.md` | Current state — the only place "where are we" should be read from | End of every work session |
| `docs/PLAN.md` | Roadmap, phases, "done when" criteria, decisions table | New ADR, phase scope change |
| `docs/FEATURES.md` | User-facing feature scope per phase | New/changed/re-tiered feature |
| `docs/ARCHITECTURE.md` | Technical design — "how it's built" | New ADR touching architecture |
| `docs/domain-model.md` | Entity fields/invariants | New/changed entity, field, or invariant |
| `docs/TOOLING.md` | Tool inventory, versions, environment fixes | New tool adopted, version changed, environment issue found/fixed |
| `docs/WORKFLOW.md` | Session delegation rules + token-optimization policy | New agent/skill, new human-review lane, delegation strategy change |
| `AGENTS.md` (repo root) | Cross-tool contract (any AI tool reads this) | Module boundary change, new "never do" rule, new human-review lane |
| `docs/EXPERT_GUIDELINES.md` + `docs/guidelines/*.md` | Coding standards, general + tool-specific | New idiom/pattern decision, new tool adopted |
| `docs/specs/*.md` | Per-feature EARS acceptance criteria | Before implementing any feature (ADR-0016) |
| `docs/adr/*.md` + `docs/adr/README.md` | Decision records + their index | Every new or status-amended decision |
| `.claude/skills/*/SKILL.md` | Project-specific review gates | New human-review lane, new guideline doc to check against |
| `.claude/agents/*.md` | Delegation agents with pre-loaded project context | Fixed project context changes (module boundaries, JDK path, etc.) |

## Change-impact map — the part that actually prevents drift

| If this changes... | ...also check/update |
|---|---|
| A new ADR is added | `docs/adr/README.md` (index); `docs/PLAN.md`'s decisions table; `docs/ARCHITECTURE.md`'s consolidated-range line if it touches architecture; `docs/STATUS.md`'s narrative |
| An ADR's *status* changes (amended/superseded) | `docs/adr/README.md`'s status column; the amending ADR should say so in its own `## Status` line too (existing convention, e.g. ADR-0001, ADR-0005) |
| ADR-0017's human-review-lane list changes | `docs/adr/README.md`'s "Human-review-required lanes" section (canonical copy); `AGENTS.md`'s "Never do" list; `docs/WORKFLOW.md`'s human-review-lanes section; `kotlin-expert-review`/`web-expert-review` SKILL.md's "When to use" |
| A module gets renamed/restructured (ADR-0001-style) | Every ADR referencing the old name (search, don't assume); `AGENTS.md`; `docs/ARCHITECTURE.md`; `docs/PLAN.md` §4 |
| A new entity/field/table is added to the domain model | `docs/domain-model.md`; the relevant Flyway migration; `docs/guidelines/exposed-koin.md` if it affects repository patterns |
| A new tool is adopted or a version changes | `docs/TOOLING.md`; `AGENTS.md`'s build/test commands if user-facing; the relevant guidelines doc |
| Phase scope changes (a feature moves phase, a phase's "done when" changes) | `docs/PLAN.md`; `docs/FEATURES.md`; `docs/STATUS.md`'s "Not started"/"Next step" |
| A new project-specific Skill or Agent is added | `docs/WORKFLOW.md`'s "Skills already available"/delegation table; `docs/STATUS.md`'s reference-docs line |
| The testing strategy changes (a new required test category, a new tool) | `docs/adr/0009-testing-strategy.md` (canonical); `docs/EXPERT_GUIDELINES.md` §3; `docs/TOOLING.md`'s testing table |

## Known intentional duplications (double-check these by hand — nothing enforces their sync automatically)

- **Human-review lanes**: canonical source is ADR-0017 + amendments (ADR-0023, ADR-0024). Copies exist in `AGENTS.md` (needs to be self-contained for other AI tools that won't necessarily read `WORKFLOW.md` or the ADR folder) and `docs/WORKFLOW.md`. `kotlin-expert-review`'s SKILL.md deliberately does *not* keep its own copy — its frontmatter description points at its own body instead of enumerating, to remove one of the three copies.
- **Module names**: canonical source is ADR-0001. Referenced (not copied in structure, just in name) throughout every other doc — a rename means a repo-wide search, not a single edit.

## What NOT to duplicate (fixed this session)

Hardcoded ADR counts ("29 ADRs") existed in `docs/PLAN.md`, `docs/STATUS.md`, and `docs/ARCHITECTURE.md`'s prose — three copies of a number that goes stale the moment any new ADR is added, which happened repeatedly. Fixed by pointing at `docs/adr/README.md` instead, since that file is already required to update in the same commit as any new ADR (it's the index, not an afterthought) — the count is now derived by reading it, not copied.
