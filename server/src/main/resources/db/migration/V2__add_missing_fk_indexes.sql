-- V2 — three foreign key columns had no index (Postgres does not auto-index FK columns).
-- Unindexed FKs cause full table scans on joins and slow constraint-checks as tables grow — this
-- directly blocks the planned cross-ledger balance dashboard (docs/FEATURES.md) once expense and
-- settlement volume grows. A new migration, not an edit to V1, since V1 has already run against
-- local dev databases and Flyway migrations are never edited once applied.

create index ix_expenses_payer on expenses (payer_membership_id);
create index ix_settlements_from_membership on settlements (from_membership_id);
create index ix_settlements_to_membership on settlements (to_membership_id);
