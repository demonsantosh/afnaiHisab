# Guidelines — Exposed 1.x + Koin (repository layer)

Tool-specific deep dive for `server`'s not-yet-written repository layer (Phase 1's expense/split/balance/settlement persistence). Cross-cutting concerns live in `docs/EXPERT_GUIDELINES.md` — read both. Enforced by `kotlin-expert-review`. Exposed's package structure changed completely in its 1.0 release (2026-01) — several rules here exist specifically because older tutorials/training data reference the pre-1.x API, which will not compile.

## Package structure — 1.x only, never pre-1.x
- Table/column definitions: `org.jetbrains.exposed.v1.core.*`. Database/transaction/query execution: `org.jetbrains.exposed.v1.jdbc.*` (this project is JDBC over HikariCP, not R2DBC — ADR-0019).
- **Never** `org.jetbrains.exposed.sql.*` — that's the pre-1.x package path. If it appears (from an old tutorial, or a training-data reflex), it won't compile and is a sign to re-check against current docs, not fix the import path.
- Other removed/superseded names to never reach for: `newSuspendedTransaction` (use `suspendTransaction`), `ThreadLocalTransactionManager`/`bindTransactionToThread()` (removed in 1.0), `transaction(db) { }` with the pre-1.x parameter order.

## DSL over DAO
- Use Exposed's DSL API (`Table` objects + `select`/`insert`/`update` DSL), not the DAO API (`IntEntity`/`UUIDEntity`, `IntIdTable`) — DAO is JetBrains' own secondary option, and this project's thin-repository shape (`core` holds all business logic, `server` only persists) has no need for DAO's richer entity behavior. Seeing `IntEntity`-style code anywhere in this codebase is a signal something copied an old tutorial, not a valid pattern here.
- UUID columns: use the `uuid()` column builder, which maps directly to `kotlin.uuid.Uuid` — matching every entity id in `core` already. Don't reach for `.javaUUID()`; that's for `java.util.UUID` interop this project doesn't need.
- Money columns: plain `long("amount")`, no special Exposed money type. Unwrap/wrap `MinorUnits.value` exactly at the repository boundary — `core` never imports Exposed, and Exposed never sees a `MinorUnits` value class directly.

## Transactions
- `suspendTransaction { }` (not `newSuspendedTransaction`) inside Ktor route handlers, since routes are suspend functions.
- **Real gotcha, not a naming nit**: on JDBC, `suspendTransaction` is suspend-*shaped* but the underlying connection is still a blocking call — it does not make JDBC non-blocking on its own. Explicitly dispatch onto `Dispatchers.IO`, exactly the pattern `HealthRoutes.kt`'s DB probe already uses. Every repository call needs this, not just the health check.
- Scope one transaction per repository function — don't span a transaction across multiple repository calls or into `core`'s use-case functions, which take plain data and never open a transaction themselves.
- **Multi-row writes from one `core` result must be one transaction, not several.** `createEqualSplitExpense` returns one `Expense` plus N `Split`s as a single logical unit — the repository function persisting it must insert the expense and all its splits inside one `suspendTransaction`, not one transaction per insert. A partial write (expense saved, not all splits) would corrupt the sum-to-expense-amount invariant `core` already guarantees at the domain layer.
- Idempotency-key checking (ADR-0023) belongs in the same transaction as the write it guards — check-and-insert as one atomic operation, not a separate read followed by a separate write, or two concurrent retries could both pass the check.

## Repository pattern + Koin
- `interface FooRepository` (returns/accepts `core` domain types only, lives conceptually alongside the domain it serves) + `class ExposedFooRepository : FooRepository` (the only class that imports Exposed types for that repository).
- Koin registration: `single<FooRepository> { ExposedFooRepository(...) }` — `single` scope, since a repository holds/uses a shared connection pool (`DatabaseFactory`'s Hikari pool is already effectively singleton-lifetime).
- Routes resolve via `by inject<FooRepository>()` at route-group scope (lazy), not `get<T>()` called fresh inside each handler — matches Koin-for-Ktor's documented preferred pattern.
- One Koin module per layer (repository module, service module) is the right granularity at this project's current size — mirrors the existing single-file `ServerModule.kt`. Split further only once there are enough repositories that one file is unwieldy, not preemptively.

## Testing
- Real H2 (already this project's local dev DB) for repository tests, not mocked SQL — an ORM-mapping bug is exactly what a mock would hide. This matches `MigrationTest`'s existing precedent of testing against a real database.
- Continue this project's existing `TestConfig`/`testAppConfig()` pattern (a fresh `AppConfig` per test) rather than introducing Koin's `.override()`/`loadKoinModules` test-replacement machinery — that's a real Koin feature, but not needed at this project's size when constructing a fresh config already achieves the same isolation.

## Explicitly out of scope for now
Koin Annotations / compile-time DI generation exists as an alternative to the manual `module { }` DSL already in use, but ADR-0005 already deliberately chose runtime Koin over compile-time alternatives (kotlin-inject) for ecosystem-maturity reasons — adopting Koin Annotations now would partially undo that reasoning without a new triggering problem. Not revisited here.

## Sources consulted (2026)
Exposed 1.0 release announcement and migration guide (blog.jetbrains.com, jetbrains.com/help/exposed), Exposed transactions docs, EXPOSED-940 (suspendTransaction blocking-on-JDBC gotcha), Koin for Ktor docs, Koin modules reference (insert-koin.io).
