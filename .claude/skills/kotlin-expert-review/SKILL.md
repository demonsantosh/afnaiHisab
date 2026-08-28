---
name: kotlin-expert-review
description: Review Kotlin/KMP code changes in AfnaiHisab against this project's own expert-level guidelines (docs/EXPERT_GUIDELINES.md) — Kotlin idioms, domain-layer purity, TDD/testing discipline, Ktor conventions, build hygiene, and money-math correctness. A stricter, project-specific sibling to the generic /code-review, informed by this project's own audits (ADR-0005/0009/0015/0017/0019) rather than generic advice. Use before merging any core/server change, and always for anything in ADR-0017's human-review lanes (money math, auth, deletion, audit log).
---

# Kotlin Expert Review

## What this is

A project-specific correctness/quality gate for AfnaiHisab's Kotlin and Kotlin Multiplatform code, distinct from the generic `/code-review`: it checks against **this project's own distilled standard**, not generic best practice, because that standard was earned the hard way (a real best-practice audit, real ADR amendments, real build failures). Load `docs/EXPERT_GUIDELINES.md` in full before reviewing — it is the checklist, not background reading.

## When to use

- Before merging any change to `core` or `server`.
- Always — not optionally — for a diff touching ADR-0017's human-review lanes: balance/settle-up money math, auth/token handling, deletion/anonymization logic, the audit log.
- After a TDD green-phase pass (a stub's `TODO()` replaced with real logic) — this is exactly where guideline §2's "KDoc must stop claiming not-yet-implemented" and §3's "every AC still has a passing test" matter most.

## How to review

1. Read `docs/EXPERT_GUIDELINES.md` in full (7 sections: Kotlin idioms, domain-layer design, testing discipline, Ktor/backend, build/tooling hygiene, money/financial correctness, git/process).
2. Identify the diff or file set in scope (current uncommitted changes, or a named branch/PR if the invocation specifies one).
3. Read every changed file completely — not a diff-only skim — since several guidelines (KDoc staleness, layer-boundary violations, missing tests for an AC) only show up by reading the whole function/file, not the changed lines in isolation.
4. Check each changed file against every applicable section of `docs/EXPERT_GUIDELINES.md`. Skip sections that don't apply (e.g. §4 Ktor/backend has nothing to say about a pure `core` domain file).
5. For anything touching money math, balances, settlements, auth, or deletion: explicitly confirm the diff still matches its `docs/specs/*.md` acceptance criteria one by one, not just "looks reasonable."
6. Verify build health as part of the review, not just code reading — run (with `JAVA_HOME` set to the project's Homebrew `openjdk@17`, per `docs/TOOLING.md`):
   ```
   ./gradlew ktlintCheck detekt --no-daemon
   ./gradlew :core:jvmTest :server:test --no-daemon
   ```
   A guideline violation that ktlint/detekt would have caught mechanically is still worth flagging (they may not run in this environment) but is lower severity than something only a reader would catch.

## Output

Report findings with the `ReportFindings` tool, most-severe first. For each finding:
- Cite the specific `docs/EXPERT_GUIDELINES.md` section it violates (e.g. "§6 Money/financial correctness: rounding rule not tested against a reference computation").
- State the concrete failure scenario, not just the abstract principle.
- If the file's own KDoc or a spec in `docs/specs/` already documents the expected behavior and the diff contradicts it, say so explicitly — that's a correctness bug, not a style nitpick.

If nothing survives review, report an empty findings list — don't manufacture minor nitpicks to seem thorough.
