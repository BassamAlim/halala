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
- WorkManager (wired to Hilt in `App`), AndroidX Biometric, Glance for the home-screen widget,
  Bouncy Castle only for Argon2id (the backup passphrase), osmdroid for the spending map
  (OpenStreetMap tiles: no Play services, no key)
- kotlinx.serialization for the JSON export
- Tests: JUnit 4, Robolectric for Room, kotlinx-coroutines-test
- Version catalog: `gradle/libs.versions.toml`. Add dependencies there, never inline.

## Commands

```bash
./gradlew :app:assembleDebug        # build
./gradlew :app:testDebugUnitTest    # unit tests (Robolectric included)
./gradlew :app:installDebug         # install on a connected device
./gradlew :app:connectedDebugAndroidTest  # SQLCipher, Keystore, migrations on a device (CI: emulator)
```

CI (`.github/workflows/ci.yml`) runs the build and unit tests, and the instrumented tests on an
emulator, on every push and PR to `main` (not `dev`), uploads
the debug APK as a run artifact, and fails if the Room schema changed without its `app/schemas`
JSON. Debug builds are signed with the release key whenever a `.env` is present (CI writes one
from the same secrets), so CI's debug APK, an Android Studio build and the releases all install
over each other. A `v*` tag runs `release.yml`: unit
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
  ai/                         merchant identification: ApiKeys (built in), GroqProtocol,
                              GroqIdentifier, AiIdentification and its worker
  data/dataSources/room/      entities, daos, relations, AppDatabase, Converters, Migrations, Seed
  data/dataSources/keystore/  DatabaseKey: the SQLCipher passphrase, wrapped by Android Keystore
  data/repositories/          the only way into storage; @Singleton + @Inject constructor
  di/                         Hilt modules for things Hilt can't construct itself
  domain/                     app-wide rules: Money, BudgetState, CashGap, Totals, TransactionItems,
                              Rules, Merchants, People, Loans, Identification, KnownMerchants
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
  half-filled form, an open dialog) is a private `MutableStateFlow` combined in. A screen
  reading ledger-wide flows works them out off the main thread (`.flowOn(Dispatchers.Default)`
  before `stateIn`); a text field's own value stays on the main thread so typing never waits
  (`ActivityViewModel`: combine the field back in after the `flowOn`).
- **The whole ledger is one shared query**: `TransactionsDao.observeAllDetails()` is shared by
  every watcher (`SharedLedgerDao`, provided in `DataSourceModule`), so it can lag a write by a
  moment. Code that reads right after writing reads fresh: `getAllDetails()`, or a one-shot like
  `BudgetsRepository.overview`.
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
- **Motion and depth** (`core/ui/Animation.kt`): every `clickable` sinks a few dp and springs
  back (`PressIndication`, the theme's indication; put `clickable` before a fill so the whole
  thing sinks). Cards are borderless on the `Card` tone, radius 20 (`Radius.lg`), padded 18 (`Insets.card`) and stacked 16 apart; screens
  stand on `ground()` (opaque Bg; no glows or coloured shadows anywhere). Figures settle in
  as one (a short rise, the bars' spring a touch quicker; no per-digit stagger: clean, not playful) and change through `RollingAmount` (only moved, never computed); bars fill with `settle()`; pushes
  glide a fifth of the width (`Emphasized`), tabs fade through. Tabs and segments tick (haptics).
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

**Before changing what a feature does, read its rule in `docs/product.md`** (money, accounts,
transfers, categories and rules, merchants, identification, people, loans, recurring, budgets,
forecast, goals, deposits, alerts, digests, assets, net worth, zakat, reminders, tags, places,
undo, lock, encryption, backups, restore, privacy, the assistant) and **update it in the same
change**. Its *Current state* says which screens exist and which board each follows. These
guardrails hold for every change:

- **Money is a `Long` of minor units** with its ISO code; only `core/domain/Money.kt` turns text
  into money and back, and every amount on screen goes through `Money.format` (so hiding
  amounts hides it).
- **The phone holds the only ledger**: a schema change bumps the version with a migration in
  `Migrations.kt`, never a destructive fallback; a database whose key is missing is refused.
  `connectedAndroidTest` keeps the app installed (`gradle.properties`) because uninstalling
  wipes it, and instrumented tests use their own `smoke-*` files.
- **What leaves the phone** is only what `docs/product.md` › Privacy lists; nothing about money
  goes in DataStore (it isn't encrypted).
- **Filing changes** (categories, rules, merchants, a transaction's category) go through
  `ClassificationRepository.audited`, so they can be undone.
- **Kept in step**: a new table or column that matters goes into `ExportFile`, `Exporter` and
  `Importer` together (bump `schemaVersion`); a ledger rule that changes what counts goes into
  `Asking.VIEW` too; a new list of accounts filters by `AccountType.listed`.
