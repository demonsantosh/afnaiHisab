# Feature requirements — AfnaiHisab

Derived from two research passes: (1) Splitwise's feature set (free + Pro), competitors (Tricount, Settle Up, Splid, SplitMyExpenses), and personal-finance/accounting tools (YNAB, GnuCash, Beancount, Actual Budget, Monarch Money); (2) a deeper pass on edge-case splitting mechanics, 2025-2026 AI-era fintech UX, accounting depth, and finance-app security/compliance baselines. Tiered against `docs/PLAN.md`'s phases. Each item: what it is + why it's in this tier.

## (a) Phase 1 — MVP, localhost web

- **Auth**: registration + login (email/password, Argon2id hashing per ADR-0030, length-based strength policy, no complexity theater) — gate to any ledger data. No email verification or password reset yet — both need real email delivery, Phase 2 (ADR-0030).
- **Create Ledger**, personal (1 member) or group (N members) — single entity per ADR-0004, not a fork
- **Invite/add members by email** — matches Splitwise's core group flow
- **Add Expense**: amount, payer, date, category, note — minimum viable record
- **Equal split only** — simplest correct split type; variants deferred to (b)
- **Rounding-remainder rule** (largest-remainder method): when an equal split doesn't divide evenly, allocate the leftover cent(s) to the shares with the largest fractional remainder, breaking ties by member order — decided now as an explicit domain rule, not left ambiguous. This matters even for equal-split-only, so it can't wait for Phase 2's split variants.
- **Balance calculation** (who owes whom) per ledger — the core value proposition, lives in `core`'s domain layer
- **Record Settlement** (mark paid) — closes the loop without payment-gateway integration
- **Expense list/history** per ledger — baseline visibility

Phase 1 is deliberately **append-only** — no edit/delete on expenses or settlements. This is why no audit log (ADR-0012) is needed yet: there's nothing to audit until Phase 2 introduces mutation. No currency handling beyond a single default currency, no notifications, no offline support.

## (b) Phase 2–4 — near-term (web hardening → Android → iOS)

**Splitting mechanics**
- **Exact-amount, percentage & weighted/share splits** ("2 shares vs 1 share", distinct from percentage) — table stakes beyond equal split; weighted shares is Splitwise's actual "split by shares" mode
- **Itemized splits** — Splitwise's most-used advanced split type
- **"Simplify debts"** (ADR-0007's greedy settle-up algorithm)
- **Partial settlements** — pay down a balance incrementally, not all-or-nothing; matches real repayment behavior
- **Expense locking after full settlement** — prevents post-hoc balance corruption
- **Cross-group/cross-friend aggregated balance dashboard** ("you owe / you're owed" across every ledger) — this is Splitwise's actual home screen and high user value; easy to miss if each ledger is designed in isolation
- **Duplicate-expense detection** (same date + amount flagged) — cheap, real user-pain preventer

**Group & trust mechanics**
- **Group roles/permissions** (owner vs member, per ADR-0004's Membership entity — who can edit/delete) — required once balances are genuinely shared
- **Visible audit log** on expense/settlement edits — ADR-0012, ships together with edit/delete, not after
- **Group archiving** — keep old groups out of the active view without deleting history
- **Expense edit/delete with balance recalculation**

**Recurring & scheduling**
- **Recurring expenses**, with explicit edit semantics (this occurrence / this-and-future / all) — half-built without the edit semantics being decided
- **Recurring-bill detection** (pattern-inferred from history + reminder) — distinct from user-entered recurring expenses; moderate lift, real value

**Money & currency**
- **Multi-currency conversion** — essential once travel/shared-trip use cases matter

**Content & visibility**
- **Receipt attachment with itemized line-item extraction** (not just OCR-to-total) — this is now the 2025-2026 baseline (Expensify SmartScan, TaxLens, SparkReceipt all extract vendor/date/tax/line-items, not just a total), upgraded from a plain "receipt photo" ask
- **Expense search/filter**
- **Category spending breakdown**
- **Notification preference granularity** (mute a group, digest vs instant) — cheap once push notifications exist
- **Push notifications** (new expense, settle-up reminder)
- **CSV export**
- **Non-app members via email** (ghost/placeholder users) — lowers the adoption barrier for groups where not everyone installs the app

**Trust/security baseline** (ADR-0013 — table-stakes for a finance app, not optional hardening)
- **2FA** (TOTP, not SMS)
- **Encryption at rest** (AES-256) + **TLS in transit** — explicit deploy-checklist items from Phase 2's deploy step onward
- **Session auto-timeout/re-lock** (web + mobile)
- **Biometric app-lock** (mobile, Phase 3/4) — separate from account login, gates the already-secured token store (ADR-0011)

**Account lifecycle** (ADR-0030)
- **Email verification** — non-blocking, unlocks password-reset eligibility rather than gating basic usage
- **Password reset** — time-limited (1h), single-use emailed token; using it revokes all existing sessions (ADR-0008)
- **Profile management** (change display name, email, password) — not yet speced in detail, flagged so it isn't discovered missing mid-Phase-2

**Internationalization** (ADR-0031 — architecture only; no translations committed yet)
- **i18n-ready UI** (`next-intl`, every string wrapped from Phase 1) — English-only content for now, but retrofitting cost is kept low deliberately

**Sync**
- **Offline mode with sync** — the feature; the sync-protocol design itself is deferred to Phase 5 per ADR-0002

**Lower-priority, cheap to add later, not tier-committed**
- Comments/discussion threads on an expense ("did this include tip?") — genuine utility, but below the correctness/trust items above

## (c) Phase 5–6 — accounting expansion (future scope)

Phase 5 is offline-first sync itself (see `docs/PLAN.md`, ADR-0002). Phase 6 is the accounting pivot:

- **Double-entry ledger** (Account/Transaction/Entry replacing Ledger/Expense) — the ADR-0004 migration path; inherits ADR-0012's audit-log pattern rather than reinventing one
- **Multi-account tracking** (bank, cash, credit card) — baseline in YNAB/GnuCash, absent from Splitwise entirely
- **Envelope/zero-based budgeting**, specifically — not just "have budgets": category-to-category fund transfers with an available-to-spend figure derived live from the register, per YNAB/Actual Budget's actual mechanic. Naming this explicitly because it's the real differentiator, not a vague "budgets" bullet.
- **Bank reconciliation workflow** — mark transactions cleared/reconciled against a statement, lock reconciled transactions from casual edits (depends on multi-account existing, so belongs here not earlier)
- **Net worth tracking** over time, across all accounts — needs multi-account + a snapshot/history mechanism
- **Scheduled/recurring transactions** at the accounting layer — distinct from (b)'s recurring *expenses*
- **Reports**: P&L-style summaries, category budget vs. actual
- **Bank import: OFX preferred, CSV fallback** — upgraded from "CSV import"; OFX carries transaction IDs (dedup on re-import) and institution/account metadata CSV lacks
- **Multi-currency FX realized/unrealized gain tracking** — distinct from (b)'s simple split-currency conversion; only matters once real multi-currency accounts exist
- **Tax-category tagging** — for year-end export
- **GDPR export/deletion**, resolved via ADR-0014's anonymize-don't-hard-delete policy — decided now specifically because it gets structurally harder to retrofit once double-entry ships
- **Plain-text/version-controllable data option** — Beancount's niche differentiator; optional stretch goal
- **Local-first/self-hosted data option** — Actual Budget's privacy differentiator; worth evaluating once ADR-0002 is revisited in Phase 5

### Phase 6+ AI-forward candidates (emerging, not yet baseline — genuinely new tier, not a near-term fold-in)
- **Natural-language expense entry** ("I paid 500 for lunch, split with Ram and Hari") — needs NLU + entity resolution against real ledger members; real complexity, fits once the core product is stable
- **AI chat/query interface for balances** ("how much does Ram owe me?") — natural fit once Phase 6's reporting layer exists to query against
- **Proactive spend insights/nudges** — ties to Phase 6's budget/report layer (YNAB-style insights), premature before that layer exists

## Explicit non-goals

- Payment-gateway integration (actually moving money) — out of scope until there's a concrete reason
- Bank account linking (Plaid-style live sync) — large scope/compliance surface, revisit only if Phase 6 demand is real
- Multi-tenant/business accounting (invoicing, payroll) — a different product
- Emoji reactions on expenses — social-feed feature with zero balance-accuracy value, adds moderation surface for no accounting benefit
- Real-time institutional-grade fraud/anomaly ML pipeline — that's bank-fraud tooling (MindBridge/Oversight-tier), wrong scale for a personal group-expense app; the lightweight duplicate-expense flag above covers the real value at a fraction of the cost
- Blockchain-style cryptographically-immutable ledger — see ADR-0014; solves a problem this app doesn't have and worsens the GDPR tension
- Voice/behavioral biometrics — enterprise fraud-detection territory, wrong scale

## Sources

First pass:
- [Splitwise Pricing Explained](https://usefairsplit.com/blog/splitwise-pricing/), [What is Splitwise Pro?](https://kb.splitwise.com/pro/what-is-splitwise-pro)
- [Tricount vs Splitwise vs Settle Up](https://tetras-ltd.com/en/blog/tricount-vs-splitwise-vs-settle-up-best-app), [Best free bill-splitting apps](https://www.lovemoney.com/news/85624/best-free-bill-splitting-apps-tricount-splid-settle-up-acasa-splitwise)
- [Beancount vs GnuCash](https://beancount.io/compare/beancount-vs-gnucash), [Actual Budget Review 2026](https://www.expensesorted.com/blog/144_actual_budget)

Second pass — splitting mechanics & AI-era UX:
- [Tricount vs Splitwise 2026](https://splitpilot.io/blog/tricount-vs-splitwise/), [Best expense splitting apps 2026](https://splitterup.app/blog/best-expense-splitting-apps), [SplitMyExpenses articles](https://www.splitmyexpenses.com/articles)
- [Expensify duplicate detection](https://help.expensify.com/articles/new-expensify/reports-and-expenses/Duplicate-Detection), [AI receipt scanning 2026](https://receiptsync.net/blog/ai-powered-receipt-scanning-complete-guide), [TaxLens itemized scanner](https://taxlensapp.com/features/itemized-receipt-scanner), [AI in fintech 2026](https://www.technource.com/blog/ai-in-fintech/)

Second pass — accounting depth & security:
- [YNAB zero-based budgeting](https://www.ynab.com/blog/what-is-a-zero-based-budget), [Goodbudget vs YNAB 2026](https://getfinny.app/blog/goodbudget-vs-ynab-2026)
- [Finance app security/compliance 2026](https://www.indiehackers.com/post/compliance-security-and-automation-top-mobile-app-features-us-finance-companies-need-in-2026-825cfa408e), [2FA for banking apps 2026](https://www.wealthnx.ai/blog/best-2fa-methods-for-banking-apps-in-2026-security-guide/)
- [OFX vs QIF vs CSV](https://www.easybankconvert.com/articles/qif-ofx-csv-comparison), [Enforcing immutability in double-entry ledgers](https://www.moderntreasury.com/journal/enforcing-immutability-in-your-double-entry-ledger), [Immutable ledgers and GDPR](https://www.serverion.com/uncategorized/how-immutable-ledgers-impact-gdpr-compliance/)
