# Spec: <feature name>

Per ADR-0016. Fill this in before implementing the feature; keep it updated alongside the code.

## Summary
One or two sentences: what this feature does and which `docs/FEATURES.md` tier item it implements.

## Acceptance criteria (EARS format)
- AC-1: WHEN <trigger/condition>, THE system SHALL <observable behavior>.
- AC-2: WHEN <trigger/condition>, THE system SHALL <observable behavior>.
- (Add one per distinct behavior, including edge cases — e.g. rounding remainders, empty groups, invalid splits.)

## Out of scope
What this feature deliberately does NOT do yet (links to the FEATURES.md tier where the deferred part lives).

## Test plan
- Which `commonTest` cases map to which AC — one row per AC is the target, not a hard rule.
- Any integration-test-only scenarios (`ktor-server-test-host`, per ADR-0009).

## Human-review-required?
State yes/no per ADR-0017's lanes (auth, money-math/settle-up, deletion/GDPR always yes).
