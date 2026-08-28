# ADR-0020: Web stays on client-side `lib/api.ts` for reads and writes, not Server Actions

## Status
Accepted

## Context
Current (2026) Next.js/React guidance treats Server Actions as the default mutation path — a function marked `'use server'`, called from a `<form action={...}>`, running on the Next.js server. This is the right default *when Next.js owns the backend* (its own DB, its own API routes). AfnaiHisab's `web` is not that shape: it is a pure frontend calling an independent, separately-versioned Ktor API (ADR-0003 frames it explicitly as "just another HTTP client," symmetric with Android/iOS in Phase 3/4). Adopting Server Actions to proxy calls to Ktor would introduce a second place that knows the backend's URL/error shape (a `'use server'` action calling Ktor, alongside `lib/api.ts`'s existing client-side fetch), splitting exactly the responsibility ADR-0015 and `CLAUDE.md` already assign solely to `lib/api.ts`. It would also silently drop the CORS path for every mutation (a server-to-server call needs no CORS), while reads stay on the client-side, CORS-checked path — an inconsistency the current health-check page was specifically built to avoid (it uses a client component precisely because a server-side fetch "would prove nothing about CORS").

## Decision
`lib/api.ts` remains the single module that knows the backend's URL and error shape, for **both reads and writes** — no `'use server'` Server Actions calling Ktor. This keeps every mutation on the same CORS-verified path as every read, and keeps web's relationship to the backend symmetric with what Android/iOS will do in Phase 3/4 (a plain HTTP client, nothing server-side proxying on its behalf).

This does **not** mean rejecting React 19's current form idiom. `useActionState` is adopted for form lifecycle management (pending/error/success state, replacing manual `useState`-per-field bookkeeping) — its `action` parameter is a plain function matching the `(state, formData) => state` shape and does not require `'use server'`. The action passed to `useActionState` calls `lib/api.ts`'s client-side fetch wrapper directly; it is a client-side async function, not a Server Action. Backend validation errors (ADR-0015's envelope) surface through `useActionState`'s returned state — the frontend never re-implements split/balance validation (ADR-0003 already establishes the backend as sole validation authority).

## Consequences
- Every API call — read or write — goes through `lib/api.ts`, is subject to the same CORS allow-list, and returns the same discriminated-union result shape (see `docs/guidelines/nextjs-react-typescript.md`).
- Forms get the current, ergonomic `useActionState` lifecycle without adopting the Server Actions architecture that assumes Next.js owns the backend.
- If `web` ever needs a capability only a real Server Action provides (e.g., a secret that must never reach the browser), that is a deliberate, separate decision at that point — not a default reached for out of following generic 2026 guidance uncritically.
