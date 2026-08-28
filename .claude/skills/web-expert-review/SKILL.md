---
name: web-expert-review
description: AfnaiHisab-specific review of web/ Next.js/React/TypeScript changes against docs/guidelines/nextjs-react-typescript.md + ADR-0020 (client-side fetch, not Server Actions). Required before merging any web/ change. Use kotlin-expert-review for core/server instead.
---

# Web Expert Review

## What this is

A project-specific correctness/quality gate for `web/`'s Next.js 16 / React 19 / TypeScript code, checking against **this project's own researched standard** (`docs/guidelines/nextjs-react-typescript.md`), not generic React advice — several of its rules are recent (2026) shifts from older, more commonly-known React convention, so a reviewer relying on older training-data instincts about "best practice" will flag the wrong things. Load that guidelines doc in full before reviewing.

## When to use

- Before merging any change to `web/`.
- Always for anything touching how `web/` calls the backend — `lib/api.ts`, any form submission, any data-fetching code — since ADR-0020 is a deliberate, non-obvious architectural choice (client-side fetch for both reads and writes, no Server Actions proxying to Ktor) that's easy to accidentally violate by reaching for the "normal" 2026 default.
- After adding any new page or component that fetches data or submits a form — the Server/Client Component boundary and `useActionState` conventions matter most exactly there.

## How to review

1. Read `docs/guidelines/nextjs-react-typescript.md` in full, and `docs/adr/0020-web-client-side-fetch-not-server-actions.md` for the rationale behind the API-call architecture specifically.
2. Read every changed file completely, not a diff-only skim.
3. Check specifically:
   - Is `'use client'` on the smallest possible leaf, or has it crept up to a page/layout for one interactive element?
   - Does any code call the Ktor backend from a `'use server'` Server Action, or from anywhere other than `lib/api.ts`? That's an ADR-0020 violation, not a style preference.
   - Do API results use a discriminated union (`ApiResult<T>`-shaped), or has a nullable-everything/exception-based shape crept back in?
   - Do forms use `useActionState`, and does the action function call `lib/api.ts` directly (not `'use server'`)?
   - Is any split/balance/expense validation duplicated client-side instead of trusting the backend's error envelope (ADR-0003)?
   - Are `params`/`searchParams` correctly `await`ed (Next.js 16's Promise change), not treated as plain objects?
4. Verify build/lint health as part of the review:
   ```
   cd web && npm run lint && npm run format:check && npm run build
   ```

## Output

Report findings with the `ReportFindings` tool, most-severe first. Cite the specific guideline or ADR each finding violates. An ADR-0020 violation (a Server Action calling Ktor, or a second module that knows the backend's URL/error shape) is high severity — it's an architectural regression, not a nitpick. If nothing survives review, report an empty findings list.
