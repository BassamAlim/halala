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
  sends its transactions back to review. Categories are one level (two levels were dropped from
  the spec); you rename them, and choose the business types each takes, on Categories.
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
- **Identifying merchants** (the AI only identifies; code decides the rest): a merchant records
  what the business is (`BusinessType`, a fixed list), who said so (`IdentifiedBy`: Halala's
  bundled `KnownMerchants` list, the AI, you, or `WITHHELD`) and the AI's confidence (0–100). A
  category takes some business types (`Category.businessTypes`, one category per type, seeded
  for the defaults; mixed ones like a department store start in none), and the expense type is
  the category's own. `Identification.tierOf` is the spec's tiers: what the list or you said, or
  the AI at 90 or more, of a type a category takes, is **Auto**: `applyRules` keeps one
  automatic rule per such merchant (source AI, "Merchant is Panda → Groceries"), made once (an
  automatic rule you delete stays deleted, `Merchant.autoRuled`) and re-pointed when a category's
  types change; yours and learned rules still beat it. 60–89 is **Suggest** (Review chooses the
  category, one tap to confirm, which learns as usual); the rest is **Ask**. `applyRules`
  identifies from the bundled list first, with no call; the AI only sees merchants still unknown
  that have unfiled spending. **Only a merchant's name, as the bank wrote it (digits kept), ever
  leaves the phone** for identification (the assistant sends your question, see below), and never one holding your accounts' or cards' last four digits, ten or
  more digits, or an IBAN (`Identification.sendable`; those are `WITHHELD` for you). The AI is
  Groq (`qwen/qwen3.8-27b`, strict JSON schema, reasoning off), always on, with no setting. Its key is built in, not typed: `BuildConfig.GROQ_API_KEY`, from `GROQ_API_KEY` in
  `.env` locally or the repository secret of that name in CI (a build without it
  identifies from the bundled list only). `IdentifyWorker` (online only, one at a time)
  runs after every SMS run and as the app opens, in batches of 40 names, the busiest first;
  what it says is recorded without a batch (the rule names why), and each merchant is asked once.
- **People** (the spec's counterparties): a transfer's title (`People.KINDS`: transfers and the
  loan kinds, never a move between your own accounts) names a `Person`, found as merchants are,
  through a `PersonAlias` keyed by the transaction's `merchantKey`, so merging or splitting moves
  aliases, never transactions. `applyRules` gives each name not seen before a new person
  (`People.nameOf`: digits dropped, capitals put in title case); unlike merchants, a name never
  joins a look-alike ("Ahmed Ali" and "Ahmed Saleh" are two people). Feeds show the person's
  name. You rename, merge ("Same as another person") and split ("Not this one") on the Person
  screen; these aren't audited (nothing is filed by them, and each can be taken back by hand).
  Known IBANs and phone contacts aren't linked yet.
- **Loans** are only ever made by your say. Marking a plain transfer to or from someone
  (`Loans.MARKABLE`, never a paired move) as lent or borrowed opens a `Loan` with that person
  (optional due date); marking a transfer back as repaying it pays it down; forgiving lets go of
  the rest. A loan is its `LoanEvent`s (lent, repaid, forgiven): one linked to a transaction takes
  that transaction's amount and time, so editing the transfer never leaves the loan behind; what
  is owed is lent less repaid and forgiven, never below zero. A linked transfer's kind becomes
  `LOAN_GIVEN`/`LOAN_RECEIVED`/`LOAN_REPAYMENT`, which count as **neither spending nor income**,
  take no category, and survive editing; "Not part of a loan" (or deleting the transfer) makes it
  a plain transfer again, and when it was all that was lent the loan goes and its repayments are
  freed. **Splits**: spending you paid can be split with people (equally, the remainder staying
  yours, or by amount, no more than the whole): each share is a loan owed to you with
  `splitOf` the purchase and an event without a transfer, and only your share counts as
  spending (`TransactionDetail.yourMinor`, used by `inOut` and a merchant's spent). Someone no
  transfer names can be added by name to split with. Undoing a split drops its loans and frees
  their repayments. Transaction detail asks whether a transfer repays the person's oldest open loan
  (`Loans.repaidBy`) and offers marking it as a loan. Merging people moves their loans.
- **Subscriptions, bills and planned payments** are one `RecurringSeries` (the spec's
  RecurringSeries): kind (`SUBSCRIPTION`, `BILL`, `PLANNED` for family support and the like),
  amount every N days/weeks/months/years from an `anchor` (occurrences count from the anchor, so
  the 31st never drifts), auto-renew, an optional end date (a contract), an optional reminder lead
  time. Charges aren't stored: they are its merchant's spending (or, for `PLANNED`, transfers to
  its person), read from the ledger (`Charges`); each one from the anchor on (up to half a cadence
  early) moves the next due date on, and a series with neither merchant nor person is taken as
  paid when its day passes. A linked one whose charge is more than `Recurring.GRACE_DAYS` late is
  "missed"; a last charge above the known price is a price rise ("Keep it" takes the new price,
  "Remind me to cancel" also asks for a reminder before it renews). `Recurring.detect` proposes
  (status `PROPOSED`) a payee charged three times or more at a steady week, month or year, the
  last recently: a subscription when the amount barely moves (5%), a bill when it moves some
  (50%), planned for a person; you add or dismiss it (dismissed stays dismissed). It runs on
  opening and on the screen. Monthly and yearly totals are exact integers, rounded half up. Merging
  merchants or people moves their series.
- **Pay cycle** (`PayCycles`): salary to salary. Pay days are `SALARY` credits at least half the
  usual salary (median of the last three) and 20 days apart (an allowance after it doesn't start
  a cycle); the cycle ends a month after the last, and runs on while a salary is late. With no
  salary in 45 days it is the calendar month.
- **Budgets** (`Budget`, `core/domain/Budgets`): a limit each pay cycle on everything, a category,
  an expense type or a merchant, optionally rolling over what was left last cycle. Spending is
  money out that counts in totals, your share of it; never stored, read from the ledger.
  `BudgetState` colours it, and spending further through the budget than through the cycle by
  10% or more reads as "spending fast" (the warn look) even under 80%. The Everything budget
  drives Home's balance card.
- **Forecast** (`Forecasts`, inputs from `ForecastRepository`): spendable money is current,
  card, wallet and cash accounts (savings and investments are left alone). Variable spending is
  spending outside what active subscriptions and bills charge; its daily rate per recent cycle
  (the last three, or this one so far after a week) gives the median and, from the slowest and
  fastest, the band. End of cycle = balance − scheduled before the next salary − rate × days
  left. A day-by-day path adds a salary on each expected pay day; "Can I afford it?" puts the
  amount on its day and reports the lowest balance from then to a month past the cycle, and
  whether it breaks this cycle's Everything budget. Months ahead: salary − scheduled − a
  month at the median rate. All in exact integers.
- **Savings goals** (`SavingsGoal`, `Goals`): a target, an optional date and the accounts it is
  saved in; what is saved is their balances. A month's saving needed = what is left over the
  months to the target month (rounded up); "you averaged" = the net flow into those accounts
  over the last three months, a third of it.
- **Anomaly alerts** (`Anomalies`, last 30 days, found in the ledger, never stored): the same
  merchant, amount and account twice within a day; a charge over three times the merchant's
  median (four or more before it) and at least 100 above it; a foreign-currency charge; a
  declined card (a `DECLINED` message); a bank balance that isn't the one before plus what was
  recorded between (a missed or doubled SMS). Only dismissals are stored (`dismissed_alerts`, by
  key; "Normal for it" quiets a merchant's large ones); a restore clears them. Home shows a row
  while any stand; the daily reminder work notifies a count of new ones, never what or how much.
- **Digests** (`Digests`, `DigestRepository`): weekly (Sunday to Saturday, the Saudi week),
  monthly and yearly, built from the ledger whenever one is opened, never stored (so "past
  digests are kept" means every finished period can be opened). Spending against the period
  before (whole percent), income less spending, what is owed to you, the top five categories,
  and up to three observations: a category moved 25% and 100 or more against its average over
  the three periods before, a price rise with the new monthly total, a loan due within two
  weeks (rule-based; AI-written ones come with the assistant). Each kind is off until you turn
  it on in Settings; the daily reminder work notifies the morning after a period with spending
  ends. Net worth's change joins with Phase 5.
- **Assets** (`Asset`, `Assets`): funds (units × unit price), gold (grams × karat/24 × the 24k
  gram price, less a dealer's spread), and cars, property and the rest (a value you give, less a
  yearly depreciation, compounded). Quantities and prices are exact decimal text; a value is
  rounded once, half up, to minor units, and one that can't be read is worth nothing. **Prices
  are entered by you**: the spec's NAV scraper and gold-price API aren't built, since they would
  be network use beyond merchant identification (the owner's call).
- **Net worth** (`NetWorth`): accounts by class (current/card/wallet/cash, savings, investment),
  assets, owed to you, less what you owe people. The timeline is read back from the ledger (each
  later day's money in and out of your accounts undone, loans as they stood), with assets from a
  daily snapshot (`net_worth_snapshots`, taken on opening, by the daily work and on saving an
  asset).
- **Zakat** (`Zakat`, `ZakatProfile`): 2.5% of what you choose to count (accounts, savings,
  funds, gold, owed to you) less debts due now (what you owe people plus any you add), when it
  reaches the nisab of 85 g of gold at the price you give (else your gold's); due on your Hijri
  day in the Umm al-Qura calendar (`java.time.chrono.HijrahDate`), the hawl the year before it.
  "Mark as paid" records the Hijri year; a reminder two weeks before is optional.
- **Retirement and compound interest** (`Planner`): exact decimals throughout (BigDecimal,
  128-bit), saving compounding monthly at the yearly rate over twelve, inflation taken off by
  the year, the income a pot supports at a 4% yearly withdrawal, and the extra monthly saving
  that would close a gap; rounded to minor units once. The planner starts from your savings,
  funds and gold and from what you saved a month over the last three; scenarios are kept
  (`retirement_scenarios`) to compare. The board's illustration compounds yearly, so its
  figures differ slightly from these.
- **Reminders for what is due** (`DueReminders`, daily at nine, periodic WorkManager work): a
  bill or subscription its lead time before it is due, a cancel reminder three days (or its lead
  time) before it renews, an open loan on its due day. Names and days, never an amount. Choosing a
  reminder or a due date asks for notification permission.
- **Undo**: everything you do to filing (an answer, "always", saving, switching or deleting a
  rule, editing or deleting a category, renaming, merging or splitting a merchant, saying what a
  merchant is) is one `AuditBatch`:
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
  "Filed automatically" names the rule (and, for an automatic one, what the merchant was
  identified as, by whom and how sure).
- **Lock**: `BiometricPrompt` on every cold start (the graph starts on `Screen.Lock`) and after
  a minute in the background (`LockManager`, monotonic clock; the minute is a preference,
  `PreferencesRepository.lockTimeoutSeconds`, with no UI yet). Always on, so Settings has no row for it; strong (class 3)
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
  and rules, 3 merchants with their aliases, 4 business types on merchants and categories, 5 all
  a restore needs: raw bank messages, account refs, balance checkpoints, full rule conditions,
  6 people with their aliases, 7 loans with their events, 8 subscriptions and bills, 9 a loan's
  split purchase, 10 budgets, 11 savings goals, 12 assets and their snapshots, 13 the zakat
  method, 14 retirement scenarios, 15 savings terms),
  keyed by `uid`s, amounts in minor units. The screen says plainly that exports aren't encrypted.
- **Restore** (same screen, "Restore from JSON"): `Importer.read` turns a schema-5 or later export into
  rows numbered afresh (pure; refuses older or newer schemas and dangling uids), you confirm,
  and `RestoreDao.replaceAll` replaces the whole ledger in one transaction. It is a full
  replace, not a merge. The history of changes (undo) and DataStore settings aren't carried.
  A new table or column that matters must be added to `ExportFile`, `Exporter` and `Importer`
  together; `ImporterTest` checks that a restored export exports again as the same file.
- **Privacy**: no analytics, no crash reporter. The only network use is Groq (HTTPS, always on
  in a build with the key): merchant identification (merchants' names and nothing else) and the
  assistant (the question you type and today's date, nothing else). Nothing about money goes
  in DataStore (it isn't encrypted).
- **The assistant** (Assistant tab, Assistant board) is "tool calling" without the round trip:
  `AssistantProtocol` asks Groq to read your question into one `Ask` (a tool from `AskTool`:
  spending, income, bills, owed, afford, balance, or unsupported; your words for the topic,
  the AI's business type for it, the days, an amount), under a strict schema. The phone runs it
  (`AssistantDomain`, `core/domain/Answers`: your topic matches your categories, then merchants,
  then merchants of that business type) and words the answer; no figure, category or name of
  yours goes back to the AI. The conversation lives in memory only.
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

**Phase 2 (classification and learning)** is built: categories and expense types (seeded;
`Category`, `ExpenseType`), rules (`Rule`, `core/domain/Rules`, `ClassificationRepository`), the
history of changes with undo (`AuditBatch`, `AuditChange`), the review reminder
(`core/reminders`), and merchant identification (business types, the bundled list, Groq in
`core/ai`). Screens: **Review** (Review board: one card per merchant, biggest first, with what it
was identified as, the chosen category to confirm for the ones sure enough, the All / Suggested /
Needs you filter, and the last answer's undo; reached from Home's review pill), **Rules** (Rules
board; from Settings and from a transaction's "Filed automatically" card), category and type on
Transaction detail, and, with no board, built from the system's components:
**Categories** (add; tap to rename, change the type and the business types it takes, or
delete), **Rule** (the form: merchant is, description contains, account, amount range →
category and type), **Recent changes** (each with Undo), the reminder sheet in Settings, and
**Merchants** (from Settings: every merchant, busiest first, with search) and **Merchant** (from
Transaction detail's Merchant row, a Review card for many, or the list: rename, what was spent,
what it is (tap to say), how the bank writes it with how each spelling joined, "Not this one",
"Same as another merchant", its transactions). Still to come in Phase 2: web search for
cryptic names (Tavily), the Review board's swiping and its loan/split marks (with Phase 3),
the usage cap in Settings, and merchant logos and locations.

**Phase 3 (people and recurring)** has begun: people (`Person`, `PersonAlias`, `PeopleRepository`,
found by `applyRules`) and loans (`Loan`, `LoanEvent`, `LoansRepository`, `core/domain/Loans`).
Screens: **People** (People board: owed to you and you owe, then Loans, open and settled, or All
transfers, everyone the latest first with what came back less what went; reached from Wealth),
**Person** (Person board: each open loan with what is still owed, its caption and progress,
"Send reminder" (the share sheet, so WhatsApp or SMS, with a polite message) and "Record
repayment" (choose their transfer, or forgive what is left), "This loan" with its due date and
events; settled loans; then all transfers with them, how the bank writes their name, "Same as
another person" and their transfers; reached from People and from Transaction detail's Person
row), and Transaction detail's **Loan** card (no board: built from the system's card and
buttons), **Subscriptions and bills** (Recurring board: a month and a year, alert cards for a
price rise, a missed charge and what was found, Next 30 days and Later, each with its badges;
reached from the Plan tab's card and Home's Coming up), **Subscription or bill** (no board: the
form, from the list or its + Add), Home's **People owe you** and **Coming up** cards (Home
board), and the Plan tab's Subscriptions and bills card (Plan board; the rest of Plan comes with
Phase 4), and Transaction detail's **Split** card and sheet (no board). Still to come in
Phase 3: the Review board's one-tap loan/split/subscription marks, linking people to IBANs and
contacts.

**Phase 4 (planning)** is built: pay cycles, budgets, savings goals, the forecast, anomaly
alerts and digests. Screens: the **Plan** tab (Plan board:
the cycle chip, Budgets this cycle, savings goals, Subscriptions and bills, Forecast; calculators come
with Phase 5), Home's **balance card** (Home board and its warn/over states, with the forecast's
"End ≈"), **Forecast** (Forecast board: the end figure and band, the balance chart drawn on a
Canvas, left over each month with the dip's biggest payments, and "Can I afford it?"), **Budgets** and **Budget**
(no board: the Plan board's budget rows full size, and the form), the Plan board's **goal
cards** and **Savings goal** (no board: the form), **Alerts** (no board: a card per alert with
Open, Normal for it and Dismiss), **Digest** (Digest board, minus net worth) and **Digests**
(the archive, from the Assistant tab and Settings' Digests sheet).

**Phase 5 (wealth)** is built: the **Wealth** tab is the Net worth board (total, this month and
year, the timeline over 3M/1Y/All, the breakdown, then Accounts, Assets, People and Zakat),
**Assets** and **Asset** (no board: the list and the form), and **Zakat** (Zakat board; reached
from Wealth and the Plan board's Zakat card), **Retirement** (Retirement board, with
Scenarios), **Compound interest** (no board) and **Savings** (Savings board: terms attached to a
savings account, `SavingsTerms` and `core/domain/Savings`; Awaeed terms run from a start for a
tenor and roll over when they renew, expected profit is simple on the balance; Hasad pays next
month on this month's lowest balance, nothing under 5,000; a term maturing within a month shows
on Wealth and is reminded three days before; the terms form has no board). Not built: fetching
fund and gold prices (an owner's decision, see Assets).

**Phase 6 (delight)** has begun: **Money flow** (Money flow board, Activity's second segment:
for a month and an account, salary or what came in, a Sankey (`Sankey` component,
`core/domain/MoneyFlow`) of moves to each of your accounts, what was spent from it and what
stayed; a leg the bank called a move with no other side is "no match": "It went to someone"
makes it a plain transfer, "Pick the account" records the other leg there and pairs them) and
the **Assistant** (Assistant board, with the Digests link; see the product rule). The board's
"See 52 transactions" link waits for a filtered feed.
