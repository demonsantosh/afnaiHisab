# ADR-0027: All timestamps UTC internally; localization is a client-only concern

## Status
Accepted

## Context
`domain-model.md` already has both `Instant` (timezone-aware instant, e.g. `createdAt`) and `LocalDate` (no timezone, e.g. `Expense.date` — deliberately, "the expense's real-world date") fields, and more of both will be added as features grow. Nothing states a system-wide policy for how these interact, and date/timezone bugs are a notoriously subtle, easy-to-introduce bug class ("an expense recorded at 11pm shows as the wrong day for a user in a different timezone") — exactly the kind of thing that's cheap to decide once and expensive to retrofit across every date field already written.

## Decision
- Every timestamp stored or transmitted (`createdAt`, `joinedAt`, audit-log timestamps, etc.) is UTC, always, using `Instant` — never a naive/local `DateTime`.
- `Expense.date` (a deliberate `LocalDate`, no time component) represents the calendar date the user intends the expense to belong to, as *they* entered it — not derived from a server timestamp, not silently reinterpreted in a different timezone. It's an opaque calendar-date value, not a moment in time.
- Localization (displaying a UTC `Instant` in the viewer's local timezone, formatting a `LocalDate` per locale) is a **client-only concern** — web (`Intl`/date-fns-style formatting) and mobile (platform-native localization) each handle their own display formatting. The server and `core` never format a date/time for display, only store and transmit unambiguous values.

## Consequences
- No date-handling bug can originate from an implicit server-side timezone assumption — there isn't one to have.
- Every future date/time field added to the domain model should default to `Instant` unless it's specifically a calendar-date concept like `Expense.date`, in which case `LocalDate` — this ADR is the rule to check against, not something to re-derive per field.
- Client-side display code (web, later mobile) is responsible for correct localization; this isn't validated by `core`'s tests (framework-agnostic, no display logic) but should be by `web-expert-review`/mobile-equivalent review once display code exists.
