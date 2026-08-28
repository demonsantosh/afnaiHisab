# ADR-0034: Mobile-side i18n via Compose Multiplatform's built-in resources

## Status
Accepted

## Context
ADR-0031 decided web's i18n approach (`next-intl`) but was scoped to `web` only — nothing addressed the mobile-side (Android/iOS, both Compose Multiplatform UI per ADR-0032) equivalent for localized strings, images, or fonts shared across those two platforms.

## Decision
**`org.jetbrains.compose.resources`** (the `compose.resources` Gradle plugin) — Compose Multiplatform's official, stable resource system. Generates typed `Res.string.xxx` accessors from per-locale files organized in common code, shared identically by Android and iOS. This is the direct mobile-side counterpart to ADR-0031, not a competing i18n choice — same principle (wrap every user-facing string from day one, translate later), different tool because web and mobile use fundamentally different resource pipelines (Next.js/JSON vs. Gradle-generated Kotlin accessors).

**Explicit caveat**: keeping translation *keys* conceptually unified between `next-intl` (web, JSON) and `compose.resources` (mobile, XML-backed) is not automatic — no single tool spans both. The only thing that keeps them aligned is a shared naming/namespacing convention for keys, enforced by discipline (e.g. `expense.form.amount_label` meaning the same thing in both systems), not tooling. This should be stated explicitly in whatever guidelines doc covers Phase 3/4 mobile work, not assumed.

## Consequences
- Phase 3 (Android) scaffolding sets up `composeResources` from the start, mirroring ADR-0031's "wrap every string now" discipline for web — same reasoning, applied to the other platform pair.
- No translations exist yet on either platform (English-only) — this ADR is architecture, not a translation commitment, exactly like ADR-0031.
- A future guideline doc (when Phase 3 starts, matching this project's `docs/guidelines/*.md` pattern for Exposed/Koin and Next.js/React) should state the shared key-naming convention explicitly.
