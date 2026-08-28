-- V4 — ADR-0008's refresh-token rotation needs server-side session/family tracking, not a purely
-- stateless JWT: rotation-on-use must mark a token single-use, and reuse of an already-burned
-- token must revoke every other token in its session family (theft detection).
--
-- One row per issued refresh token (the row's own id is the token's `jti` claim):
--  * family_id groups every token that descends from the same login/register call. Rotating a
--    token inserts a new row with the same family_id and points the old row's replaced_by_id at
--    it; the family concept is what lets a reuse of an old token revoke *every* token in the chain,
--    not just the one reused.
--  * replaced_by_id set => this token has already been used once (single-use enforcement) — a
--    second use of it is exactly the reuse-detection trigger.
--  * revoked_at set => this token (and, when reuse is detected, its whole family) must never be
--    honored again, regardless of expires_at.

create table refresh_sessions (
    id              uuid primary key,
    user_id         uuid    not null references users (id),
    family_id       uuid    not null,
    issued_at       timestamp with time zone not null,
    expires_at      timestamp with time zone not null,
    revoked_at      timestamp with time zone,
    replaced_by_id  uuid references refresh_sessions (id)
);

create index ix_refresh_sessions_user on refresh_sessions (user_id);
-- Reuse-detection revokes every row sharing a family in one statement (docs/guidelines/exposed-koin.md).
create index ix_refresh_sessions_family on refresh_sessions (family_id);
