# Halala · هللة — Product & Technical Spec

Sep 30, 2026 · @Bassam

## Overview

A single-user Android app that reads bank SMS, turns each one into a structured transaction, and uses AI plus learned rules so the owner's job is reviewing suggestions, not typing. Name: **Halala · هللة**, tagline "كل هللة في الحسبة" (every halala counted).

**Goals**

- Near-zero manual entry: every SMS becomes a transaction automatically; only low-confidence items need a tap.
- Never teach it twice: every correction becomes a rule that applies forward *and* backward over history.
- One place for cash flow, loans, subscriptions, bills, savings, investments, gold and net worth.
- Private by default: all data on-device; for AI, only the names of shops, as your bank writes them, ever leave the phone.

**Design principles**

- Deterministic first, AI second: regex parsers and rules handle known patterns; the LLM handles only what they can't.
- Every AI decision carries a confidence score and a reason, and is reversible.
- Raw SMS is kept forever, so any parse or classification can be re-run when the logic improves.
- Money is stored as integer halalas/cents, never floating point.

**Decisions already made**

| Topic | Decision |
| --- | --- |
| Platform | Android only, Kotlin + Jetpack Compose |
| Distribution | Sideloaded APK, built by GitHub Actions, published to GitHub Releases |
| Storage | Room (SQLite), encrypted, with backup and export |
| AI budget | Free tier preferred, hard cap US$5/month |
| Banks (v1) | Al Rajhi, D360, SNB, STC Bank, Barq; Al Rajhi Capital (investments); Al Rajhi Awaeed and Hasad (savings) |
| Currencies | SAR primary, USD secondary; any ISO currency supported |
| Budget cycle | Payday to payday, detected from salary SMS |
| Location | Captured once per transaction, never tracked in the background |
| Login | Biometric on every open |
| Build process | Spec written for AI-assisted implementation (Claude Code) with owner review |

**Out of scope for v1:** receipt OCR, email parsing, bank statement import, multi-user or shared budgets, credit cards, Arabic UI, iOS.

## Architecture & tech stack

A single-module-per-layer Kotlin app: clean architecture (data / domain / UI), everything offline-first, with a pluggable AI provider behind one interface so the model can change without touching features.

| Layer | Choice | Notes |
| --- | --- | --- |
| Language / UI | Kotlin 2.x, Jetpack Compose, Material 3 | English UI; all strings in resources and layouts RTL-safe. Arabic SMS are still parsed. |
| Min SDK | Android 10 (API 29), target latest | Covers any recent phone; needed for modern biometrics and scoped storage |
| DI | Hilt |  |
| Database | Room over SQLCipher | Whole DB encrypted; key held in Android Keystore, unlocked by biometric |
| Background work | WorkManager | Price refresh, digests, reminders, retro-classification batches, backups |
| SMS intake | `BroadcastReceiver` on `SMS_RECEIVED` + `ContentResolver` query of the SMS inbox for back-import | Needs `RECEIVE_SMS` and `READ_SMS`; fine for a sideloaded APK |
| Location | `FusedLocationProvider.getCurrentLocation()` once, when a transaction SMS arrives | No background tracking; coarse accuracy is enough; negligible battery cost |
| Networking | Ktor client + kotlinx.serialization | AI calls, price feeds |
| Charts | Vico (Compose-native) | Net worth, cash flow, budgets |
| Maps (heatmap) | osmdroid (OpenStreetMap) or Google Maps Compose with heatmap utils | osmdroid avoids an API key |
| Auth | `BiometricPrompt` (class 3 / strong), device credential fallback | Required on every cold start and after 1 min in background (configurable) |
| Build / release | GitHub Actions: lint, unit tests, signed release APK on tag, attached to a GitHub Release | Signing keystore stored as an encrypted repo secret |
| Updates | In-app check against the GitHub Releases API, one-tap download of the new APK | Avoids needing a store |

**AI provider**

One `LlmProvider` interface with implementations for Groq and Gemini, selectable in settings, with automatic fallback when one is rate-limited. All calls use JSON-schema structured output.

| Provider / model | Free-tier limits | Privacy | Role in the app |
| --- | --- | --- | --- |
| Groq `qwen/qwen3.8-27b` | 30 requests/min, 1,000/day, 8K tokens/min, 200K tokens/day ([Groq docs](https://console.groq.com/docs/rate-limits)) | Settled: only merchant names are sent, and Groq doesn't train on API inputs | **Primary**: classification, merchant normalisation, assistant |
| Gemini Flash-Lite (3.1 / 3.5) | Free tier available; free-tier content **is** used to improve Google's products ([Gemini pricing](https://ai.google.dev/gemini-api/docs/pricing)) | Free tier: shared with Google; paid tier: not shared | **Fallback** when Groq is rate-limited or down; optional merchant lookup with Google Search/Maps grounding |

**Web search**: Tavily (free plan, 1,000 credits a month) for identifying unknown merchants, with its own key and monthly credit counter in settings.

- Everyday volume is tiny: roughly 5–20 SMS a day, most resolved by rules without any AI call.
- The one-time back-import is the only heavy load; it is batched (see *Ingestion*) so it fits under the 200K tokens/day cap over a few days.
- If free tiers disappear, Gemini 3.1 Flash-Lite paid costs US$0.25 per million input tokens and US$1.50 per million output tokens, which keeps this app far below US$5/month.

**What leaves the phone:** only a merchant's name as the bank wrote it ("PANDA 1042 RIYADH"), for purchases, refunds and bills. Its digits are kept: they are part of how the bank names the shop. Never: amounts, dates, accounts, balances, your categories, names of people (a transfer's title), OTPs, whole SMS. As a safeguard against a parser capturing too much, a name holding one of your accounts' last four digits or an IBAN is never sent.

## Data model

Everything hangs off two tables: **RawMessage** (the untouched SMS, kept forever) and **Transaction** (what it means). Money is `amountMinor: Long` plus an ISO currency code; foreign transactions also store the SAR amount the bank charged and the implied FX rate.

| Entity | Key fields | Purpose |
| --- | --- | --- |
| Institution | name, sender IDs (e.g. `AlRajhiBank`), parser version | One per bank or broker |
| Account | institution, user nickname, type (current, savings, card, wallet, investment, cash), last-4 digits, IBAN suffix, currency, opening balance, archived | You name every account; last-4 digits route each SMS to the right one when a bank has several |
| RawMessage | sender, body, received time, hash, parse status, parser version | Source of truth; re-parsable |
| Transaction | account, direction (debit/credit), amount, currency, SAR amount, time, kind, merchant, counterparty, category, expense type, tags, note, location, confidence, review state, raw message link | The core record |
| Transaction kind | purchase, refund, transfer-out, transfer-in, internal-transfer, salary, ATM withdrawal, cash deposit, fee, bill payment, investment buy/sell, savings deposit/withdrawal, loan-given, loan-received, loan-repayment | Drives which screens and totals include it |
| Merchant | canonical name, aliases (raw descriptors), business type (and who identified it: the bundled list, AI or you), logo, website | "ABC TRDG EST 1234" and "ABC TRADING" both map to one merchant |
| Counterparty | display name, account names | People you transfer to or lend to |
| InternalTransfer | out-leg transaction, in-leg transaction, match confidence | Pairs the two sides of a move between your own accounts |
| Category | name, icon, colour, default expense type, business types it takes | One level, e.g. Groceries takes supermarkets |
| ExpenseType | fixed / variable × essential / discretionary | Second axis, independent of category |
| Tag | name, colour, active date range (optional) | e.g. "Trip to Istanbul", "Wedding" |
| Rule | conditions (JSON), actions (JSON), source (manual, learned, AI), hit count, created from transaction, enabled | The learning memory; visible and editable |
| Suggestion | transaction, field, value, confidence, source, reasoning | Pending AI or rule suggestions awaiting review |
| Loan | counterparty, direction (I lent / I borrowed), principal, currency, start transaction, due date, status | Linked to one or more transactions |
| LoanEvent | loan, transaction (optional), amount, type (disbursement, repayment, forgiveness, adjustment) | Partial repayments and write-offs |
| Split | transaction, counterparty, share | Splitting one bill across people; each share can become a Loan |
| RecurringSeries | kind (subscription, bill), merchant/payee, amount (fixed or range), cadence, next due, auto-renew, reminder lead time, end date, linked transactions | Subscriptions and bills in one model |
| Budget | scope (category, tag, merchant, or expense type), amount, period (pay cycle / month / custom), rollover, alert thresholds | Targets |
| SavingsGoal | name, target amount, target date, linked accounts or envelope | Goals on top of savings accounts |
| Holding | account, instrument (fund code, name), units, cost basis | Fund positions at Al Rajhi Capital |
| PriceQuote | instrument, date, price, source | Daily NAV and gold price history |
| Asset | type (gold, vehicle, property, other), quantity + unit (gold grams + karat), manual value, value history | Non-cash assets |
| NetWorthSnapshot | date, per-account balances, assets, liabilities, total | Daily snapshot powering the net worth timeline |
| Reminder / Schedule | type (review, digest, bill), day(s), time, snooze-until | All user-chosen schedules |
| AuditLog | entity, field, old, new, actor (user, rule, AI), time | Explains every automated change and allows undo |

**Balances** are not computed only from transactions. When an SMS includes the post-transaction balance (most Saudi bank SMS do), it is stored as a *balance checkpoint*; the account's balance is the latest checkpoint plus later transactions. A drift between computed and reported balance flags a missed or duplicate SMS.

## Ingestion pipeline

Every SMS runs through the same six stages whether it arrives live or from the back-import; only stage 6 differs (live items notify, historic items are batched).

1. **Filter.** Keep only messages from known bank sender IDs. Drop OTPs, marketing and login alerts by keyword ("OTP", "رمز التحقق", "كلمة المرور"), but keep declined-transaction alerts as a separate type (useful for anomaly detection).
2. **Parse.** A per-bank parser (regex templates, Arabic and English variants) extracts amount, currency, direction, account last-4, merchant descriptor, counterparty name/IBAN, balance, date and time. Unparseable messages are kept with a parse-failure status and never sent to the LLM (a whole SMS holds amounts and balances); the raw SMS stays, so they parse once a template for the new format is added.
3. **Route.** Last-4 digits or IBAN suffix map to one of your named accounts. Unknown last-4 → prompt once: "Which account is ••1234?"
4. **Deduplicate.** Same account + amount + time within 3 minutes + similar body = one transaction (banks sometimes send two SMS for one card purchase).
5. **Pair internal transfers.** An outgoing transfer on one of your accounts and an incoming credit of the same amount on another of your accounts within 48 hours (tighter when names/IBANs match) are linked as one InternalTransfer. These are excluded from spending and income totals and shown only in the Money Flow view.
6. **Classify.** Run the rule engine, then identify the merchants rules can't settle (the bundled list, then AI) and file them by business type (see *AI & learning*). Assign merchant, category, expense type, tags, and kind (loan, subscription, bill, salary…).

**Parser test suite.** Each bank parser ships with fixture SMS (masked real examples) and expected output, run in CI. This matters more than any other test in the project: banks change SMS formats without warning. A parse-failure rate above 5% for a sender in a week raises an in-app alert.

### Back-import and classifying history

You will have thousands of historic SMS; nobody reviews those one by one. The plan is to make the machine do the bulk and make your few decisions multiply.

1. **Import and parse everything** (stages 1–5). Parsing is deterministic and free, so all historic amounts, accounts and balances are correct immediately.
2. **Cluster by merchant descriptor.** Group transactions by normalised descriptor (lowercase, strip branch numbers, terminal IDs and city suffixes). Thousands of transactions typically collapse into a few hundred merchants, and the top \~50 cover most of the volume.
3. **Identify merchants, not transactions.** Send each *cluster's* name to the LLM once, in batches of \~40 names per request, to get a canonical name, business type and confidence; the app turns the business type into a category and expense type itself. Well-known merchants are identified by the bundled list without a call. That is a few dozen requests in total, well inside the free tier.
4. **Detect patterns in bulk.** Recurring amounts to the same merchant → subscription/bill candidates. Regular same-day credits → salary. Repeated transfers to the same person → counterparty.
5. **Review by impact.** The review inbox shows *clusters*, sorted by total SAR affected: "Panda Retail — 214 transactions, SAR 18,400 → Groceries?" One tap confirms all 214 and creates a rule.
6. **Retroactive propagation.** Any rule you create or edit, at any time in the future, offers: "Apply to 37 past transactions too?" (default yes, with a preview). History keeps getting better as you use the app.
7. **Leave the long tail alone.** Low-value one-off merchants the AI is unsure about stay as "Uncategorised (auto)" and are excluded from nagging; they still count in totals.

The result is that after roughly 15–30 minutes of cluster review you get a fully usable history, and it keeps improving passively.

### Location capture

When a live purchase SMS arrives, request one coarse location fix (with a 10-second timeout) and attach it to the transaction. No background tracking, no location for back-imported history, and an off switch.

### Pipeline diagram

&#91;embedded content: SMS ingestion and learning loop\]

Only low-confidence items reach you; each correction loops back as a rule, so the same question is never asked twice.

## AI & learning

The rule engine is the memory; the LLM is the fallback. Over time the share of transactions needing AI should fall toward zero, which also keeps costs at zero.

The AI only identifies: given a merchant's name, it says what the business is. Which of your categories that is, its expense type, and how sure the app is are decided on the phone, by code. A fact like "Panda is a supermarket" never goes out of date, so each merchant is asked about once, ever.

### Merchant resolution

1. Normalise the descriptor and look it up in local Merchant aliases. Hit → done.
2. Fuzzy match (token similarity ≥ 0.85) against known merchants.
3. A new merchant is looked up in a bundled list of well-known Saudi merchants (Panda, Tamimi, Othaim, Jahez, HungerStation, STC, Aldrees, Nahdi, Jarir…), which gives its name and business type with no call.
4. Otherwise, an LLM call with the merchant's name alone: returns canonical name, business type (one of the app's list, or unknown) and confidence. If the LLM's confidence is below 0.80, it gets a web search tool (Tavily) and looks the name up online before answering (see below).
5. The result is kept on the merchant, so the same merchant never costs another call.

### Business types and categories

- A fixed list of business types (supermarket, restaurant, café, food delivery, fuel station, pharmacy, clinic, telecom, utility, government service, airline, hotel, ride-hailing, online marketplace, electronics, clothing, …, unknown). The LLM must answer from it (a strict JSON schema).
- Each category takes some business types (seeded: supermarket → Groceries, café → Restaurants, food delivery → Delivery, fuel station → Fuel, …). A type belongs to one category at most; you move types between categories on the Categories screen.
- The expense type is the category's own.
- A mixed type (department store, online marketplace) starts in no category, and unknown is in none, so they are asked until you give the type a category.
- You can correct a merchant's business type on its screen; it then files as you said.

### Web search for unknown stores

When a descriptor is cryptic ("ALMTRF TRDG EST 0412"), the AI searches the web to find out what the business actually is.

- **Provider**: Tavily Search API behind a `WebSearchProvider` interface. A basic search costs 1 credit and the free plan includes 1,000 credits a month; pay-as-you-go is US$0.008 per credit ([Tavily docs](https://docs.tavily.com/documentation/api-credits)).
- **How it runs**: the LLM calls `webSearch(query)` as a tool. The app builds queries from the merchant's name plus "Saudi Arabia", in English and in Arabic where the name suggests it (e.g. "مؤسسة المطرف للتجارة الرياض"). The LLM reads the top results and returns canonical name, business type, website and confidence.
- **Evidence shown**: the review card says "Found online: Al-Mutref Trading, a building-materials store in Riyadh" with the source link, so you can judge the match.
- **Search once per merchant, ever**: the result is saved as a Merchant alias; later transactions from that descriptor never trigger another search.
- **Budget**: live use is typically a handful of new merchants a week. For the back-import, clusters are searched in order of total SAR spent, capped at about 800 searches a month, and the rest queue for the next month's credits; small one-offs are never searched.
- **Privacy**: only the merchant's name is sent. No amounts, dates, account numbers or names of people.
- **Fallback**: Gemini with Google Search/Maps grounding can be enabled as a second option (off by default, because free-tier Gemini content is used to improve Google's products).

### Confidence tiers

| Tier | Condition | Behaviour |
| --- | --- | --- |
| Auto | Matched by a rule; or identified by the bundled list, or by AI at ≥ 0.90, as a business type a category takes | Filed by an AI rule ("Merchant is Panda → Groceries"); visible in the feed with a small "auto" badge, and the rule can be turned off |
| Suggest | Identified by AI at 0.60–0.90 as a type a category takes | Added to the review inbox with the category pre-selected and the evidence ("Identified as a supermarket") |
| Ask | Confidence < 0.60, unknown, a mixed type, a type no category takes, or a kind the app cannot infer (loan, split, reimbursable) | Added to the inbox with no default |

Thresholds are adjustable in settings. The app tracks how often you accept each tier's suggestions and nudges thresholds to keep the inbox small without losing accuracy.

### Review inbox

- Card stack with swipe right = accept, swipe left = edit, tap = details.
- Grouping: identical-merchant items appear as one card ("5 × Starbucks · confirm all").
- Each card can be marked as a **loan**, **split**, **subscription** or **bill** in one tap, which opens the relevant mini-form.
- Review reminders: daily or weekly at a chosen day and time; the notification shows the count and offers **Review now**, **In 1 hour**, **Tomorrow**, or a custom time. No notification when the inbox is empty. The app never notifies per transaction; it stays quiet apart from review reminders, digests, bill and loan reminders, and anomaly alerts.

### Learned rules

Every correction proposes a rule, shown in plain language before saving:

> When merchant is **Jahez** and amount < SAR 150 → category **Delivery**, type **Variable · Discretionary**. Applies to 23 past transactions.

- Conditions: merchant, descriptor contains/regex, account, amount range, day/time, counterparty, location radius, SMS sender.
- Actions: set merchant, category, expense type, tags, kind, counterparty, note, exclude from budgets, mark as internal transfer.
- Priority order: manual rules > learned rules > AI; more specific rules beat general ones.
- Rules screen lists every rule with hit count, last hit and source; each can be edited, disabled, deleted or re-run over history with a preview of changes.
- Every automated change is in the AuditLog with one-tap undo.

### AI tag suggestions

Tags are suggested from context: date clustering (many transactions in a new city → "Trip to \<city>?"), foreign currency bursts, or a merchant type (hotel, airline). An active tag can be applied automatically to everything in its date range until you end it.

### Assistant (natural-language questions)

A chat screen that answers questions like "How much did I spend on coffee since June?" or "Who owes me money?".

- **Tool-calling, not data dumping.** The LLM gets a small set of functions (`sumTransactions(filter)`, `listTransactions(filter, limit)`, `getBalances()`, `getLoans()`, `getBudgets()`, `forecast(horizon)`), and the app runs them locally on the database. Only aggregated results go back to the model, never the full ledger.
- Answers show the filter used as editable chips, so you can check how the number was produced and tap through to the transactions.
- Can also take actions with confirmation: "Mark my last transfer to Khalid as a loan".

## Features

### Accounts and money flow

- You name every account ("Rajhi – Salary", "Rajhi – Awaeed", "STC Bank – Daily"). Several accounts at one bank are told apart by last-4 digits or IBAN suffix from the SMS.
- **Internal transfers** are paired automatically and hidden from spending and income. A **Money Flow** screen shows them as a Sankey diagram per period: salary lands in Rajhi, moves to Awaeed and D360, and so on. Unpaired transfer legs show as "one side missing" for you to resolve.
- **Cash wallet**: ATM withdrawals move money into a Cash account. Cash spending is logged by a quick-add (amount + category, two taps) or by a periodic "reconcile cash" prompt: enter what is in your wallet and the gap becomes "cash spending, uncategorised". Balance is always manually editable.

### Transfers to and from people

- Every transfer names a counterparty from the SMS (name or IBAN).
- Per-person ledger: total sent, total received, net, and a timeline, with filters for loans only or all transfers.

### Loans (owed to me / I owe)

- Mark any transaction as a loan from the inbox or its detail screen; choose person, direction and optional due date.
- Repayments: when a transfer arrives from someone with an open loan, the app suggests "Repayment of loan from 12 Aug?" Partial repayments are supported; the loan tracks the remaining balance.
- **Splits**: split a bill across people (equal, by share or by amount); each person's share becomes a loan owed to you.
- Reminders on due dates, and a one-tap "send reminder" that opens WhatsApp or SMS with a polite, pre-filled message.
- Summary card on the home screen: "People owe you SAR X · You owe SAR Y".

### Subscriptions and bills

Both use one RecurringSeries model.

- **Detection**: the same merchant with a similar amount at a regular interval (weekly, monthly, yearly) three times or more is proposed as a subscription. Bills (rent, car insurance, Istimara renewal, utilities) can also be added manually. Family support is a planned recurring expense: repeated transfers to the same family member are detected, confirmed once, then counted under the Family support category and budgeted and forecast like a bill, not treated as loans or plain transfers.
- Fields: amount (fixed or estimated range), cadence (any N days/weeks/months/years), next date, auto-renew yes/no, end date or duration (e.g. a 12-month rent contract), reminder lead time (e.g. 3 days before, or 30 days before for yearly insurance).
- Future occurrences feed the forecast.
- Alerts: price increase versus the last charge, a charge that did not arrive when expected, a free trial about to convert, and a yearly renewal coming up.
- Subscriptions screen: monthly-equivalent total, annual total, and a list sorted by yearly cost so the biggest ones to question come first.

### Categories, expense types and tags

- Categories (one level) with a sensible Saudi-context default set (Groceries, Restaurants, Delivery, Fuel, Transport, Utilities, Telecom, Rent, Housing, Health, Education, Family support, Charity, Travel, Entertainment, Shopping, Government fees, Fees & charges, Other).
- Expense type as a second, independent axis: fixed or variable, and essential or discretionary.
- Tags cut across both and support AI suggestions (see *AI & learning*).

### Budgets

- Budget any scope: a category, a group of categories, a tag, a merchant, or an expense type (e.g. "Discretionary ≤ SAR 3,000 per cycle").
- Period defaults to the **pay cycle** (salary credit to the next salary credit). Salary is detected as a large credit from your employer within about ±5 days of the usual date, since it can arrive 2–3 days early or late; the expected amount is learned and updates automatically after a raise. Calendar-month and custom periods are deferred.
- Options: rollover of unspent amount, alerts at 50/80/100% and a "pace" warning when you are ahead of the expected spend for this point in the cycle.
- Savings goals: target amount and date, linked to one or more savings accounts; shows the monthly amount needed to stay on track.

### Savings, investments and gold

- **Awaeed (term savings)**: each Awaeed is its own deposit with principal, start date, tenor (Al Rajhi offers 1 to 36 months), profit rate, profit payout (at maturity, monthly or in advance) and a maturity choice: pay out principal plus profit, renew the principal only, or renew principal plus profit ([Al Rajhi Awaeed](https://www.alrajhibank.com.sa/en/Personal/Accounts/Awaeed-Account)). The app shows a maturity countdown and expected profit, reminds you a few days before maturity to confirm the choice, schedules the payout in the forecast, and on renewal opens the next term automatically. Locked money counts in net worth but is marked "available on \<date>".
- **Hasad (monthly-profit savings)**: profit is paid monthly on the month's minimum balance, and SAR 5,000 is the minimum balance to earn profit ([Al Rajhi Hasad](https://www.alrajhibank.com.sa/en/Personal/Accounts/Hassad-Account)). The app tracks this month's lowest balance, estimates the coming profit, parses the monthly profit credit, and warns before a withdrawal would lower the profit-bearing balance ("Moving SAR 3,000 out drops this month's profit base to SAR 7,000").
- Profit rates for both are user-editable, not hard-coded, because the bank changes them.
- **Funds** at Al Rajhi Capital: holdings of units per fund, with cost basis from buy/sell SMS. Unit prices (NAV per unit) are published per fund on the [Saudi Exchange mutual fund pages](https://www.saudiexchange.sa/wps/portal/saudiexchange/hidden/company-profile-mutual-fund/!ut/p/z1/04_Sj9CPykssy0xPLMnMz0vMAfIjo8ziTR3NDIw8LAz83Y0DDAwC3QL8PM0DzYwNAo30I4EKzBEKDMKcTQzMDPxN3H19LAwNPEz1w8syU8v1wwkpK8hOMgUAof6qaw!!/?selectedFund=012056); they are delayed and some funds value only on certain days (e.g. twice a week), so the app fetches daily and stores the last known price with its date. There is no official public API, so the fetcher is a small scraper with a manual-entry fallback if it breaks.
- **Gold**: stored as grams and karat (24/22/21/18). Value = grams × karat purity × spot price per gram in SAR, minus a configurable dealer spread to show a realistic resale value. Spot price from a free gold API that supports SAR and karat grades, such as [GoldAPI.io](https://www.goldapi.io/), fetched once a day. You can also record purchases with the price paid, to show gain or loss.
- **Other assets** (car, property, other): manual value with a value history, and an optional yearly depreciation rate for vehicles.

### Net worth timeline

Net worth = everything you own (account balances, cash, savings, fund value, gold, other assets, money owed to you) minus everything you owe (card balances, money you owe people). The timeline is a daily line chart of that total, with a breakdown by asset class, so you can see whether you are getting richer month to month and what drives it. Snapshots are taken daily in the background; history before install is reconstructed from SMS balance checkpoints.

### Forecasting

- **End-of-cycle balance**: current balance + expected salary + scheduled recurring items − projected variable spend (median of the last 3 cycles per category, pro-rated for days left). Shown with a low–high band.
- **Cash flow, 3–12 months**: month-by-month projection of income, fixed costs, variable costs and savings.
- **Net worth projection**: current trend extended, with fund and gold growth at user-set rates.
- **"Can I afford X?"**: enter an amount and date (one-off or monthly); the app replays the forecast and reports the lowest balance reached and which budgets or goals it breaks. Also available through the assistant.

### Retirement planning and compound interest

- **Compound interest calculator**: principal, monthly contribution, annual return, compounding frequency, years; shows the growth curve and the contributions-versus-returns split.
- **Retirement planner**: current age, retirement age, current invested assets (pre-filled from funds, savings and gold), monthly contribution (pre-filled from your average savings rate), expected return and inflation, and desired monthly income in today's riyals. Output: projected pot, the monthly income it supports, the gap, and the extra monthly saving needed to close it. Scenarios can be saved and compared.

### Zakat calculator

- Pre-fills zakatable assets: cash and account balances, savings (including Awaeed and Hasad), all gold by weight (held as an investment asset, so always zakatable), fund holdings, and money owed to you that you expect to be repaid; subtracts debts due now.
- Nisab threshold based on 85 g of gold at today's price (silver basis optional), rate 2.5%, on a Hijri (lunar) year: you set your zakat date in the Hijri calendar, the app tracks the hawl (one full Hijri year, about 354 days) and converts it to the Gregorian date each year using the Umm al-Qura calendar (Android's built-in IslamicCalendar, umalqura variant) and a reminder before it.
- Each asset class has a toggle and a short note, because scholarly opinions differ (for example on personal-use jewellery and on how to value investment funds). The calculator shows the method used; it does not issue rulings.

### Anomaly alerts

- Possible duplicate charge (same merchant and amount within minutes or a day).
- Unusually large transaction for that category or merchant (well above its usual range).
- Subscription price increase, or a new recurring charge you have not confirmed.
- Spending pace far above normal for this point in the cycle.
- Card used in a new country or a foreign-currency charge you may not expect.
- Declined-transaction SMS, and balance mismatches that suggest a missed SMS.
- Each alert can be dismissed or turned into a rule ("this is normal for this merchant").

### Digests

- Weekly, monthly and yearly digests, each on or off with its own day and time.
- Contents: spending versus last period and budget, top categories and merchants, subscriptions renewing soon, loans outstanding, net worth change, one or two AI-written observations ("Delivery spending is up 40% versus your 3-cycle average").
- Delivered as a notification that opens a full digest screen; past digests are kept.

### Spending map

A heatmap of where you spend, built from per-transaction locations, with filters by category and period, plus a list of top places.

## Screens & navigation

Five bottom tabs plus a global quick-add button. The review inbox badge sits on the Home tab so pending items are never more than one tap away.

| Tab | Screens | Main content |
| --- | --- | --- |
| Home | Dashboard, Review inbox, Alerts | Pay-cycle spend vs budget, forecast end balance, net worth, people-owe-you card, upcoming bills, inbox count |
| Activity | Transactions feed, Transaction detail, Money Flow, Search | Filterable feed (account, category, tag, kind, person); detail shows raw SMS, location, audit trail, rule that filed it |
| Plan | Budgets, Savings goals, Subscriptions & bills, Forecast, Calculators (compound interest, retirement, zakat) | Everything forward-looking |
| Wealth | Net worth timeline, Accounts, Funds, Gold & assets, People (loans + transfers) | Balances and holdings |
| Assistant | Chat, Digests archive | Natural-language questions and past digests |

**Settings**: accounts and banks, categories, rules, AI provider and keys, review and digest schedules, confidence thresholds, location toggle, notifications, security, backup and export, parser health.

**Onboarding**: grant SMS permissions → detect banks from sender IDs → name each detected account (by last-4) → back-import → cluster review → set salary account and pay day confirmation → optional budgets.

**Widgets**: home-screen widget with cycle spend vs budget, inbox count and a quick-add button for cash.

## Design

The look is decided: **Ink Block Jade**, a dark-only UI with a near-black ground, one jade accent (`#7DD4A0`), Instrument Sans for words and IBM Plex Mono for every number. The build follows two sources, and neither is re-invented in code.

| Source | Holds | Use it for |
| --- | --- | --- |
| [Halala Design System](https://claude.ai/artifact/S6fcpdnuBUEso5u7ojecaj) | Tokens (colour, budget states, type, spacing, radius, sizes), 10 components with usage rules, voice, Compose mapping notes | The Compose theme (`HalalaColors`, `Typography`, `HalalaNumbers`, dimension constants) and every shared component |
| [Halala design canvas](https://claude.ai/artifact/7G8KxznMhsrFhsC6HGC6A2) | 22 phone screens across Home, Activity, Plan, Wealth, Assistant, Settings and onboarding | Layout and content of each screen |

Rules that affect code:

- Spending amounts are never coloured; income uses the accent; coral is only for over-budget and anomalies.
- The Home balance card changes fill with the budget state: jade under 80%, amber 80–100%, coral over 100%.
- Every automated decision shows its reason (rule, web result or confidence) and a way to change it.
- Minimum touch target 44 dp; all numbers use tabular figures.
- Screens in the spec not yet drawn (spending map, compound-interest calculator, categories editor, backup flow) follow the same components.

## Security, privacy, backup & export

The phone is the only place the full ledger lives; backups and exports are encrypted with a passphrase only you know.

**Security**

- Biometric prompt (strong / class 3) on every launch and after a configurable background timeout (default 1 minute); device PIN as fallback.
- Room database encrypted with SQLCipher; the key is wrapped by an Android Keystore key that requires user authentication.
- `FLAG_SECURE` on all screens (no screenshots, hidden in the recent-apps view), toggleable.
- API keys for Groq and Gemini stored in EncryptedSharedPreferences, never in the APK or the repo.
- No analytics, no crash reporter that sends data off-device (crash logs stay local and can be exported manually).

**Backup**

- Automatic encrypted backup on a schedule (daily or weekly) to a folder you choose via the Storage Access Framework: local storage, Google Drive, or any cloud provider app that exposes a folder.
- Keeps the last N backups (default 10). Restore from settings or at onboarding.

**Export and re-import**

| Format | Contents | Use |
| --- | --- | --- |
| `.halala` (zip of JSON + raw SMS, AES-256-GCM, passphrase via Argon2id) | Everything: accounts, transactions, raw SMS, rules, loans, budgets, assets, settings | Full backup and lossless re-import, including to a new phone |
| CSV (one file per entity, in a zip) | Transactions, accounts, loans, budgets | Opening in Excel or Google Sheets |
| JSON (unencrypted, schema-versioned) | Same as the backup, readable | Your own scripts or migration to another tool |

Every export includes a `schemaVersion`; importers migrate older versions forward. Re-import merges by stable IDs, so importing the same file twice never duplicates data.

## Roadmap, open questions & risks

Build the ledger core first and make it trustworthy; every later feature reads from it.

1. **Phase 0 — Foundations.** Repo, GitHub Actions (build, test, signed release), Room + SQLCipher, biometric lock, accounts screen, manual transactions, cash wallet, CSV/JSON export.
2. **Phase 1 — SMS core.** Live SMS receiver, parsers and fixture tests for all five banks plus Al Rajhi Capital, account routing by last-4, dedupe, internal transfer pairing, balance checkpoints, transactions feed, back-import.
3. **Phase 2 — Classification & learning.** Categories and expense types, rule engine, business types, Groq merchant identification (names only), merchant clustering and cluster review, review inbox with confidence tiers, review reminders with snooze, rules screen, retroactive rule application, audit log.
4. **Phase 3 — People & recurring.** Counterparties, transfers ledger, loans and repayments, splits, subscription detection, bills with custom durations and reminders.
5. **Phase 4 — Planning.** Pay-cycle detection, budgets and savings goals, forecasting and "can I afford", anomaly alerts, weekly/monthly/yearly digests.
6. **Phase 5 — Wealth.** Fund holdings and NAV fetcher, gold with daily price, other assets, net worth snapshots and timeline, zakat calculator, compound interest and retirement planner.
7. **Phase 6 — Delight.** Assistant with tool calling, AI tag suggestions, spending heatmap, Money Flow Sankey, home-screen widget, encrypted scheduled backups and full re-import.

Later ideas: email receipts, warranty vault, shared household mode.

**Open questions**

- [ ] Groq data-retention policy: decided, acceptable. Only merchant names are sent, so retention doesn't matter, and Groq doesn't train on API inputs ([Your data in GroqCloud](https://console.groq.com/docs/your-data)).
- [ ] Zakat year: decided, Hijri.
- [ ] App name: decided, Halala · هللة. Icon: a single coin.
- [ ] Exact SMS formats per bank: left to the development agent, collected from the phone's inbox during Phase 1.

**Risks**

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Banks change SMS formats | Missed or misread transactions | Fixture tests in CI, parse-failure alerts, raw SMS kept for re-parsing once a template is added, balance-mismatch detection |
| Free AI tiers shrink or vanish | Classification stops | Provider interface with fallback, rules handle most traffic, paid Flash-Lite stays well under US$5/month |
| Fund price scraping breaks | Stale portfolio value | Show price date, alert when stale over 7 days, manual entry fallback |
| Android restricts SMS access further | Core intake breaks | Sideloading avoids Play policy |
| Losing the phone or the passphrase | Data loss | Scheduled encrypted backups off-device; passphrase hint and a printed recovery key at setup |
| Back-import overwhelms the free tier | Slow first run | Cluster-first classification, batching, spread over several days with progress shown |

**Sources**

- [Groq rate limits](https://console.groq.com/docs/rate-limits)
- [Gemini Developer API pricing](https://ai.google.dev/gemini-api/docs/pricing)
- [Saudi Exchange mutual fund profile (Al Rajhi Inclusion Fund)](https://www.saudiexchange.sa/wps/portal/saudiexchange/hidden/company-profile-mutual-fund/!ut/p/z1/04_Sj9CPykssy0xPLMnMz0vMAfIjo8ziTR3NDIw8LAz83Y0DDAwC3QL8PM0DzYwNAo30I4EKzBEKDMKcTQzMDPxN3H19LAwNPEz1w8syU8v1wwkpK8hOMgUAof6qaw!!/?selectedFund=012056)
- [Argaam: Al Rajhi Capital funds](https://www.argaam.com/en/tadawul/tasi/al-rajhi-capital/broker-funds)
- [GoldAPI.io](https://www.goldapi.io/)

* [Tavily credits & pricing](https://docs.tavily.com/documentation/api-credits)
