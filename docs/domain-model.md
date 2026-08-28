# Domain model — AfnaiHisab (v1)

Detailed fields/types/invariants for the entities listed in `docs/PLAN.md` §3. This is what `core`'s package layout mirrors during Phase 0 scaffolding. Update this file whenever the model changes — it's the source of truth, not `PLAN.md`'s summary.

## Critical invariant: money is integer minor units, never float

Every money field (`Expense.amount`, `Split.amount`, `Settlement.amount`) is a **`Long` in minor units** (e.g. cents for USD — the currency code determines the minor-unit exponent, most currencies 2, some like JPY 0). Never `Float`, `Double`, or an implicit-scale decimal. This makes FEATURES.md §(a)'s rounding-remainder rule (largest-remainder method) exact by construction — integer arithmetic has no representation error to reason about. This is the single most important correctness rule in the whole domain model; get it right in `core`'s type definitions before anything else is built on top.

In `core` this is expressed as `MinorUnits`, a `@JvmInline value class` wrapping that `Long` (and `CurrencyCode`, wrapping the ISO 4217 `String`) — `core/src/commonMain/.../domain/Money.kt`. Both erase to the underlying primitive at runtime, so the storage types in the tables below are unchanged; the wrapper exists so the compiler rejects a split count, a timestamp, or a category name being passed where money or a currency is expected. A future Exposed column mapping must unwrap `.value` explicitly at that boundary.

## ID strategy

UUIDv7 for every entity's primary key — time-sortable, which makes it a natural cursor for ADR-0015's cursor-based pagination without a separate sequence/timestamp column. Confirmed by a 2026 best-practice audit as the currently-recommended choice over both UUIDv4 (worse index locality) and bare bigint (no natural pagination cursor).

**Monitored risk, not a current problem**: implemented via `kotlin.uuid.Uuid`, which is still `@ExperimentalUuidApi` — JetBrains' own docs note binary-incompatibility risk before stabilization (tracked, unresolved, in [KT-31880](https://youtrack.jetbrains.com/issue/KT-31880)). Acceptable for an application with no external consumers to break (unlike a published library), but every entity id in `core` depends on it, so a breaking stdlib change would ripple everywhere. No mitigation action needed now — just don't let this become a silent assumption. Re-check this note if upgrading the Kotlin version ever mentions `kotlin.uuid` changes in its release notes.

## Entities

### User
| Field | Type | Notes |
|---|---|---|
| id | UUID (v7) | |
| email | String | unique among non-ghost users |
| passwordHash | String? | Phase 1: required for real accounts; null for ghost users (can't log in) |
| displayName | String | |
| isGhost | Boolean | default false — non-app member per FEATURES.md §(b); invited by email, no login until they claim the account (Phase 2, out of Phase 1 scope) |
| createdAt | Instant | |

### Ledger
| Field | Type | Notes |
|---|---|---|
| id | UUID | |
| name | String | |
| defaultCurrency | String | ISO 4217, e.g. `"USD"` — see open question below |
| createdAt | Instant | |
| archivedAt | Instant? | Phase 2 (group archiving), null in Phase 1 |

**Invariant (ADR-0004):** "personal" vs "shared" is never a stored field — it's derived from `Membership` count (1 = personal, N = shared). No branching logic anywhere in `core`'s domain layer keys off a ledger "type."

### Membership
| Field | Type | Notes |
|---|---|---|
| id | UUID | |
| ledgerId | UUID (FK → Ledger) | |
| userId | UUID (FK → User) | |
| role | enum `OWNER` \| `MEMBER` | |
| joinedAt | Instant | |

**Invariants:** unique `(ledgerId, userId)`; a Ledger always has ≥1 `OWNER` (reject removing the last one).

### Expense
| Field | Type | Notes |
|---|---|---|
| id | UUID | |
| ledgerId | UUID (FK → Ledger) | |
| payerMembershipId | UUID (FK → Membership) | who paid |
| amount | Long (minor units) | |
| currency | String | ISO 4217. Phase 1: must equal `Ledger.defaultCurrency`; Phase 2+ allows conversion (FEATURES.md §b) |
| category | String | Phase 1: free text; richer taxonomy later, not blocking |
| note | String? | |
| date | LocalDate | the expense's real-world date, distinct from `createdAt` |
| createdAt | Instant | |
| splitType | enum `EQUAL` \| `EXACT` \| `PERCENTAGE` \| `WEIGHTED` \| `ITEMIZED` | Phase 1: `EQUAL` only; rest are Phase 2 (FEATURES.md §b) |
| isLocked | Boolean | default false — Phase 2, set true after full settlement |

**Invariants:** `amount > 0`; `sum(Split.amount for this expense) == Expense.amount` exactly, always — this is what the rounding-remainder rule exists to guarantee.

### Split
| Field | Type | Notes |
|---|---|---|
| id | UUID | |
| expenseId | UUID (FK → Expense) | |
| membershipId | UUID (FK → Membership) | |
| amount | Long (minor units) | this member's share |
| shareValue | Long? | raw percentage (0-100) or weight (e.g. "2 shares") — null for `EQUAL`/`EXACT`, required for `PERCENTAGE`/`WEIGHTED` |

### Settlement
| Field | Type | Notes |
|---|---|---|
| id | UUID | |
| ledgerId | UUID (FK → Ledger) | |
| fromMembershipId | UUID | who pays |
| toMembershipId | UUID | who receives |
| amount | Long (minor units) | |
| currency | String | |
| note | String? | |
| createdAt | Instant | |

**Invariant:** `fromMembershipId != toMembershipId`; `amount > 0`.

### AuditLogEntry (Phase 2+, ADR-0012 — not built in Phase 0/1, shape documented now)
| Field | Type | Notes |
|---|---|---|
| id | UUID | |
| entityType | enum `EXPENSE` \| `SETTLEMENT` | |
| entityId | UUID | |
| changedByUserId | UUID | subject to ADR-0014's anonymization on deletion request |
| changeType | enum `CREATE` \| `UPDATE` \| `DELETE` | |
| oldValue | JSON? | |
| newValue | JSON? | |
| timestamp | Instant | |

**Invariant:** append-only — rows are never updated or deleted, even by ADR-0014's anonymization (which redacts `changedByUserId`'s linked PII elsewhere, not this table's rows).

## Derived (never stored)

- **`MemberBalance`** — `{ membershipId, netBalance: Long }`, computed by summing this member's `Split.amount`s owed minus `Settlement.amount`s paid/received, per `PLAN.md` §3's derivation invariant. Recomputed on read, never cached as a stored column.
- **Settlement suggestions** ("simplify debts") — `List<MemberBalance> -> List<Settlement>` via ADR-0007's greedy algorithm. Only becomes a real `Settlement` row if the user confirms it.

## Open question carried from `docs/PLAN.md` review

`Ledger.defaultCurrency`'s actual default value (USD vs. something else) is still open — see the last planning discussion. Doesn't block writing this schema (it's just a seed-data default), but needs an answer before Phase 1's first real ledger gets created.
