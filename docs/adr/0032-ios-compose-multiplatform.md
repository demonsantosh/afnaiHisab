# ADR-0032: iOS UI framework — Compose Multiplatform UI, decided early

## Status
Accepted

## Context
ADR-0010 and `ARCHITECTURE.md`'s "Platform look-and-feel" note deliberately left iOS's UI framework (SwiftUI vs. Compose Multiplatform UI) open until Phase 4, reasoning that more information — real experience from Phase 3's Android work, further ecosystem maturation — would exist by then. The user asked to decide early instead. Rather than decide on stale assumptions, current (2026) status was researched: Compose Multiplatform for iOS reached **Stable in v1.8.0 (May 2025)** — genuinely production-ready, not experimental, with real production adopters (Netflix, Cash App cited; an indie example citing 96% code sharing between Android and iOS). The maturity-based reason for waiting no longer holds — this is no longer a risky early bet.

## Decision
iOS (Phase 4) uses **Compose Multiplatform UI**, the same framework as Android (ADR-0021) — not SwiftUI. This maximizes actual shared UI code between Android and iOS, not just the MVI state layer (ADR-0010) — directly serving both this project's explicit learning goal (the KMP ecosystem, hands-on) and solo-developer efficiency (one UI codebase, not two).

Accepted with two explicit, real, permanent tradeoffs — not hidden, and not maturity gaps that further waiting would resolve:
- **Native look-and-feel is not automatic.** Compose Multiplatform renders via Skia, not native UIKit — it does not inherit iOS's Human Interface Guidelines styling for free the way SwiftUI does. Matching iOS conventions (fonts, spacing, motion) is deliberate work the team owns, permanently, by architecture — acceptable for this app's actual context (a personal/small-group tool, not a flagship consumer app competing on pixel-perfect iOS polish).
- **Accessibility (VoiceOver) requires deliberate semantic mapping** per screen — well-supported by current tooling, but not automatic. Treated with the same discipline already applied to authorization (ADR-0024) and idempotency (ADR-0023): built in as the screen is built, not hardening added later.

## Consequences
- Resolves `ARCHITECTURE.md`'s "Platform look-and-feel" open flag — no longer deferred to Phase 4; that section should be updated to reflect this decision.
- Phase 4 scaffolding plans for shared Compose UI code between `app/androidApp` and `app/iosApp` from the start, not two separate UI implementations.
- HIG-fidelity work and per-screen accessibility semantics are now known, named Phase 4 line items — not risks to discover mid-implementation.
- Revisit only if real Phase 3 Android experience surfaces something that changes this calculus — the same standing option ADR-0006 (SQLDelight) already reserves for itself — but this is not deferred by default anymore.
