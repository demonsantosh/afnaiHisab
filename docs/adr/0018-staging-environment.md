# ADR-0018: Free staging environment before production, with a defined promotion gate

## Status
Accepted

## Context
The plan had a gap: Phase 1 is localhost-only, and Phase 2's "deploy" bullet pointed straight at a still-undecided *production* target. There was no distinct environment for testing with real multiple users and real mobile debug builds before committing to (likely paid) production infrastructure. Free-tier hosting for a JVM backend specifically changes fast enough (Heroku's free tier is long gone, Fly.io's free tier ended for new users in 2026, Railway's "free" tier is now a one-time credit, not ongoing) that this needed a researched, current answer rather than an assumption.

## Decision
**Staging stack — Koyeb (backend) + Neon (Postgres) + Vercel Hobby (web), total cost $0:**
- **Koyeb** hosts `server` (Ktor/JVM) — free web service, no credit card, no forced sleep on the free tier (unlike Render, which cold-starts free services). Tight on RAM (512 MB) — fine for a handful of beta testers, not a claim beyond that.
- **Neon** hosts staging Postgres — free tier with no forced pause (unlike Supabase's free projects, which pause after 7 days idle and would silently break a staging environment between test sessions).
- **Vercel Hobby** hosts `web` (Next.js) — free, generous limits, and its personal/non-commercial license fits this project's current phase exactly.
- CORS allow-list (ADR-0015) gets the Vercel staging domain added alongside `localhost:3000`.

**Mobile debug-build testing, staged by what exists:**
- Before Phase 2's staging deploy exists: Android emulator uses `10.0.2.2` to reach the dev machine's localhost (built-in, no setup); a physical device on the same LAN uses the dev machine's local IP; **Cloudflare Tunnel** (not ngrok — free with no rate limits, versus ngrok's free tier being ephemeral/rate-limited) exposes the local dev server to a remote tester if needed before anything is deployed.
- Once Phase 2's Koyeb staging backend is live: Android/iOS debug builds just point at its public HTTPS URL directly — no tunnel needed. Since Phase 3/4 (mobile) come after Phase 2 (staging deploy) in the roadmap, this is the normal path, not the exception.

**Promotion gate — staging → production, explicit criteria, not a vibe call:**
1. Phase 2's full feature set (splits, audit log, 2FA, encryption at rest — ADR-0013) is implemented and has run on staging without data-integrity issues.
2. Real multi-user testing has happened on staging (not just solo testing) — at minimum, two people using a shared ledger concurrently.
3. Mobile debug builds (Phase 3/4, whichever exists by then) have been validated against staging, not just localhost.
4. A production hosting decision is made deliberately at this point (likely paid, given Koyeb's free-tier RAM ceiling) — this ADR does not pick that target; it only defines when it's time to.

## Consequences
- Phase 2 now has two concrete, sequenced deploy steps: staging (decided, free, this ADR) then production (still deliberately TBD, decided later against the gate above).
- Koyeb's 512 MB RAM is a real constraint worth monitoring once Postgres connection pooling + JVM overhead are both in play — if staging testing reveals this is too tight, revisit the backend host, not the whole staging strategy.
- No infrastructure cost during Phase 2/3/4's testing period — removes "can't afford to test with real users" as a reason to skip real multi-user validation before production.
