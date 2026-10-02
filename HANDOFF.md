# Handoff: merchants (Phase 2), unbuilt

Picked up from a session that couldn't build. Delete this file once the work below is verified
and merged.

## State

- Branch `ccr-81f31f77-jbkqt3`, two commits on top of `dev` (`d3e4664`):
  - `3f98c3a` Add merchants: one per business however its bank spells it
  - `8f6a735` Point CLAUDE.md at the spec copy in the repo
- **Nothing here has been compiled or tested.** The previous container had no Android SDK and
  its network policy blocked `dl.google.com` (SDK, Google Maven) and `api.foojay.io` (Gradle's
  JDK 25 toolchain). Every line was checked by reading only.
- CLAUDE.md (Product rules › Merchants, Undo, Exports, Current state) already describes the
  feature as built.

## What was built

- `merchants` and `merchant_aliases` tables, and a `transactions.merchantKey` column. The
  database is now version 4, with `Migration3To4` and `app/schemas/.../4.json`.
- `core/domain/Merchants.kt`: `key` normalises a descriptor (letters only, trailing places and
  company words dropped), `nameOf` names a new merchant, and `similarity`/`similarTo` match
  spellings (bigram Dice ≥ 0.85, at least 5 letters).
- `ClassificationRepository`:
  - `applyRules()` now resolves merchants first: it keeps keys in step with titles, then gives
    each key not seen before an alias (purchases, refunds and bills only).
  - `learn` teaches a rule the merchant (`RuleConditions.merchantId`).
  - New `renameMerchant`, `mergeMerchants` and `splitAlias` are audited, so undo covers
    MERCHANT and ALIAS entities.
  - A `Mutex` (`writing`) serialises `applyRules`, `audited` and `undo`.
- `Rules`:
  - `MerchantLookup` maps a key or a merchant name to its merchant.
  - `matches`/`matcher` compare merchants by identity.
  - `clusters` groups by merchant.
- Wiring:
  - `TransactionDetail` gets `merchantId`/`merchantName` through `DETAIL_SELECT`, and
    `titleOf` shows the merchant name.
  - `MainViewModel` → `MainDomain.catchUp()` runs `applyRules` when the app opens, which fills
    merchants in after the upgrade.
- UI:
  - New `features/merchants` (list, from Settings) and `features/merchant` (rename, spellings
    with "Not this one", "Same as another merchant", transactions).
  - Transaction detail has a Merchant row, and its origin card says how the bank wrote it.
  - A Review card covering many transactions opens its merchant.
  - Activity search also matches the merchant name.
- Export: JSON schema 3 (`merchants`, `merchantUid` on transactions and rules); CSV gains a
  `merchant` column.
- Tests:
  - New `MerchantsTest`, and new merchant tests in `ClassificationRepositoryTest`.
  - New migration test for 3→4.
  - Updated `ExporterTest`, and the `ClassificationRepository` constructor calls in the SMS
    tests.

## To do first

1. `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest`, then fix whatever
   breaks. Likely spots:
   - Room query verification of the new DAO queries (`MerchantsDao`, `DETAIL_SELECT`, the
     `json_extract` on `$.merchantId` in `ClassificationDao.observeRules`).
   - Smart casts in `ClassificationRepository.learn`.
   - The new tests' expectations.
2. Check `git status app/schemas` is clean after the build. `4.json` was written by hand, with
   Room's identity hash recomputed by a reimplementation that reproduces 1–3.json exactly. If
   Room writes a different file, commit Room's version.
3. Ask the owner whether to open a PR (CI only runs on PRs/pushes to `main`).

## Environment

The build needs JDK 25 (from `gradle/gradle-daemon-jvm.properties`; Gradle fetches it via
foojay) and the Android SDK (from `dl.google.com`). If either is missing, see CLAUDE.md's
Commands section and set `sdk.dir` in `local.properties`. A setup script that preinstalls both
would save the download each session.
