# Halala: product rules and current state

What Halala does and how far it is built, for whoever changes it. The rules are decided (mostly
by the spec); a change of behaviour updates its rule here in the same change. Stack,
architecture and the design system are in `CLAUDE.md`.

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
- **Foreign charges** are in the account's currency too, with what the merchant asked for kept
  beside it (`originalAmountMinor`, `originalCurrency`; Transaction detail's "How it got here"
  gives it and the rate). When the bank's SMS gives only the foreign amount, the charge is
  **estimated** (`Transaction.estimated`, shown with ≈): at the rate of the charge nearest in
  time whose SMS gave both amounts in that currency (the bank's own rate, its fee included),
  else, for a currency pegged to the dollar (USD, AED, QAR, BHD, OMR, JOD), at the peg plus a
  2.5% card fee (`ForeignRates`). Editing its amount says what the bank charged and ends the
  estimate. One that can't be estimated (a floating currency never quoted) waits as `FOREIGN`
  and is tried again on every SMS run; so is a foreign-only move between your accounts, which is
  still refused. A fee quoted in the foreign currency isn't recorded.
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
  filtered to one account. A move across currencies is refused (by hand and from SMS alike).
- **Totals** (Activity's In/Out) are per currency, in SAR for now, this calendar month (pay
  cycles arrive with budgets).
- **Categories and rules**: only spending has a category (money out that counts in totals;
  `Rules.canCategorise`). A transaction with a category and no `ruleId` was filed by you, and no
  rule ever changes it; one with a `ruleId` was filed by that rule and shows the Auto badge.
  A bank's fee (`FEE`, often untitled, split off a transfer) names nothing a rule can match, so
  `applyRules` files any unfiled one under Fees & charges (found by its seeded name, `Seed.FEES`),
  with no `ruleId`, so it stays wherever you move it.
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
  0.85, five letters or more) joins that merchant; so does one the bank cut short
  (`Merchants.cutShortOf`: eight letters or more, a prefix of a known alias or it of the key,
  cut inside a word, so "JARIR BOOK" joins "JARIR BOOKSTORE" but "AL RAJHI TAKAFUL" never joins
  "AL RAJHI", and only when it fits one merchant); else it starts one named after it. Feeds
  show the merchant's name; the title keeps the bank's words, and Transaction detail says
  both. You rename, merge ("Same as another merchant": the merged-into merchant's learned
  answer stands) and split ("Not this one": the spelling becomes its own merchant and what
  rules filed under it goes back to review) on the Merchant screen. The app runs `applyRules`
  on opening, which fills merchants in after the upgrade. Merchants one of whose spellings
  is the other's cut short (`Merchants.cutShortPairs`, the same test, for those apart from
  before it or that came first) and merchants the AI gave the same website (for their logos)
  are offered as "Same merchant?" cards on Merchants (no board), cut-short ones first,
  counted in the Inbox: Merge keeps the one you named, else the busier; "Not the same" is
  remembered in DataStore by `People.pairKey` of their uids. Never merged on its own: a
  marketplace and its subscription share a site.
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
  leaves the phone** for identification (the assistant sends your question, and people's names go to find one person under two names, see below and People), and never one holding your accounts' or cards' last four digits, ten or
  more digits, or an IBAN (`Identification.sendable`; those are `WITHHELD` for you). The AI is
  Groq (`qwen/qwen3.8-27b`, strict JSON schema, reasoning off), always on, with no setting. Its key is built in, not typed: `BuildConfig.GROQ_API_KEY`, from `GROQ_API_KEY` in
  `.env` on the machine that builds it. **CI and the releases are built with no keys** (an APK
  anyone can download must not carry one), so they identify from the bundled list only, as any
  build without it does. `IdentifyWorker` (online only, one at a time)
  runs after every SMS run and as the app opens, in batches of 20 names (Groq's free tier refuses a request for over 1,000 output tokens a minute, `GroqProtocol.MAX_TOKENS`), the busiest first;
  what it says is recorded without a batch (the rule names why), and each merchant is asked once.
  **Web search** (`core/ai/WebSearch.kt`, `WebLookup`, the spec's Tavily step): after
  identifying, a merchant the AI said at under 80 with 100 or more of spending (small one-offs
  never) is looked up once, the most money first: its name and "Saudi Arabia" (in Arabic for an
  Arabic name) go to Tavily (basic search, five results), then the name and those public results
  go to Groq, which answers again and names the result it rests on. A surer answer replaces the
  first, keeping the page (`Merchant.webUrl`, `webTitle`), shown as "Found online: <title>" (it
  opens the page) on Review and the Merchant screen; either way `searchedOnline` stops it being
  searched again. At most 800 searches a month (a count in DataStore). The Merchant screen's
  "Look it up" (with the Review board's jade "Found online" globe, `ic_globe`) (for one nothing surer than the AI has identified) does the same at once
  (`WebLookup.lookUpNow`; the AI from the name alone when no search can be had) but changes
  nothing: what it found (name, business, how sure, the page) is offered in an "Is it this?"
  sheet, sure or not. "Use this" takes it as yours (`ClassificationRepository.acceptLookup`: the name
  unless you named it, the business as you said it, so it files however sure the AI was, and
  the page kept to show; one change you can undo; picking a type yourself drops the page); "Not this" leaves
  it as it was. A search spent this way still stops it being searched again on its own. The key is
  `BuildConfig.TAVILY_API_KEY`, as Groq's (`TAVILY_API_KEY`); a build without it never searches.
  **Logos** (`core/logos/LogoLookup`, run in `IdentifyWorker` after web search): **the owner
  chose to have them fetched online.** Each identified merchant (list, you or AI; not withheld
  or unknown) is asked its own website once, in batches of 40 names as the bank wrote them
  (`GroqProtocol.websitesRequest`, `Identification.sendable` as for identifying), kept as a bare
  domain (`Merchant.website`). Each website's icon is then fetched once from Google's icon service
  (`google.com/s2/favicons`, 128px), which so learns the domains of the places you shop; one that
  is missing or under 48px is kept as none. Logos live in the encrypted ledger
  (`merchant_logos`), never exported (a restore fetches them again); `MerchantLogos` decodes them
  once and `LocalMerchantLogos` hands them to `Avatar` (pass `merchantId`), which falls back to
  the initial. **A wrong logo is corrected on Merchant** (the Logo row under What it is; no
  board): you type its website (any form `GroqProtocol.domainOf` reads) and its icon is fetched
  then (`LogoLookup.correct`; offline, the next run fetches it), or "No logo" keeps the initial
  and never fetches one. Either way the AI isn't asked its website again.
- **The owner's definitions** (no board, no screen): Halala is the owner's first. Places anyone
  pays at go in the bundled `KnownMerchants` list (a new build), backed by the chains of
  OpenStreetMap's name-suggestion-index in Saudi Arabia and worldwide
  (`app/src/main/resources/known_merchants_nsi.json`, written by `scripts/nsi_merchants.py`; run it again to
  refresh; brands named a single common English word are left out, and the hand-kept list beats
  it); the owner's own merchants and
  categories go in `definitions.json` at the repository root, pushed to the phone with no
  reinstall: `adb push definitions.json /sdcard/Android/data/bassamalim.halala/files/` (read by
  `DefinitionsFile` each time `applyRules` runs, so on next opening). `categories` (name,
  optional `expenseType`) are made when missing; each of `merchants` has a `name`, an optional
  `type` (a `BusinessType`), `spellings`, and an optional `category` it files under whatever its
  type (`KnownMerchants.parse`). The file beats the bundled list, the list beats the AI, and
  neither beats what you said on the Merchant screen. A file that can't be read is ignored whole
  (logcat tag `Halala`). A merchant the list or you identified raises no foreign-currency alert.
- **People** (the spec's counterparties): a transfer's title (`People.KINDS`: transfers and the
  loan kinds, never a move between your own accounts) names a `Person`, found as merchants are,
  through a `PersonAlias` keyed by the transaction's `merchantKey`, so merging or splitting moves
  aliases, never transactions. `applyRules` gives each name not seen before a new person
  (`People.nameOf`: digits dropped, capitals put in title case); unlike merchants, a name never
  joins a look-alike ("Ahmed Ali" and "Ahmed Saleh" are two people). Feeds show the person's
  name. You rename, merge ("Same as another person") and split ("Not this one") on the Person
  screen; these aren't audited (nothing is filed by them, and each can be taken back by hand).
  **Halala suggests merges and never makes one** (a merge moves loans): People shows "Same
  person?" cards (no board) for pairs `People.suggest` finds, the surest reason first: spelled
  the same (`People.canonical`: spaces and the ways an Arabic letter is written dropped, never
  a fuzzy score, since siblings share two names of three), the same last four digits quoted for
  their account (banks quote no more than four, so no IBAN; read again from the transfers' SMS,
  `PeopleRepository.observeAccountRefs`), or the AI read the names as one (another language, an
  initial, a name cut short). `PeopleMatching` runs after merchant identification in
  `IdentifyWorker`, only when someone new has appeared, and sends every person's names as banks
  wrote them (`Identification.sendable` ones, at most 300 people) and nothing else. Merging keeps
  the one you named, else the one with more transfers; "Not the same" is remembered. What the AI
  said and what you dismissed are in DataStore by `People.pairKey` (uids, never a name). **Salary by transfer**: an incoming plain transfer from someone
  can be marked "It's my salary" (Transaction detail, confirmed): that person's `salarySince`
  becomes its time, and it and their transfers in after it become `SALARY` (`PeopleDao.markSalaries`,
  run by `applyRules` and when it is set), so pay cycles and the forecast see them. Salary names
  no one, so those leave the person's transfers. Their page shows "Pays your salary" and
  "Not my salary anymore", which stops it for what comes next (what was salary stays; Edit
  changes one back). Merging keeps the earlier date. Exported with the person.
- **Loans** are only ever made by your say. Marking a plain transfer to or from someone
  (`Loans.MARKABLE`, never a paired move) as lent or borrowed opens a `Loan` with that person,
  or with anyone else you choose (someone asked you to pay a third person for them; a transfer
  naming nobody can be marked this way too) (optional due date); marking a transfer back as repaying it pays it down. A **refund** is marked the same way (`Loans.MARKABLE`): one that is someone else's money to give back is borrowed from whoever you choose (it names no one), and money lent can come back as a refund, so a loan's "Record a repayment" offers any refund too; taking a refund out of a loan leaves it a plain transfer in, its kind set back on its form; forgiving lets go of
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
  transfer names can be added by name to split with. "Paid for someone else" (spending that
  isn't a transfer) is a split whose one share is the whole, with an optional due date. Undoing a split drops its loans and frees
  their repayments. Transaction detail asks whether a transfer repays one of the person's open loans
  (`Loans.repaidBy`; with several, you choose which, oldest first, or more than one: the transfer
  is spread over them, oldest paid off first and the rest to the newest (`Loans.allocate`), each share
  editable and together exactly the transfer (`Loans.validateShares`); each loan's event carries its
  share, `LoansRepository.repayMany`). "Not part of a loan" takes back every share; a loan going frees
  a transfer only when it repays no other and offers marking it as a loan. Merging people moves their loans.
- **Subscriptions, bills and planned payments** are one `RecurringSeries` (the spec's
  RecurringSeries): kind (`SUBSCRIPTION`, `BILL`, `PLANNED` for family support and the like),
  amount every N days/weeks/months/years from an `anchor` (occurrences count from the anchor, so
  the 31st never drifts), auto-renew, an optional end date (a contract), an optional reminder lead
  time. Charges aren't stored: they are its merchant's spending (or, for `PLANNED`, transfers to
  its person), read from the ledger (`Charges`); each one from the anchor on (up to half a cadence
  early) moves the next due date on, and a series with neither merchant nor person is taken as
  paid when its day passes. A linked one whose charge is more than `Recurring.GRACE_DAYS` late is
  "missed"; a last charge above the known price is a price rise ("Keep it" takes the new price,
  "Remind me to cancel" also asks for a reminder before it renews). Heads-ups
  (`Recurring.headsUp`): a yearly (or 12-month) one renewing within 30 days, and a subscription
  not charged yet whose first day (its anchor: a free trial's end) is within a week; each is a card
  with "Keep it" (remembered in `dismissed_alerts` by series and day) and "Remind me to cancel",
  and counts in the Inbox. Nothing once a cancel reminder is set. `Recurring.detect` proposes
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
  an expense type, a merchant or a tag (what carries it, `Tags.byTransaction`), optionally
  rolling over what was left last cycle. Spending is
  money out that counts in totals, your share of it; never stored, read from the ledger.
  `BudgetState` colours it, and spending further through the budget than through the cycle by
  10% or more reads as "spending fast" (the warn look) even under 80%. The Everything budget
  drives Home's balance card. Every budget notifies once a cycle at 50, 80 and 100%
  (`Budgets.alerts`, `core/reminders/BudgetAlerts`; the highest reached only, its name and the
  percent, never an amount), looked at after each SMS run and by the daily reminder work; what
  was told is in DataStore by `Budgets.toldKey` (budget id, cycle start, percent).
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
  saved in (none is fine: a goal can be held in deposits alone); what is saved is their balances
  plus the running term deposits filed under it, plus what you marked toward it on Transaction
  detail (`GoalContribution`: a transfer, move or investment, "Put in" or "Took out", for money
  kept where Halala has no account, like Al Rajhi Capital; a plain one becomes `SAVINGS_DEPOSIT`
  or `SAVINGS_WITHDRAWAL`, so neither spending nor income, and goes back to its `kindBefore`
  when unmarked; a move is marked by its sending leg, and none is counted that an account the
  goal holds already counts). A month's saving needed = what is left over the
  months to the target month (rounded up); "you averaged" = the net flow into those accounts
  over the last three months, a third of it.
- **Term deposits** (`Deposit`, no board): an Awaeed isn't an account of yours. The bank opens
  a numberless one per deposit, so each creation SMS is one `Deposit`, linked to the leg that
  arrived (its amount and start are that transaction's, as a loan event's are). The ledger
  still needs somewhere for the money: one holding account a bank (`AccountType.DEPOSIT`, found
  by the `product:` ref), counted as savings in net worth and **never listed, chosen or edited**
  (`AccountType.listed`; filter any new account list by it). The SMS gives no terms: rate,
  tenor and what happens at maturity are yours to add on the Deposit form, with its purpose,
  which is a savings goal (`goalId`). Al Rajhi's closing SMS ("اقفال حساب عوائد", one amount,
  naming no deposit) ends the running deposit it fits (`SavingsRepository.closePaid`: the
  largest that went in before, at 80% or more of what came back): what went in moves back, and
  the rest is profit (an `OTHER` credit, so income). "It was paid out" does the same by hand,
  without profit, for a bank that sends none. Messages an older parser couldn't read are tried
  again once after `BankFormats.PARSER_VERSION` is bumped. Savings accounts you make yourself
  keep `SavingsTerms`.
- **Anomaly alerts** (`Anomalies`, last 30 days, found in the ledger, never stored): the same
  merchant, amount and account twice within a day; a charge over three times the merchant's
  median (four or more before it) and at least 100 above it; a foreign-currency charge; a
  declined card (a `DECLINED` message); a bank balance that isn't the one before plus what was
  recorded between (a missed or doubled SMS; off by no more than 5% of the estimated charges
  between is an estimate, not a mismatch); parser health: a sender more than 5% of whose
  last week of messages (notices and OTPs aside) are `UNRECOGNISED`, keyed by the newest
  failure so another brings it back. Only dismissals are stored (`dismissed_alerts`, by
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
  are fetched for linked assets** (`core/prices`, `Asset.priceSource`): gold from
  `api.gold-api.com` (USD a troy ounce, turned into SAR a gram at the 3.75 peg, exactly), funds
  from Mubasher's list of every Saudi fund (one request for the whole list, so nothing says which
  you hold); linked on the Asset form ("Price from the market" for a fund, "Today's market price"
  for gold). `PriceWorker` runs daily, online only, and right after you link one; it updates the
  price and its day and takes a net worth snapshot. Anything not linked is priced as you type it.
- **Net worth** (`NetWorth`): accounts by class (current/card/wallet/cash, savings, investment),
  assets, owed to you, less what you owe people. Savings also hold what you marked toward a goal that
  left your accounts (`NetWorth.heldElsewhere`: put in less taken out, never below none; a move
  is already in balances). The timeline is read back from the ledger (each
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
- **Tags** cut across categories: a `Tag` has a name and optional days; with `auto` set it takes
  every transaction in its days (to today while it has no end) as they arrive
  (`TagsRepository.applyActive`, run as the app opens and after each SMS run). `TransactionTag`
  rows carry them; taking a tag off a transaction its days cover marks the row `removed`, so it
  stays off. Suggestions are worked out on the phone (`core/domain/Tags.suggest`, no AI call):
  three or more purchases in another currency, no more than a week apart, in the last four
  months, are "Trip to <the country of that currency>?" ("Tag the trip" makes an automatic tag
  over those days; "Not a trip" is remembered in DataStore by its key). A budget can be on a
  tag; a tag's own screen is its feed (everything carrying it, and what was spent).
- **Where you spend** (the spec's heatmap; no board). There is no setting: whenever location is
  allowed all the time (it must be, since SMS arrive while the app is closed), `SmsWorker` asks
  Android's own location (`PlaceCapture`, no Play services) for the purchases its run recorded
  that happened in the last 30 minutes, and keeps it (`TransactionPlace`, degrees × 10⁷,
  accuracy; nothing worse than 500 m) in the encrypted ledger. History from before has no
  places. Location is asked for once, the first time in after onboarding (a sheet on the main
  shell; "Not now" is remembered, `locationAsked`), and again from the map: without permission,
  with it only while in use, or with location off, the map is `MapPlaceholder` (a drawn street
  grid) saying why, with the button that fixes it (`rememberLocationRequest`, which opens
  Halala's settings when Android won't ask again, or the location switch); it looks again on
  resume. The map (`HeatMap`, osmdroid, tiles inverted for the dark theme) shows a heat of
  your spending by period and category, and the top places (purchases within about 200 m,
  named by their usual merchant; `core/domain/Places`). It lives on Activity's **Insights**:
  a "Where you spend" card for the month picked on the bars (a small map the page scrolls over,
  how many purchases were placed and what they came to, the three places most went, or the
  street-grid placeholder with its fix); the card opens the whole map, where the period and
  category are chosen. Transaction detail's **Where** card (for spending) shows a purchase's place on a
  small map the page scrolls over, with its accuracy, or the street-grid placeholder saying why
  there is none (location not allowed all the time, off, or nothing kept, as for history and
  what you add by hand), with the button that fixes it when one can.
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
  `PreferencesRepository.lockTimeoutSeconds`, chosen in Settings › Privacy: at once, 1, 5 or 15
  minutes). The lock itself is always on; strong (class 3)
  biometrics with the device credential as fallback (Android 10 can't combine those, so there it
  accepts any biometric plus credential). A phone with no screen lock opens straight through —
  you can never lock yourself out. `FLAG_SECURE` is always set (no screenshots, blank in
  recents); the spec's toggle for it comes with the security settings.
- **Hide amounts** (Settings › Privacy, no board): `Money.masked` makes `Money.format` return
  `••••` everywhere (screens, the widget, the assistant's answers), so the app can be shown to
  someone. Turning it on is one tap; turning it off asks for the fingerprint (the lock's prompt).
  It is a preference, so it survives a restart. Either way the app starts over from Home, since
  screens hold amounts already formatted. Forms prefill amounts through `Money.input` (empty while
  hidden); files (`Money.plain`: exports, backups) stay exact. An amount shown any other way than
  `Money.format` isn't hidden: don't add one.
- **Encryption at rest**: the whole Room database is SQLCipher. Its 32-byte random passphrase is
  stored only wrapped by an AES-256-GCM key in Android Keystore (`DatabaseKey`), in
  `noBackupFilesDir`. That Keystore key is **not** bound to user authentication, on purpose: the
  SMS receiver and workers must write while the phone is locked, and an auth-bound key dies when
  a fingerprint is enrolled, which would lose the ledger. The lock guards the UI; the key guards
  the file. Binding it later only re-wraps the same passphrase. A database whose key is missing
  is refused loudly, never replaced with an empty one.
- **No Android backup**: `allowBackup="false"` and every domain excluded from cloud backup and
  device transfer — a copy could never be decrypted elsewhere. Data moves by Halala's own export
  and the encrypted `.halala` backup.
- **Exports** (Settings › Backup and export) are written to a file you pick (SAF). CSV: a zip of
  `accounts.csv` and `transactions.csv` (UTF-8 with BOM, CRLF, signed decimal amounts plus exact
  `amount_minor`, local times, text cells defused against spreadsheet formula injection). JSON:
  `ExportFile`, schema-versioned (`schemaVersion`, bump on any shape change; 2 added categories
  and rules, 3 merchants with their aliases, 4 business types on merchants and categories, 5 all
  a restore needs: raw bank messages, account refs, balance checkpoints, full rule conditions,
  6 people with their aliases, 7 loans with their events, 8 subscriptions and bills, 9 a loan's
  split purchase, 10 budgets, 11 savings goals, 12 assets and their snapshots, 13 the zakat
  method, 14 retirement scenarios, 15 savings terms, 16 tags and the transactions carrying them,
  17 an asset's price source, 18 the places of purchases, 19 term deposits, 20 a budget's tag,
  21 merchants looked up online, 22 goal contributions, 23 merchants' websites, 24 estimated
  foreign charges),
  keyed by `uid`s, amounts in minor units. The screen says plainly that exports aren't encrypted.
- **Encrypted backups** (Backup and export › Encrypted backups, no board): a `.halala` file
  (`core/backup/BackupFile`) is the JSON export zipped and sealed with AES-256-GCM under a key
  stretched from your passphrase by Argon2id (Bouncy Castle; the stretch and salt are in the
  file's header, which is authenticated too). The passphrase is never stored: the key it makes is
  kept wrapped by Keystore (`BackupKeyStore`, `noBackupFilesDir`) so scheduled backups run
  unattended. `Backups` writes to a folder you grant (SAF tree, persisted), daily or weekly as
  WorkManager work, keeping the last 5, 10 (default) or 20. Restoring a `.halala` asks for the
  passphrase, then is the same full replace as JSON. Setting a passphrase also makes a
  **recovery key** (25 Crockford base32 characters, five groups; shown once, never stored),
  and takes an optional **hint**. A file with a recovery key is version 2: the passphrase's key
  is also sealed under one stretched from the recovery key (`RecoverySlot`), and the hint
  (at most 50 characters, never containing the passphrase) is in the header, readable without
  either so a new phone can show it, and authenticated. Version 1 files still open. "Recovery
  key" on the backup screen makes one for a passphrase set before them (from the kept key, no
  retyping); a new one opens backups from then on. Restore offers "Use the recovery key
  instead". Onboarding's first step offers **Restore from a backup** (`Screen.Export(fromOnboarding
  = true)`: restore only; SMS access is asked first so new messages still arrive, and a restore
  finishes onboarding). Not yet: printing the recovery key.
- **Restore** (same screen, "Restore", a backup or a JSON export): `Importer.read` turns a schema-5 or later export into
  rows numbered afresh (pure; refuses older or newer schemas and dangling uids), you confirm,
  and `RestoreDao.replaceAll` replaces the whole ledger in one transaction. It is a full
  replace, not a merge. The history of changes (undo) and DataStore settings aren't carried.
  A new table or column that matters must be added to `ExportFile`, `Exporter` and `Importer`
  together; `ImporterTest` checks that a restored export exports again as the same file.
- **Privacy**: no analytics, no crash reporter. Network use: Groq (HTTPS, always on in a build
  with the key) for merchant identification (merchants' names and nothing else, with public web
  results for one it was unsure of), Tavily for those web searches (a merchant's name and the
  country, nothing else), Groq for merchants' websites (their names as for identifying) and
  Google's icon service for their logos (each website's domain), for finding
  one person under two names (the names banks wrote for people you transfer with, nothing else) and the
  assistant (the question you type and today's date, nothing else); and market prices (public
  gold and fund prices, fetched with nothing of yours, only once you link an asset); and the
  spending map's OpenStreetMap tiles (the area you look at, never your purchases). Nothing about money goes
  in DataStore (it isn't encrypted).
- **The assistant** ("Ask", from the icon beside Home's gear; the Assistant board without the
  conversation, and no tab) is text-to-SQL: `AssistantProtocol` gives Groq your question, today's
  date and the columns of `tx`, and gets one SELECT back under a strict schema. `tx`
  (`core/domain/Asking.VIEW`, prepended to every query) is the ledger read flat with its rules
  applied: `flow` (spent / income / moved, as `countsInTotals` and paired moves say),
  `amount_minor` (your share of a split), merchant and person by alias, local day, month,
  weekday and hour, tags. The query runs on the phone on its own **read-only** SQLCipher
  connection (`LedgerQueryRepository`), at most 50 rows; one SQLite refuses goes back once with
  SQLite's reason (the query's own words). No row, figure, category or name of yours goes to
  the AI. Columns named `…_minor` are money and go through `Money.format`; while amounts are
  hidden every figure is. Headings are the query's own column names (the one place words on
  screen aren't string resources), and "How this was worked out" shows the query. Only the
  latest answer is kept, in memory. A new ledger rule that changes what counts must be put in
  `Asking.VIEW` too; `AskingTest` checks it against `inOut`.
- The spec's global quick-add is a flat jade `QuickAddButton` on Home and Activity (the boards
  don't draw one). It opens the transaction form on the cash wallet: Out / In / Move, amount,
  account, kind, where or who, when, note.

## Current state

**Phase 0 (Foundations)** is built: project setup, Room over SQLCipher with the Keystore-wrapped
key, the theme and shared components, the biometric lock, the five-tab shell, accounts (add, name,
archive, several per bank by last four), manual transactions and moves, the cash wallet with its
count, CSV/JSON export, and the CI and release workflows.

Screens and where they come from: **Home** (Home board: mark and wordmark, wallet and banks in the
summary-card grid, Recent; the balance card waits for budgets; the board's review pill is gone, replaced by the Inbox tab),
**Activity** (Activity board: search, a row of plain chips to browse by (Merchants, People, Tags, Digests; no board), an Uncategorised chip (spending with no category yet, as the inbox counts it; on top of the account) and account filter chips, month In/Out, rows by day; Money flow
waits for Phase 6), **Transaction** (Transaction detail board, minus category, tags, location
and SMS), **Settings** (Settings board, only the rows that are true today; reached from a gear
on Home, since the boards don't show where Settings lives), and **Plan**, **Wealth**,
**Assistant** as placeholder tabs (Wealth already links to Accounts). No board draws
**Accounts**, **Account** (add/edit), **New transaction**, **Count your wallet**, **Backup and
export** or **Locked**: they are built from the system's components (Settings' list card, the
onboarding board's bank/••digits/name rows, the segmented control).

**Phase 1 (SMS core)** is built in `core/sms`: the receiver and worker (`SmsReceiver`,
`SmsWorker`), per-bank parsers (`BankFormats`, `SmsParser`) with fixture tests, the ingest
pipeline (`SmsIngest`: routing by last four, dedupe, pairing internal transfers, balance
checkpoints) and back-import (`SmsImport`: the whole inbox at onboarding; on every opening
while READ_SMS is allowed, from a month before the newest kept message, so SMS missed without
the permission come in; kept ones are skipped by hash), plus **Onboarding** (the onboarding board).
Amounts show the riyal sign for SAR (`Currency.kt`), the ISO code otherwise.

**Phase 2 (classification and learning)** is built: categories and expense types (seeded;
`Category`, `ExpenseType`), rules (`Rule`, `core/domain/Rules`, `ClassificationRepository`), the
history of changes with undo (`AuditBatch`, `AuditChange`), the review reminder
(`core/reminders`), and merchant identification (business types, the bundled list, Groq in
`core/ai`). Screens: **Review** (Review board: one card per merchant (never a person: transfers are filed on their own detail), biggest first, with what it
was identified as, the chosen category to confirm for the ones sure enough, the All / Suggested /
Needs you filter, and the last answer's undo; reached from the Inbox tab), **Rules** (Rules
board; from Settings and from a transaction's "Filed automatically" card), category and type on
Transaction detail, and, with no board, built from the system's components:
**Categories** (add; tap to rename, change the type and the business types it takes, or
delete), **Rule** (the form: merchant is, description contains, account, amount range →
category and type), **Recent changes** (each with Undo), the reminder sheet in Settings, and
**Merchants** (from Activity: every merchant, busiest first, with search) and **Merchant** (from
Transaction detail's Merchant row, a Review card for many, or the list: rename, what was spent,
what it is (tap to say), how the bank writes it with how each spelling joined, "Not this one",
"Same as another merchant", its transactions). Review also swipes (toward the end accepts: the
suggestion, or the category sheet when there is none; toward the start changes) and marks a
card as a split (one purchase: Transaction detail opens on its split sheet), a subscription or a
bill (the series form, started from the newest charge: monthly, next due on or after today,
linked to the merchant); loans are marked on transfers, which never reach Review. Web search
for cryptic names is built (see Identifying merchants).

**Phase 3 (people and recurring)** has begun: people (`Person`, `PersonAlias`, `PeopleRepository`,
found by `applyRules`) and loans (`Loan`, `LoanEvent`, `LoansRepository`, `core/domain/Loans`).
Screens: **People** (People board: owed to you and you owe, then Loans, open and settled, or All
transfers, everyone, the most transfers first, with what came back less what went; reached from Activity, Home and the loan lines of Wealth's breakdown),
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
board; always shown, so they are a way in even when empty), and the Plan tab's Subscriptions and bills card (Plan board; the rest of Plan comes with
Phase 4), and Transaction detail's **Split** card and sheet (no board).

**Phase 4 (planning)** is built: pay cycles, budgets, savings goals, the forecast, anomaly
alerts and digests. Screens: the **Plan** tab (Plan board:
the cycle chip, Subscriptions and bills and Forecast first, Budgets this cycle, savings goals, Zakat, then Retirement and Compound interest as list rows; calculators come
with Phase 5), Home's **balance card** (Home board and its warn/over states, with the forecast's
"End ≈"), **Forecast** (Forecast board: the end figure and band, the balance chart drawn on a
Canvas, left over each month with the dip's biggest payments, and "Can I afford it?"), **Budgets** and **Budget**
(no board: the Plan board's budget rows full size, and the form), the Plan board's **goal
cards** and **Savings goal** (no board: the form), **Alerts** (no board: a card per alert with
Open, Normal for it and Dismiss), **Digest** (Digest board, minus net worth) and **Digests**
(the archive, from Activity and Settings' Digests sheet).

**Phase 5 (wealth)** is built: the **Wealth** tab is the Net worth board (total, this month and
year, the timeline over 3M/1Y/All, the breakdown, then Savings, Accounts and Assets),
**Assets** and **Asset** (no board: the list and the form), and **Zakat** (Zakat board; reached
from the Plan board's Zakat card), **Retirement** (Retirement board, with
Scenarios), **Compound interest** (no board) and **Savings** (Savings board: a card per Awaeed deposit (see Term deposits; its
form, **Deposit**, has no board), and terms attached to a
savings account, `SavingsTerms` and `core/domain/Savings`; Awaeed terms run from a start for a
tenor and roll over when they renew, expected profit is simple on the balance; Hasad pays next
month on this month's lowest balance, nothing under 5,000; a term maturing within a month shows
on Wealth and is reminded three days before; the terms form has no board), and fetched fund
and gold prices (see Assets).

**Phase 6 (delight)** is built: **Insights** (Activity's second segment, beside Transactions; no board. It switches between **Spending** and **Money flow**. Spending: the month's spending against the month before, six months' bars that pick the month (chevrons page six months older, back to the first month anything was spent, or newer, up to this one; paging picks the newest shown), a category ring in one ink stepped (spending is never coloured; the top five and Other), each category tapping through to **Category spending** (`Screen.CategorySpending`, no board: that month's total, by merchant, and its transactions; unfiled too) and Other opening into the categories it gathers, spending through the month against the month before (`LineChart`'s dashed second line), and where the most went; `InsightsDomain`), **Money flow** (Money flow board, Insights' second view,
redrawn top to bottom for a phone: for a month and an account, salary or what came in, the share
spent and kept and spending against the month before, then a vertical Sankey (`Sankey`
component, `core/domain/MoneyFlow`) from where the money came from (salary, your accounts,
people, refunds, what was already there) through the account to where it went (your accounts,
saved, people, the top five categories and the rest, not filed yet, what stayed); tap a part on
the chart or in the "Came in" and "Went out" lists below it to light it up and see its biggest
transactions; a leg the bank called a move with no other side is "no match": "It went to someone"
makes it a plain transfer, "Pick the account" records the other leg there and pairs them) and
the **Assistant** (see the product rule; first built as a fifth tab, now Ask behind Home's icon), the **Inbox** tab in its place (no board: one row for each kind of thing waiting for your say, with its count, opening where it is answered: merchants to file → Review, alerts → Alerts, a subscription found, missed or dearer → Subscriptions and bills, "Same merchant?" → Merchants, "Same person?" → People, trips to tag → Tags; `InboxDomain` counts what those screens would show and stores nothing; no badge on the tab, as the design system says),
**Encrypted backups** (see the product rule), and the **home-screen widget** (`core/widget`,
no board: this cycle's spending against the total budget with its state colour, the Review
count, and "+ Cash", which opens the lock as always and then the form on the wallet
(`QuickAddRequest`); refreshed when the app goes to the background and after each SMS run),
**Tags** (from Activity; a tag's form with what carries it; the Tags row on Transaction
detail; no board draws them), and **Where you spend** (see the product rule).
The widget shows amounts outside the lock: it is there only if you add it.
