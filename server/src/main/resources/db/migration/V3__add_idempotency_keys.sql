-- V3 — idempotency_keys table (ADR-0023, docs/domain-model.md "IdempotencyKey").
--
-- Every mutating financial endpoint requires an `Idempotency-Key` header; on first sight of a key
-- the request is processed normally and its response stored here verbatim (success or rejection
-- alike); on a repeated key the stored response is returned without reprocessing. Checking and
-- inserting a key must be one atomic transaction with the write it guards
-- (`docs/guidelines/exposed-koin.md`) — enforced in `server` code, not by this schema alone.
--
-- `response_body` is `text`, not a native JSON column type: Exposed 1.x has no built-in JSON column
-- builder without an extra contrib module, and H2/Postgres both accept arbitrary text just fine —
-- the column always holds the exact JSON text produced by the response serializer.
--
-- Column named `idempotency_key`, not the bare `key` in docs/domain-model.md's table sketch: `key`
-- collides with the reserved SQL keyword used in `PRIMARY KEY`/`FOREIGN KEY`, which H2 (and some
-- Postgres contexts) reject as a bare unquoted identifier.
--
-- Scoped by (user_id, idempotency_key), not idempotency_key alone (kotlin-expert-review finding,
-- 2026-08-28): an unscoped key is a real cross-tenant leak — a colliding or reused key value from a
-- different user would otherwise return *that other user's* cached response verbatim, most exploitable
-- on `POST /ledgers`, which has no ADR-0024 membership gate to catch it first. `user_id` has no FK to
-- `users` on purpose: an idempotency record must remain resolvable even if the user row is later
-- anonymized (ADR-0014) — this table is a short-lived operational log, not a durable relationship.

create table idempotency_keys (
    user_id         uuid                     not null,
    idempotency_key uuid                     not null,
    response_body   text                     not null,
    response_status integer                  not null,
    created_at      timestamp with time zone not null,
    constraint pk_idempotency_keys primary key (user_id, idempotency_key)
);
