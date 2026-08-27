# ADR-0011: Platform-specific APIs via expect/actual (secure storage, push notifications)

## Status
Accepted

## Context
KMP's native-API-access value (expect/actual declarations) was implicit in our docs but never named concretely. Two real features already on the roadmap need it: ADR-0008's refresh-token storage needs a secure per-platform mechanism, not just "stored somewhere"; and `docs/FEATURES.md` tier (b) already commits to push notifications, which has no architecture behind it. Both are the same underlying shape: a common interface in `core`, platform-specific `actual` implementations in `app/androidApp` / `app/iosApp`.

## Decision
- **Secure token storage** (ADR-0008's access + refresh tokens): `expect` interface in `core` (`TokenStore`: save/read/clear), `actual` backed by Android Keystore (via `EncryptedSharedPreferences` or `Jetpack Security Crypto`) on Android, iOS Keychain on iOS. Never plain `SharedPreferences`/`UserDefaults` — these are auth credentials.
- **Push notifications**: `expect` interface in `core` for registering a device token and handling incoming payloads, `actual` wired to FCM on Android and APNs on iOS. Server (`server/`) stores one device-token-to-platform mapping per user session and picks the right delivery path at send time — this is backend scope, not just a mobile concern.

## Consequences
- Both are Phase 3/4 implementation items, not Phase 0/1 — recorded now so they don't get "discovered" mid-Phase-3 as unplanned work.
- `TokenStore`'s `expect`/`actual` boundary is the first real test of ADR-0001's module discipline holding up under a case where platform code genuinely can't be shared (unlike domain logic, this is inherently platform-specific) — the interface lives in `core`, the implementation correctly does not.
- Push notification delivery is a `server/` responsibility as much as a mobile one — Phase 3 scope estimates should account for backend work here, not just client work.
