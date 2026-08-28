# ADR-0025: Backup/disaster-recovery policy — don't rely solely on the hosting provider's default

## Status
Accepted

## Context
A system-design review asked the question ADR-0018 never asked: what happens if the database is lost or corrupted? Research confirms Neon's free tier (ADR-0018's chosen staging Postgres host) includes point-in-time recovery capped at **6 hours of history, max 1 GB of changes** — real, but thin. A bug discovered a day after it corrupted data has no path back through Neon's own free-tier recovery. ADR-0018 picked Neon specifically for "no forced pause" and never evaluated backup posture at all — an omission a general production-readiness review would have caught, and specifically the kind of category (backup/DR) that tends to get silently folded into "capacity planning" or skipped entirely on a first-pass review, per the same research. This app stores real financial ledger data — losing it matters to a real user, unlike a throwaway demo, even during staging.

## Decision
Don't depend solely on the hosting provider's default retention. From the point staging (ADR-0018) carries any data anyone would be upset to lose:
- A periodic `pg_dump` export (a simple scheduled task — manual habit is acceptable for now, cron/CI-scheduled export is better once set up) stored somewhere independent of Neon itself (e.g., a local machine, or cheap/free object storage).
- Before any production promotion (ADR-0018's gate), this ADR's backup posture is explicitly re-evaluated as one of the promotion criteria — a production host choice that turns out to have the same thin-PITR characteristic as Neon's free tier is not acceptable for real user data without an independent backup story.

## Consequences
- This is a process/discipline item, not a code change — no new application code required, just an operational habit (or, better, a small scheduled script) that isn't glamorous and is exactly the kind of thing that's easy to skip. Track it in `docs/STATUS.md` until it's actually set up, not just decided.
- Amends ADR-0018 implicitly: "free hosting" was evaluated on cost and uptime, not data durability — this ADR is the missing third leg, and any future hosting choice (staging or production) should be checked against all three, not just the two already considered.
- Low priority while staging holds only test/development data; becomes real priority the moment any data anyone would actually miss exists there.
