# ADR-0031: i18n architecture decided now; translations added later (web)

## Status
Accepted. Scope note added 2026-08-28: this ADR is `web`-only, as it always was — see ADR-0034 for the mobile (Android/iOS) equivalent, `compose.resources`.

## Context
Nothing in this project has addressed multi-language UI support, despite the app potentially serving a geographically/linguistically diverse user base (clarified as distinct from ADR-0022's small-scale *user-count* NFR — a dozen users scattered across countries is still "small-scale," but needs localization ADR-0022 never addressed). The costly part of internationalization isn't the library setup — it's retrofitting every hardcoded UI string into a translation-key system after the fact, touching every component that was written without it in mind. Deciding the *pattern* now, even with zero translations yet, is the same "cheap now, expensive later" reasoning already used for ADR-0027 (timezones).

## Decision
- **Web: `next-intl`** — the current standard for Next.js App Router specifically (not the older Pages-Router-era i18n approach), with native Server Component support and typed translation keys. Every user-facing string in `web/` is wrapped in a translation call (`t('key')`) from Phase 1 onward, even though only an `en` message file exists initially — this costs a function call per string, not a translation effort, and is what keeps the option open cheaply.
- **Locale-sensitive display generally** (not just dates): extends ADR-0027's existing "localization is a client-only concern" principle from dates specifically to *all* locale-sensitive formatting — currency display, number formatting, pluralization. `core` and `server` deal in unambiguous values (ISO currency codes, integer minor units, UTC instants per ADR-0027); every client formats for its own viewer.
- **Mobile (Phase 3/4)**: each platform's native i18n mechanism (Android string resources, iOS `.strings`/String Catalogs) — not decided in detail now, consistent with this project's "decide when you get there" pattern for other Phase 3/4-specific choices (e.g. ADR-0006's SQLDelight-vs-Room reconsideration point).
- **Not decided now**: which languages to actually translate into, or when. This ADR is about keeping the door open, not about committing to a translation roadmap — that's a product decision for whenever real non-English-speaking usage is real, not speculative.

## Consequences
- Phase 1 web development writes every string through `next-intl` from the first component, even English-only — a small, permanent discipline, not a one-time setup task to remember later.
- No user-facing translated content exists yet and none is promised by this ADR — it purely keeps retrofitting cost low if/when real translations are needed.
- `User.preferredLocale` (a stored, cross-device preference) is deliberately *not* added to the domain model yet — Phase 1 is web-only, where browser locale detection is sufficient; a stored preference becomes worth its cost once multiple clients (web + mobile) need a consistent locale per user, i.e. Phase 3+.
