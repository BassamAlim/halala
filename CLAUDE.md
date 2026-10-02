# CLAUDE.md

Guidance for Claude Code working in this repository.

## What Halala is

Halala (هللة, "every halala counted") is a single-user Android finance app. It will read bank
SMS, turn each into a structured transaction, and use deterministic rules first and AI second so
the owner's job is reviewing suggestions, not typing. It is private by default: the full ledger
lives only on the phone, in an encrypted database, and only minimal scrubbed text will ever leave
it for AI. It is sideloaded (APKs from GitHub Releases), never published to a store.

Sources of truth, read before writing code:

- **Spec** (product, data model, AI pipeline, roadmap):
  `https://claude.ai/code/artifact/198e29a7-8f36-4d4b-8c39-ada8fb94df56`; a copy is checked in at the
  root (`Personal Finance App — Product & Technical Spec.md`), which may be the newer of the two
- **Design system** "Halala Design System" (tokens, components, voice, Compose mapping):
  `https://claude.ai/artifact/S6fcpdnuBUEso5u7ojecaj`
- **Screen designs** "Halala design canvas", 22 phone screens on its *Screens* page (Home and its
  budget states, Review inbox, Activity, Transaction detail, Net worth, Plan, Forecast,
  Subscriptions and bills, Retirement, Zakat, People, Person, Savings, Money flow, Assistant,
  Digest, Settings, Rules, onboarding): `https://claude.ai/artifact/7G8KxznMhsrFhsC6HGC6A2`

**Build screens from those boards rather than inventing layouts.** Where the spec and the
designs disagree, ask the owner.

## Stack

- Kotlin, Jetpack Compose, Material 3 — AGP 9 with built-in Kotlin support (do **not** apply
  `org.jetbrains.kotlin.android`; AGP 9 rejects it). minSdk 29 (Android 10), target 37.
- Hilt for DI, KSP for annotation processing (never KAPT)
- Room over **SQLCipher** (`net.zetetic:sqlcipher-android`) for the ledger, DataStore
  Preferences for settings that aren't money
- Navigation Compose with **type-safe routes** (`@Serializable` destinations in `core/nav/Screen.kt`)
- WorkManager (wired to Hilt in `App`; workers arrive with later phases), AndroidX Biometric
- kotlinx.serialization for the JSON export
- Tests: JUnit 4, Robolectric for Room, kotlinx-coroutines-test
- Version catalog: `gradle/libs.versions.toml`. Add dependencies there, never inline.

## Commands

```bash
./gradlew :app:assembleDebug        # build
./gradlew :app:testDebugUnitTest    # unit tests (Robolectric included)
./gradlew :app:installDebug         # install on a connected device
```

CI (`.github/workflows/ci.yml`) runs both on every push and PR to `main` (not `dev`), uploads
the debug APK as a run artifact, and fails if the Room schema changed without its `app/schemas`
JSON. A `v*` tag runs `release.yml`: unit
tests, a release APK signed from the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and
`KEY_PASSWORD` secrets (unsigned, with a warning, if any is missing), attached to a GitHub
Release with its SHA-256. It can also be run by hand from the Actions tab with a tag name: it
builds `main` (or the given ref) and creates the tag on the commit it built. Bump `versionCode`/`versionName` in `app/build.gradle.kts` before
tagging. Locally, a `.env` at the root with the same four keys (`KEYSTORE_PATH` instead of the
base64) signs `assembleRelease`; it is gitignored.

## Architecture

MVVM over a single data layer. Dependencies point one way: UI → ViewModel → Domain → Repository
→ DAO/DataStore. Nothing below the ViewModel knows about Compose; nothing above the repository
knows about Room.

```
core/
  data/dataSources/room/      entities, daos, relations, AppDatabase, Converters, Migrations, Seed
  data/dataSources/keystore/  DatabaseKey: the SQLCipher passphrase, wrapped by Android Keystore
  data/repositories/          the only way into storage; @Singleton + @Inject constructor
  di/                         Hilt modules for things Hilt can't construct itself
  domain/                     app-wide rules: Money, BudgetState, CashGap, Totals, TransactionItems,
                              Rules, Merchants
  enums/                      shared enums (AccountType, Direction, TransactionKind, …)
  export/                     the CSV and JSON exports (Exporter, Csv, ExportFile)
  lock/                       LockManager: when the biometric lock asks again
  models/                     models that aren't rows (drafts, TransactionItem)
  nav/                        Screen (routes), Navigator, Navigation (the graph)
  ui/theme/                   design tokens: Color (HalalaColors), Type, Dimens, Shape, Theme
  ui/components/              shared composables (the design system's components)
  ui/Labels.kt                enums and DayLabels → string resources
  utils/                      small pure helpers (Labels: dates, account names)
features/<feature>/
  <Feature>Screen.kt          composables only
  <Feature>ViewModel.kt       @HiltViewModel; exposes one StateFlow<UiState>; no Android types
  <Feature>UiState.kt         immutable state + already-formatted UI models
  <Feature>Domain.kt          the feature's business logic and rules, injected into the ViewModel
```

Rules that matter:

- **Repositories are `@Singleton class X @Inject constructor(...)`** — no module needed. Only
  things Hilt cannot construct (Room, DataStore, Clock, dispatchers) get a `@Provides`.
- **ViewModels expose exactly one `StateFlow<UiState>`**, built with `combine(...).stateIn(...)`
  and `SharingStarted.WhileSubscribed(5_000)`. Screens read it with
  `collectAsStateWithLifecycle()`. What a screen owns rather than the database (a search, a
  half-filled form, an open dialog) is a private `MutableStateFlow` combined in.
- **Rules live in the Domain's companion as pure functions** (`EditAccountDomain.validate`,
  `EditTransactionDomain.validate`, `ReconcileCashDomain.draftFor`) so they are tested without
  storage; the Domain's instance methods do the reading and writing.
- **No date or money maths in composables.** The ViewModel emits formatted strings.
- **`java.time.Clock` is injected**, never `LocalDate.now()` / `Instant.now()` in logic.
- **Navigation goes through `Navigator`** (injected into ViewModels), which emits commands on a
  channel. Nothing outside `core/nav` touches a `NavController`. Add a destination by adding a
  `@Serializable` entry to `Screen` and a `composable<Screen.X>` to the graph. The five tabs are
  local state in `MainScreen`, not destinations.
- Room queries return `Flow`; writes are `suspend`. **Bump the DB version and write a
  migration** in `Migrations.kt` — never a destructive fallback: the phone is the only copy of the
  ledger. Schemas are exported to `app/schemas` and CI checks they're committed.
- **Every word on screen is a string resource** (`res/values/strings.xml`), so Arabic can be
  added as `values-ar` later. ViewModels hand over enums and `DayLabel`s; `core/ui/Labels.kt`
  turns them into words. Layouts use start/end, never left/right; the back and chevron glyphs are
  `autoMirrored`.

## Design system

Tokens live in `core/ui/theme/` and are the only source of colour, type, spacing and radius.
Don't hardcode hex values or `.dp` literals that aren't a named token in `Dimens.kt`
(`Spacing`, `Sizes`, `Insets`) or `Shape.kt` (`Radius`).

- **Dark only** ("Ink Block Jade"). No light scheme, no dynamic colour. `HalalaTheme` takes no
  parameters and wraps everything in a `Surface(color = Bg, contentColor = Text)`.
- **Colour is meaning.** `HalalaColors` maps the tokens one-to-one. Jade `Accent` is the brand
  and the action — at most one or two jade things per screen. **Spending amounts are never
  coloured** (plain `Text`, leading −); income is `Income` (jade, leading +); moves between your
  own accounts are `TextMuted` with no sign; `StateOver` coral is only for over budget, anomalies
  and destructive confirms. `Info` is for neutral hints that must not read as money.
- **Budget state** (`core/domain/BudgetState`): under 80% `OK` (jade), 80–100% `WARN` (amber),
  over 100% `OVER` (coral). It picks the `BalanceCard` fill and `ProgressBar` colour, compared in
  exact integers.
- **Type**: Instrument Sans for words, IBM Plex Mono for **every number**, with tabular figures
  (`HalalaNumbers`, `fontFeatureSettings = "tnum"`). Both are bundled OFL fonts in `res/font`
  (not downloadable fonts: nothing is fetched, no Play services). `HalalaType` holds the Text
  styles by token name; `Typography` maps them onto Material slots.
- **Icons are the boards' own stroke glyphs** (24 grid, 1.7 stroke, round caps and joins),
  transcribed into `res/drawable/ic_*.xml` and tinted in code. There is no `material-icons`
  dependency — don't add one; port the glyph from a board.
- **The logo** is the design's final mark (canvas *Logo* page, "Final · ه coin with inner
  ring"; design system *Logos* group): a jade coin with a thin inner ring and ه cut out, one
  even-odd shape. `ic_halala_mark` is it cropped to the coin (Home's header at 26dp, the lock at
  64dp; never tinted, 32dp minimum elsewhere). The launcher icon is the design's app icon on
  the same 108dp adaptive grid (`ic_launcher_foreground` over bg, plus a monochrome layer for
  themed icons). The small `halala-glyph` (no ring, for under 32dp) is for the notification
  icon when notifications arrive.
- **Touch targets are at least 44dp** (`Sizes.touchTarget`); a 32dp chip pads its hit area.
- Components (`core/ui/components`): `HalalaCard`/`SummaryCard`/`ListCard`+`ListRow`,
  `BalanceCard`, `HalalaButton` (Primary: one per section; Secondary; destructive = secondary with
  coral text), `HalalaChip` (Plain, Outline, Accent = active filter, On = selected choice),
  `AutoBadge`, `TransactionRow`/`Avatar`/`GroupLabel`, `TopBar` (sub-screens) and `ScreenTitle`
  (tabs), `BottomNav`, `SearchField`/`HalalaTextField`, `ProgressBar`, `SegmentedControl`,
  `HalalaSheet`/`ConfirmSheet`/`ChoiceSheet`, `QuickAddButton`, `FormField`/`ChoiceChips`.
- Voice: short, plain, second person; figure then currency ("6,240 SAR"); thousands separators
  always; the true minus sign; two decimals in lists and detail, none in summaries; no
  exclamation marks, no emoji.

## Product rules

These are decided (mostly by the spec); don't re-litigate them in code.

- **Money is integer minor units, never floating point.** Every amount is a `Long` count of
  halalas/cents (`amountMinor`) next to an ISO 4217 code. Text becomes minor units and minor
  units become text only in `core/domain/Money.kt`, through exact decimals: `parse` (grouping in
  threes, the currency's own decimal count, Arabic-Indic digits accepted, overflow rejected),
  `format` (Latin digits, commas, `−`, optional `+`, summaries round half away from zero),
  `plain` (ASCII, for files) and `sum` (`Math.addExact`: overflow fails loudly). Balances are
  summed in SQL over integers.
- **A transaction's amount is always positive; `Direction` carries the sign**, and a transaction
  is always in its account's currency. `TransactionsRepository` enforces both, so callers can't
  get them wrong.
- **Accounts are yours to name**, several per bank, told apart by the **last four digits** the
  bank's SMS quote (required for anything a bank holds; unique per bank by index, checked on
  Save with a message naming the account that has them). Accounts are **archived, never
  deleted** — their history points at them. An account's currency is fixed once it has
  transactions. Every account and transaction has a stable `uid` for exports and re-import.
- **The cash wallet** is seeded with the database (`Seed.kt`, with the v1 institutions). ATM
  withdrawals and deposits are a **Move** between the bank and the wallet. The wallet's balance
  is corrected by **counting it** (`ReconcileCash`): less than recorded becomes one
  "Cash spending" purchase (source `RECONCILE`, counted as spending, awaiting a category); more
  becomes an `ADJUSTMENT` (neither income nor spending).
- **Internal transfers** are two legs (a debit and a credit) paired in `internal_transfers`,
  created, edited and deleted together. They are excluded from spending and income
  (`TransactionKind.countsInTotals` and `inOut`), shown once in the feed by their sending leg
  ("Salary → Cash", muted, dashed swap avatar), or by the account's own leg when the feed is
  filtered to one account. A move across currencies is refused until FX lands with SMS parsing.
- **Totals** (Activity's In/Out) are per currency, in SAR for now, this calendar month (pay
  cycles arrive with budgets).
- **Categories and rules**: only spending has a category (money out that counts in totals;
  `Rules.canCategorise`). A transaction with a category and no `ruleId` was filed by you, and no
  rule ever changes it; one with a `ruleId` was filed by that rule and shows the Auto badge.
  Choosing a category for a merchant asks "just this one, or always": always writes one learned
  rule per merchant (taught the merchant's id, so it files every spelling) and files its past too.
  `ClassificationRepository.applyRules()` is idempotent and runs after every SMS run, every
  manual save and every rule change. A rule's use count is counted from the transactions it
  filed, not stored. Rule conditions and actions are JSON columns, so new kinds need no
  migration; all that are set must hold. Yours beat learned, learned beat AI, then the rule with
  more conditions, then the newer. Removing a category deletes the rules that file under it and
  sends its transactions back to review.
- **Merchants**: a `Merchant` is a business however its bank spells it; each spelling is a
  `MerchantAlias` keyed by `Merchants.key` (lower case, letters only, trailing places and company
  words dropped: "PANDA 1042 RIYADH" is `panda`). Every transaction stores its title's key
  (`merchantKey`, kept in step by `TransactionsRepository`) and finds its merchant through the
  alias, so merging or splitting moves aliases, never transactions. `applyRules` first resolves
  keys not seen before, oldest first, for purchases, refunds and bills only (a transfer's title
  is a person): a key spelled like a known alias (`Merchants.similarTo`, bigram similarity ≥
  0.85, five letters or more) joins that merchant, else it starts one named after it. Feeds
  show the merchant's name; the title keeps the bank's words, and Transaction detail says
  both. You rename, merge ("Same as another merchant": the merged-into merchant's learned
  answer stands) and split ("Not this one": the spelling becomes its own merchant and what
  rules filed under it goes back to review) on the Merchant screen. The app runs `applyRules`
  on opening, which fills merchants in after the upgrade.
- **Undo**: everything you do to filing (an answer, "always", saving, switching or deleting a
  rule, deleting a category, renaming, merging or splitting a merchant) is one `AuditBatch`:
  `ClassificationRepository.audited` snapshots categories, rules, merchants, aliases and every
  transaction's filing before and after, and stores the rows that differ. `undo` puts each row back only where it still reads as the batch left it, so nothing
  done since is overwritten. What rules file on their own as SMS arrive is not a batch (the
  transaction names its rule). New mutations of categories, rules or filings go through
  `audited`.
- **Notifications**: only the review reminder (off by default; daily or weekly at a time, set in
  Settings), which shows a count of merchants and never an amount, says nothing when the inbox
  is empty, and can be put off an hour or a day. It is periodic WorkManager work, so it can run
  late in Doze. Tapping it opens the app (lock, then Home), not Review directly.
- **Every automated decision says why** (the spec's principle): Transaction detail's
  "How it got here" card names the source (added by you, a wallet count, or an SMS), and
  "Filed automatically" names the rule.
- **Lock**: `BiometricPrompt` on every cold start (the graph starts on `Screen.Lock`) and after
  a minute in the background (`LockManager`, monotonic clock; the minute is a preference,
  `PreferencesRepository.lockTimeoutSeconds`, with no UI yet). Always on; strong (class 3)
  biometrics with the device credential as fallback (Android 10 can't combine those, so there it
  accepts any biometric plus credential). A phone with no screen lock opens straight through —
  you can never lock yourself out. `FLAG_SECURE` is always set (no screenshots, blank in
  recents); the spec's toggle for it comes with the security settings.
- **Encryption at rest**: the whole Room database is SQLCipher. Its 32-byte random passphrase is
  stored only wrapped by an AES-256-GCM key in Android Keystore (`DatabaseKey`), in
  `noBackupFilesDir`. That Keystore key is **not** bound to user authentication, on purpose: the
  SMS receiver and workers must write while the phone is locked, and an auth-bound key dies when
  a fingerprint is enrolled, which would lose the ledger. The lock guards the UI; the key guards
  the file. Binding it later only re-wraps the same passphrase. A database whose key is missing
  is refused loudly, never replaced with an empty one.
- **No Android backup**: `allowBackup="false"` and every domain excluded from cloud backup and
  device transfer — a copy could never be decrypted elsewhere. Data moves by Halala's own export
  (and, in Phase 6, the encrypted `.halala` backup).
- **Exports** (Settings › Backup and export) are written to a file you pick (SAF). CSV: a zip of
  `accounts.csv` and `transactions.csv` (UTF-8 with BOM, CRLF, signed decimal amounts plus exact
  `amount_minor`, local times, text cells defused against spreadsheet formula injection). JSON:
  `ExportFile`, schema-versioned (`schemaVersion`, bump on any shape change; 2 added categories
  and rules, 3 merchants with their aliases), keyed by `uid`s, amounts in minor units. The screen says plainly that exports aren't encrypted.
- **Privacy**: no analytics, no crash reporter, no network in Phase 0. Nothing about money goes
  in DataStore (it isn't encrypted).
- The spec's global quick-add is a flat jade `QuickAddButton` on Home and Activity (the boards
  don't draw one). It opens the transaction form on the cash wallet: Out / In / Move, amount,
  account, kind, where or who, when, note.

## Current state

**Phase 0 (Foundations)** is built: project setup, Room over SQLCipher with the Keystore-wrapped
key, the theme and shared components, the biometric lock, the five-tab shell, accounts (add, name,
archive, several per bank by last four), manual transactions and moves, the cash wallet with its
count, CSV/JSON export, and the CI and release workflows.

Screens and where they come from: **Home** (Home board: mark and wordmark, wallet and banks in the
summary-card grid, Recent; the balance card waits for budgets, the review pill for the inbox),
**Activity** (Activity board: search, account filter chips, month In/Out, rows by day; Money flow
waits for Phase 6), **Transaction** (Transaction detail board, minus category, tags, location
and SMS), **Settings** (Settings board, only the rows that are true today; reached from a gear
on Home, since the boards don't show where Settings lives), and **Plan**, **Wealth**,
**Assistant** as placeholder tabs (Wealth already links to Accounts). No board draws
**Accounts**, **Account** (add/edit), **New transaction**, **Count your wallet**, **Backup and
export** or **Locked**: they are built from the system's components (Settings' list card, the
onboarding board's bank/••digits/name rows, the segmented control) — replace them when boards
exist.

**Phase 1 (SMS core)** is built in `core/sms`: the receiver and worker (`SmsReceiver`,
`SmsWorker`), per-bank parsers (`BankFormats`, `SmsParser`) with fixture tests, the ingest
pipeline (`SmsIngest`: routing by last four, dedupe, pairing internal transfers, balance
checkpoints) and back-import (`SmsImport`), plus **Onboarding** (the onboarding board).
Amounts show the riyal sign for SAR (`Currency.kt`), the ISO code otherwise.

**Phase 2 (classification and learning)** is built as far as it goes without a network:
categories and expense types (seeded; `Category`, `ExpenseType`), rules (`Rule`,
`core/domain/Rules`, `ClassificationRepository`), the history of changes with undo
(`AuditBatch`, `AuditChange`), and the review reminder (`core/reminders`). Screens: **Review**
(Review board: one card per merchant, biggest first, with the last answer's undo; reached from
Home's review pill), **Rules** (Rules board; from Settings and from a transaction's "Filed
automatically" card), category and type on Transaction detail, and, with no board, built from
the system's components: **Categories** (add, tap to remove), **Rule** (the form: merchant is,
description contains, account, amount range → category and type), **Recent changes** (each
with Undo), the reminder sheet in Settings, and **Merchants** (from Settings: every merchant,
busiest first, with search) and **Merchant** (from Transaction detail's Merchant row, a Review
card for many, or the list: rename, what was spent, how the bank writes it with how each
spelling joined, "Not this one", "Same as another merchant", its transactions). Still to come
in Phase 2: renaming and two-level categories, the Review board's suggestion parts, merchant
logos and locations, and AI classification with scrubbing (Groq's data-retention question in the spec is still open:
nothing is sent until the owner settles it).
