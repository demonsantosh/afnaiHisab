# Expert Guidelines — AfnaiHisab

What "implemented well" means in this codebase, distilled from two sources: this project's own experience (the 2026 best-practice audit and its fixes, ADR-0005/0009/0015/0017/0019's amendments, the TDD red-phase work) and standard expert Kotlin/KMP/Ktor practice. Read before writing code; check against before merging. Referenced by `AGENTS.md` and the `kotlin-expert-review` skill.

## 1. Kotlin language idioms
- No `!!` outside tests. Prefer `?:`, `requireNotNull`, or restructure so the nullability can't arise.
- Immutable by default: `val` over `var`, data classes + `copy()` over mutable state, `List`/`Map` (not `MutableList`/`MutableMap`) at API boundaries.
- Model illegal states as unrepresentable: sealed classes/interfaces + exhaustive `when` (no catch-all `else` on a closed hierarchy you own), not boolean/enum soup.
- `@JvmInline value class` for any domain-critical primitive wrapper — money, currency, meaningful ids. Zero runtime cost, compiler-enforced distinctness. (`MinorUnits`, `CurrencyCode` are the precedent.)
- Scope functions (`let`/`run`/`apply`/`also`/`with`) for genuine scoping/chaining clarity, not reflexively — if a plain `val` reads clearer, use that.
- Extension functions for operations that genuinely feel external to a type; never to reach around encapsulation.
- Expression-body functions for a single expression; block bodies otherwise. Don't force one style where it hurts a specific case's readability.

## 2. Domain-layer design (`core`)
- Pure functions: no hidden I/O, no wall-clock reads, no internal `Uuid.random()` — every time- or identity-dependent value is an explicit parameter (this is what makes `commonTest` deterministic, ADR-0009).
- Validation returns data (`ValidationResult`), never throws, for any *expected* rejection path. Exceptions are for genuinely exceptional/programmer-error conditions only.
- Business rules live in `core`, never in a `server` route handler (ADR-0001) — a route's job is deserialize → call `core` → serialize, nothing else.
- Respect layer boundaries even when it means more functions, not fewer: `createSettlement` validates+constructs; `recordSettlement` composes that with balance derivation (AC-13). Don't collapse two genuinely different responsibilities into one function for convenience.
- KDoc as a living spec, not a comment: precise enough that a test suite could be (and, in this project's TDD-stub pattern, was) written directly from it. The moment real logic replaces a stub, its "not yet implemented" language must go with it — a stale disclaimer next to working code is a legibility hazard, not a harmless leftover.

## 3. Testing discipline
- TDD red-then-green for anything in a human-review lane (ADR-0017 — money math, auth, deletion, audit log): write the failing test against a documented stub before the real implementation exists.
- Deterministic only, always: no `Uuid.random()`, no wall-clock reads, no `Math.random()` in a test. Seeded/fixed fixtures throughout (`TestFixtures.kt`'s `uuid(n)` pattern is the template).
- One behavior per test, named after what it proves, not its mechanics (`` `AC-2 allocates the leftover minor units to the lowest membershipIds, sum always exact` ``, not `test2`).
- Exhaustive/parameterized coverage for edge cases with an independent reference computation to check against (AC-2's 9-case rounding test), not a single happy path.
- Every spec acceptance criterion maps to at least one test (ADR-0016) — an untested criterion is a visible gap, not a silent one.
- Beyond unit tests, per ADR-0009's amendment: integration tests (real H2/route, not mocked) for every repository and route; at least one real HTTP-level API/contract test before Phase 2 ships (an in-process route test can pass while the actual wire format is wrong); an explicit negative authorization test (non-member rejected) per ledger-scoped route (ADR-0024); an explicit idempotency test (repeated key returns the original response, ADR-0023); property-based tests specifically for the settle-up algorithm and the rounding rule, where "this invariant holds for any input" is stronger than enumerating cases by hand.

## 4. Ktor / backend
- Routes are thin: validate input shape, call `core`, map the result to a response. No business logic, no persistence logic beyond calling a repository.
- Every error flows through the one status-pages handler into the standard envelope (ADR-0015) — never a hand-rolled `try/catch`-and-format-JSON-by-hand in a route.
- Dependencies arrive via constructor/Koin injection, never object singletons or static/global state — every dependency a class needs is visible in its constructor.
- Structured logging (`logback` + `call-logging`), never `println` — a log line should be traceable to a request or lifecycle event.
- Configuration flows through `AppConfig`; never a bare `System.getenv` scattered through business code.

## 5. Build / tooling hygiene
- Version catalog, pinned versions, no floating ranges (ADR-0017) — caught by Renovate, not memory.
- `ktlint` (formatting) and `detekt` (complexity/smell/bug-pattern) are both required, both wired into `check`. Neither substitutes for the other.
- The Gradle configuration cache must stay green. A new incompatibility from a build-script change is a build-script bug to fix, not something to route around (`--no-configuration-cache` is not an acceptable workaround).
- Every foreign key gets an index unless there's a documented reason it doesn't need one — this project already paid for rediscovering that once (the missing-FK-index audit finding); don't pay for it again at a bigger scale.
- Migrations are append-only once committed. Never edit an applied `V*.sql` — add a new one.

## 6. Money / financial correctness — the highest-stakes lane
- Integer minor units always (`MinorUnits`), never float, double, or an implicit-scale decimal.
- Every rounding decision is an explicit, named, tested rule (the largest-remainder method) — never "whatever the language's default division happens to do."
- Balance changes are always explainable: report before/after context (AC-13's pattern), never a bare delta with no way to see what it resolved.
- Anything touching balance math, settlements, or the audit log requires human review before merge (ADR-0017), regardless of who or what authored the diff.

## 7. Git / process
- Conventional Commits, referencing the spec or ADR a change implements.
- A spec (EARS acceptance criteria, ADR-0016) exists before implementation for any feature — never a vague ticket title stood in for a spec.
- One commit per logical layer of a change, not one giant commit per feature.
