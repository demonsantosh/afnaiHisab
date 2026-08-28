# Guidelines — Next.js 16 / React 19 / TypeScript

Tool-specific deep dive for `web/`. Cross-cutting concerns (testing discipline, git/process, general correctness) live in `docs/EXPERT_GUIDELINES.md` — read both. Enforced by the `web-expert-review` skill. Current as of the 2026 App Router/React 19 ecosystem — several rules here are recent shifts from older, more commonly-known React convention; that's flagged explicitly where it applies.

## Server vs. Client Components
- Default every component to a Server Component. Add `'use client'` only where `useState`/`useEffect`/event handlers/browser APIs are actually needed — and push that boundary to the smallest leaf, not the page or layout. Overusing `'use client'` is the single most-cited 2026 App Router mistake (can cost 30–40% of the RSC bundle-size benefit).
- `params`/`searchParams` in page components are `Promise`s in Next.js 16 — `await` them. A common upgrade bug is treating them as plain objects.
- Fetch independent data with `Promise.all`, not sequential `await` — sequential awaits for unrelated data is a named anti-pattern.

## API calls — reads and writes both go through `lib/api.ts` (ADR-0020)
- No `'use server'` Server Actions calling the Ktor backend — see ADR-0020 for why. `lib/api.ts` is the only module that knows the backend's URL and error shape, for every call.
- **Shift from older React convention**: storing server data in `useState` populated via a `useEffect` + `fetch` is now an explicitly named anti-pattern — Server Components fetch directly; client-side fetches (this project's deliberate CORS-testing exception) are for the specific cases that need to run in the browser, not a default data-loading pattern.
- Type every API result as a discriminated union mirroring `core`'s `ValidationResult` and the backend's error envelope (ADR-0015):
  ```ts
  type ApiResult<T> =
    | { ok: true; data: T }
    | { ok: false; error: { code: string; message: string } }
  ```
  Exhaustive `switch`/`if (result.ok)` narrowing, never a nullable-everything shape.

## Forms (ADR-0020)
- `useActionState` for form lifecycle (pending/error/success), not manual `useState` bookkeeping per field — this is the 2026 default, a genuine shift from controlled-components-as-default. The action function calls `lib/api.ts` directly; it does not need `'use server'`.
- Never re-implement split/balance/expense validation client-side — the backend is the sole validation authority (ADR-0003). Surface its error envelope through `useActionState`'s state, don't duplicate the rule it's enforcing.
- Uncontrolled inputs + the action's `FormData` over controlled inputs + `useState`, per current default guidance — reach for controlled inputs only when something genuinely needs per-keystroke reactivity (e.g. a live character counter), not as the default.

## TypeScript
- Enable `noUncheckedIndexedAccess` and `exactOptionalPropertyTypes` in `tsconfig.json` — both are now default-on recommendations, not edge-case hardening.
- Known gotcha: discriminated-union narrowing can misbehave with `noUncheckedIndexedAccess` on some TS versions — if enabling it breaks a discriminated union's narrowing, that's a known interaction to search for, not a sign the union pattern itself is wrong.
- Discriminated unions over enums or a nullable-fields object for anything representing "one of several possible shapes" (API results, form state, split-type-specific fields) — exhaustive `switch`, no runtime cost, and a direct mirror of how `core` already models the same idea in Kotlin.

## Folder structure
- Feature-based once past a handful of pages: route code in `app/`, cross-feature shared code in `components/`, general API/business logic in `lib/`, feature-specific logic in `features/<name>/` rather than dumped into `lib/`. Route groups (`(auth)`, `(ledger)`) organize without changing URLs.
- Don't pre-build this structure now — `web/` is currently one health-check page. Add structure when a second real feature (the expense form) actually needs it, not preemptively.

## Testing (ADR-0019's already-decided tools)
- Vitest + React Testing Library: Server Actions-as-plain-functions (n/a here per ADR-0020, but the pattern generalizes to any plain async function), synchronous Server Components, and Client Components.
- **Real limitation to plan around**: Vitest cannot render *async* Server Components at all. Any async Server Component (likely shape for the ledger/auth pages) needs Playwright for meaningful test coverage, not Vitest — don't discover this mid-Phase-1.
- Playwright: auth flows, real form submissions, anything involving an async Server Component.

## Sources consulted (2026)
Next.js 16 App Router guide (dev.to/getcraftly), Next.js 16 performance/Server Components (digitalapplied.com), Server Actions tutorial (makerkit.dev), Next.js discussion #72919 on Server Actions scope, common App Router mistakes (upsun.com), `noUncheckedIndexedAccess` (dev.to/gabrielanhaia), discriminated unions for API responses (dev.to/maanu07), React 19 blog + `useActionState` (react.dev, shubhra.dev), Next.js/React testing guides (strapi.io, qaskills.sh).
