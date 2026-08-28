# ADR-0028: API backward compatibility — v1 stays additive-only

## Status
Accepted

## Context
Phase 3/4 will ship Android and iOS apps that call the same `/api/v1` backend (ADR-0003's "web is just another HTTP client" symmetry). Unlike a web app, where every user gets the latest code on next page load, a mobile app version is stuck on whatever a user installed until *they* choose to update — some users will run an old app version against the *current* backend indefinitely. Nothing in this project has stated what that means for API evolution, and getting it wrong doesn't fail loudly — it fails as "some users' apps silently break after a backend deploy," which is much worse than a build error.

## Decision
- `/api/v1` is **additive-only**: new optional fields, new endpoints, new optional query parameters are fine. Removing a field, changing a field's type/meaning, or changing required-ness of an existing field is **not** a v1 change — it requires `/api/v2`.
- A genuinely breaking change means both versions are served simultaneously for a stated deprecation window (not decided precisely now — a concrete policy like "6 months" or "until app-store analytics show <1% of active installs on v1-only client versions" is a Phase 3/4-era decision, made when there's a real breaking change and real client version data to look at, not invented speculatively now).
- Server-side response fields clients don't recognize must be ignorable — clients (web now, mobile later) should be built to tolerate unknown extra fields in a JSON response from day one, not fail on them. This is what makes additive changes actually safe in practice, not just in principle.

## Consequences
- Every `server` route change gets checked against "is this additive" before merging, once mobile clients exist and this actually matters (Phase 3+) — low-stakes now (Phase 1/2, web-only, can redeploy both sides together), genuinely load-bearing later.
- The precise deprecation-window policy is deliberately not decided now — inventing a number with no real client-version data behind it would be guessing, not deciding. Revisit when Phase 3/4 makes it concrete.
- No `v2` exists yet, and none is anticipated for Phase 1/2 — this ADR is preparation for a problem that starts existing at Phase 3, decided now because the *convention* (additive-only, tolerate-unknown-fields) needs to be in place before the first mobile client ships, not retrofitted after.
