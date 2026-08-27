# Architecture — AfnaiHisab

Technical reference consolidating ADR-0001 through ADR-0019. Read `docs/PLAN.md` for phases/roadmap, `docs/FEATURES.md` for scope, `AGENTS.md` for the repo-root agent contract, and `docs/TOOLING.md` for the concrete tool inventory; this doc is "how it's actually built."

## Module layout (ADR-0001, amended)

```
core/            shared Kotlin module (commonMain) — domain, data, validation as internal packages
server/          Ktor backend, depends on core
web/             Next.js, talks to server over HTTP — not a KMP target (ADR-0003)
app/androidApp/  Phase 3, depends on core directly
app/iosApp/      Phase 4, depends on core via Kotlin/Native framework
```

`core` is the load-bearing module: business logic written once here is what makes Phase 3/4 additive instead of a rewrite (ADR-0001). `web` is deliberately outside the KMP graph — it's a normal HTTP client, same as Android/iOS will be until they upgrade to consuming `core` directly.

## Backend persistence

- **Postgres** for `server`'s system-of-record data (users, ledgers, expenses, splits, settlements) — standard relational store for this domain shape, matches the "server-authoritative" model in ADR-0002.
- **Exposed** (JetBrains' Kotlin SQL framework) as the JVM-side query/ORM layer in `server` — the natural Ktor-ecosystem pairing; not a topic that needed dedicated research, it's the default choice for a Kotlin-first backend.
- This is distinct from ADR-0006's SQLDelight decision: Exposed is JVM-only backend persistence against Postgres; SQLDelight (Phase 3+) is the multiplatform *mobile local cache*, running against SQLite on-device. Two different databases for two different concerns — don't conflate them.

## Dependency injection — Koin (ADR-0005)

One Koin module per layer (`core`, `server`, each app). `koin-ktor` integration on the backend. Runtime resolution, not compile-time — accepted trade-off for a learning project where ecosystem docs matter more than compile-time DI safety (kotlin-inject was the alternative considered).

## Domain logic — where the value actually lives

All of the following are pure functions/classes in `core`'s domain package, framework-agnostic, exercised by `commonTest` (ADR-0009) before any UI touches them:

- **Balance calculation**: derive per-member balance from Expenses + Splits + Settlements. Never stored directly (docs/PLAN.md §3) — always recomputed, which is what keeps edits/deletes (Phase 2) correct by construction instead of requiring manual balance patching.
- **Split validation**: equal / exact / percentage / weighted / itemized splits must always sum to the expense total; reject at the domain layer, not just at the UI form layer.
- **Settle-up / debt simplification (ADR-0007)**: `List<MemberBalance> -> List<Settlement>` via greedy largest-creditor/largest-debtor matching (two max-heaps or sort + two-pointer), O(n log n). Deliberately not min-flow/Ford-Fulkerson — NP-complete for true minimality, not worth the complexity for this feature's actual value.

## Presentation layer (mobile, ADR-0010)

MVI, hand-rolled, living in `core`'s `presentation` package — shared between Android and iOS regardless of which UI framework iOS ends up using (Compose Multiplatform UI or SwiftUI, still undecided per Phase 4). Per screen with real state complexity:

```
Intent (sealed)  ──dispatch──>  reduce(state, intent) ──>  State (immutable data class)
                                        │
                                        └──> Effect (SharedFlow) — one-off events: navigation, snackbars
```

- `MutableStateFlow<State>` is the single source of truth per screen — no parallel mutable properties.
- Applied to genuinely stateful screens: expense form (multi split-type validation), settle-up preview, Phase 5 sync status. Trivial screens (settings, profile) use a plain state holder — not applied dogmatically everywhere.
- No MVI framework (MVIKotlin/Orbit/Circuit) adopted up front — revisit only if hand-rolled boilerplate becomes a real pain point in Phase 3.
- Reducers are pure functions, unit-tested in `commonTest` per ADR-0009 — the same testing leverage argument as the domain layer, one level up.

## Data layer (mobile, Phase 3+)

- **SQLDelight** (ADR-0006) for the local read-through cache in Phase 3, later the offline write queue in Phase 5. Chosen over Room 3.0 specifically because it writes SQL directly in `commonMain` — the query layer is proven multiplatform by the time Phase 4 (iOS) needs it, rather than inheriting Android-first assumptions.
- **ktor-client** in `core`'s data package for networking, `MockEngine`-tested (ADR-0009) so networking logic doesn't require a live server to test.

## Auth (ADR-0008)

- Access token ~1h, refresh token ~24h+, single-use with rotation: each refresh burns the old token and issues a new one; reuse of an already-burned token revokes the whole session family (theft detection).
- Client: Ktor's Bearer Auth `loadTokens`/`refreshTokens` hooks — auto-attach, auto-refresh on 401, retry original request, `Mutex`-guarded against duplicate concurrent refreshes.
- Server: session/family tracking table, not purely stateless JWT — required for rotation + revocation to work. This is Phase 1/2 backend scope, not deferrable.
- OAuth (Phase 2) layers on top of this token pattern; it doesn't replace it.
- On-device storage (Phase 3+, ADR-0011): Android Keystore-backed encrypted storage, iOS Keychain — via an `expect`/`actual` `TokenStore` in `core`. Never plain `SharedPreferences`/`UserDefaults`.

## Native platform APIs (ADR-0011)

Two features need real `expect`/`actual` boundaries — the concrete case of KMP's "native API access" value, not just domain-logic sharing:

| Concern | `expect` (in `core`) | `actual` Android | `actual` iOS |
|---|---|---|---|
| Secure token storage | `TokenStore` | Keystore / Encrypted­SharedPreferences | Keychain |
| Push notifications | device-token registration + payload handling | FCM | APNs |

Push delivery also requires `server/` to store a device-token-to-platform mapping per session and route to the correct provider — Phase 3 scope, backend and mobile both.

## Platform look-and-feel (flagged, not yet decided)

Point deliberately left open at Phase 4: Android (Material) and iOS (Human Interface Guidelines) have different UX conventions. If Phase 4 picks Compose Multiplatform UI for iOS (ADR-0003/PLAN.md leave this open), per-platform theming needs deliberate design work, not a single shared theme assumed to look native on both. If Phase 4 picks SwiftUI instead, this is moot — native UI is native by construction. **Decide explicitly when Phase 4 starts; don't let this default silently.**

## API conventions (ADR-0015)

- All routes under `/api/v1/...` from Phase 0.
- Cursor-based pagination for unbounded lists (expense history, audit log) — offset pagination breaks under concurrent inserts on a shared ledger.
- Standard error envelope: `{ "error": { "code": "...", "message": "..." } }`, every endpoint, from Phase 1.
- CORS: explicit allow-list (`localhost:3000` in dev, the deployed web origin in Phase 2) — never a wildcard.
- Secrets: `.env` (gitignored) locally; production secret store chosen alongside the Phase 2 deploy target.
- Rate limiting on auth endpoints (login, refresh) from Phase 2 onward, via Ktor's rate-limiting plugin.

## Testing strategy (ADR-0009)

| Layer | Tool | What |
|---|---|---|
| `core` domain logic | `kotlin.test` + `kotlinx-coroutines-test` in `commonTest` | Balance calc, split validation, settle-up — runs on every target for free |
| `server` routes | `ktor-server-test-host` | Integration tests against real route wiring |
| `core` networking | `MockEngine` | Ktor-client code without a live server |
| Coverage | Kover | — |

Kotest is optional, added only if property-based testing is specifically wanted for the settle-up function (ADR-0007) — not adopted wholesale up front.

## Staging environment (ADR-0018)

| Layer | Host | Why |
|---|---|---|
| `server` (Ktor) | Koyeb free tier | No forced sleep/cold-start on free web services (unlike Render); 512 MB RAM ceiling — fine for a handful of testers, monitor if it's not |
| Postgres | Neon free tier | No forced pause (unlike Supabase's 7-day idle pause, which would silently break staging between sessions) |
| `web` (Next.js) | Vercel Hobby | Free, generous limits, personal-project license fits this phase |

Total cost: $0. Mobile debug builds (Phase 3/4) point at Koyeb's public URL directly once it exists — no tunnel needed by that point in the roadmap. Before staging exists: Android emulator via `10.0.2.2`, physical device via LAN IP, or Cloudflare Tunnel for a remote tester (not ngrok — free and unrate-limited vs. ngrok's ephemeral/rate-limited free tier).

Promotion to production is gated, not automatic — see ADR-0018's four criteria (Phase 2 feature set stable on staging, real two-person concurrent test done, mobile debug builds validated against staging, production target deliberately chosen at that point).

## Open items deliberately deferred

- Web → Compose Multiplatform Web migration: revisit per ADR-0003, only after Android + iOS have proven `core` across two platforms.
- Offline-first sync protocol: revisit per ADR-0002 at the start of Phase 5, not designed now.
- Bank/CSV import, payment-gateway integration: explicit non-goals for now (docs/FEATURES.md), no architecture committed.
