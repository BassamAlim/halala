package bassamalim.halala.core.export

import bassamalim.halala.core.data.dataSources.definitions.DefinitionsFile
import androidx.test.core.app.ApplicationProvider
import bassamalim.halala.core.data.dataSources.room.AppDatabase
import bassamalim.halala.core.data.dataSources.room.Seed
import bassamalim.halala.core.data.dataSources.room.entities.Account
import bassamalim.halala.core.data.dataSources.room.entities.AccountRef
import bassamalim.halala.core.data.dataSources.room.entities.Asset
import bassamalim.halala.core.data.dataSources.room.entities.NetWorthSnapshot
import bassamalim.halala.core.data.dataSources.room.entities.BalanceCheckpoint
import bassamalim.halala.core.data.dataSources.room.entities.Budget
import bassamalim.halala.core.data.dataSources.room.entities.Category
import bassamalim.halala.core.data.dataSources.room.entities.Institution
import bassamalim.halala.core.data.dataSources.room.entities.InternalTransfer
import bassamalim.halala.core.data.dataSources.room.entities.Loan
import bassamalim.halala.core.data.dataSources.room.entities.LoanEvent
import bassamalim.halala.core.data.dataSources.room.entities.Merchant
import bassamalim.halala.core.data.dataSources.room.entities.MerchantAlias
import bassamalim.halala.core.data.dataSources.room.entities.Person
import bassamalim.halala.core.data.dataSources.room.entities.PersonAlias
import bassamalim.halala.core.data.dataSources.room.entities.RawMessage
import bassamalim.halala.core.data.dataSources.room.entities.RecurringSeries
import bassamalim.halala.core.data.dataSources.room.entities.RetirementScenario
import bassamalim.halala.core.data.dataSources.room.entities.Rule
import bassamalim.halala.core.data.dataSources.room.entities.SavingsGoal
import bassamalim.halala.core.data.dataSources.room.entities.Deposit
import bassamalim.halala.core.data.dataSources.room.entities.SavingsTerms
import bassamalim.halala.core.data.dataSources.room.entities.Tag
import bassamalim.halala.core.data.dataSources.room.entities.TransactionTag
import bassamalim.halala.core.data.dataSources.room.entities.TransactionPlace
import bassamalim.halala.core.data.dataSources.room.entities.RuleActions
import bassamalim.halala.core.data.dataSources.room.entities.RuleConditions
import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.data.dataSources.room.entities.ZakatProfile
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.AssetsRepository
import bassamalim.halala.core.data.repositories.BudgetsRepository
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.GoalsRepository
import bassamalim.halala.core.data.repositories.InstitutionsRepository
import bassamalim.halala.core.data.repositories.LoansRepository
import bassamalim.halala.core.data.repositories.PeopleRepository
import bassamalim.halala.core.data.repositories.PlannerRepository
import bassamalim.halala.core.data.repositories.RecurringRepository
import bassamalim.halala.core.data.repositories.SavingsRepository
import bassamalim.halala.core.data.repositories.TagsRepository
import bassamalim.halala.core.data.repositories.PlacesRepository
import bassamalim.halala.core.data.repositories.RestoreRepository
import bassamalim.halala.core.data.repositories.SmsRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.data.repositories.ZakatRepository
import bassamalim.halala.core.data.TEST_CLOCK
import bassamalim.halala.core.data.testDatabase
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.AliasMatch
import bassamalim.halala.core.enums.AssetType
import bassamalim.halala.core.enums.BudgetScope
import bassamalim.halala.core.enums.CadenceUnit
import bassamalim.halala.core.enums.RecurringKind
import bassamalim.halala.core.enums.SeriesStatus
import bassamalim.halala.core.enums.BusinessType
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.ExpenseType
import bassamalim.halala.core.enums.IdentifiedBy
import bassamalim.halala.core.enums.LoanDirection
import bassamalim.halala.core.enums.LoanEventType
import bassamalim.halala.core.enums.MaturityChoice
import bassamalim.halala.core.enums.SavingsKind
import bassamalim.halala.core.enums.RawStatus
import bassamalim.halala.core.enums.RuleSource
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate

/** An export, restored, must export again as the very same file: nothing lost, nothing made up. */
@RunWith(RobolectricTestRunner::class)
class ImporterTest {

    private val at = Instant.parse("2026-09-29T18:14:00Z")

    // Ids are deliberately not 1, 2, 3: a restore numbers rows afresh and must re-point everything.
    private val snapshot = LedgerSnapshot(
        institutions = Seed.INSTITUTIONS.mapIndexed { index, name -> Institution(id = index + 10L, name = name) },
        accounts = listOf(
            Account(21, "acc-cash", null, "Cash", AccountType.CASH, currency = "SAR", createdAt = at),
            Account(22, "acc-salary", 10, "Salary", AccountType.CURRENT, "4821", currency = "SAR", openingBalanceMinor = 100_000, archived = true, createdAt = at)
        ),
        balances = emptyMap(),
        transactions = listOf(
            Transaction(31, "tx-jahez", 22, Direction.DEBIT, 21_450, "SAR", at, TransactionKind.PURCHASE, "Jahez Olaya", "lunch", TransactionSource.SMS, at, rawMessageId = 61, originalAmountMinor = 5_720, originalCurrency = "USD", categoryId = 47, expenseType = ExpenseType.VARIABLE_DISCRETIONARY, ruleId = 59, merchantKey = "jahez olaya"),
            Transaction(32, "tx-out", 22, Direction.DEBIT, 50_000, "SAR", at, TransactionKind.ATM_WITHDRAWAL, "", "", TransactionSource.MANUAL, at),
            Transaction(33, "tx-in", 21, Direction.CREDIT, 50_000, "SAR", at, TransactionKind.ATM_WITHDRAWAL, "", "", TransactionSource.MANUAL, at),
            Transaction(34, "tx-khalid", 22, Direction.DEBIT, 150_000, "SAR", at, TransactionKind.LOAN_GIVEN, "KHALID ALI", "", TransactionSource.SMS, at, merchantKey = "khalid ali")
        ),
        transfers = listOf(InternalTransfer(71, "pair-1", outTransactionId = 32, inTransactionId = 33, matchConfidence = 0.8)),
        categories = listOf(
            Category(47, "cat-delivery", "Delivery", ExpenseType.VARIABLE_DISCRETIONARY, listOf(BusinessType.FOOD_DELIVERY))
        ),
        rules = listOf(
            Rule(59, "rule-jahez", RuleConditions("Jahez", merchantId = 84), RuleActions(47, ExpenseType.VARIABLE_DISCRETIONARY), RuleSource.LEARNED, createdAt = at),
            Rule(60, "rule-mine", RuleConditions(contains = "olaya", accountId = 22, minMinor = 100, maxMinor = 90_000), RuleActions(47), RuleSource.LEARNED, enabled = false, createdAt = at)
        ),
        merchants = listOf(
            Merchant(84, "mer-jahez", "Jahez", BusinessType.FOOD_DELIVERY, IdentifiedBy.AI, 93, namedByYou = true, autoRuled = true, searchedOnline = true, webUrl = "https://jahez.net", webTitle = "Jahez — food delivery", website = "jahez.net", websiteAsked = true)
        ),
        aliases = listOf(MerchantAlias(95, 84, "jahez olaya", "Jahez Olaya", AliasMatch.FIRST)),
        rawMessages = listOf(
            RawMessage(61, "AlRajhiBank", "Purchase 214.50 SAR at Jahez Olaya", at, "hash-1", RawStatus.entries.last(), parserVersion = 3),
            RawMessage(62, "AlRajhiBank", "Something about ••9999", at, "hash-2", RawStatus.UNROUTED, parserVersion = 3, unroutedRefs = "9999")
        ),
        refs = listOf(AccountRef(101, institutionId = 10, ref = "7777", accountId = 22)),
        checkpoints = listOf(
            BalanceCheckpoint(111, accountId = 22, balanceMinor = 28_550, at = at, rawMessageId = 61),
            BalanceCheckpoint(112, accountId = 22, balanceMinor = -300, at = at, rawMessageId = null)
        ),
        people = listOf(Person(121, "person-khalid", "Khalid A.", namedByYou = true)),
        personAliases = listOf(PersonAlias(131, 121, "khalid ali", "KHALID ALI")),
        loans = listOf(
            Loan(141, "loan-khalid", 121, LoanDirection.LENT, "SAR", LocalDate.parse("2026-10-15"), at),
            Loan(142, "loan-split", 121, LoanDirection.LENT, "SAR", null, at, splitOf = 31)
        ),
        loanEvents = listOf(
            LoanEvent(151, "lent", 141, LoanEventType.DISBURSEMENT, transactionId = 34),
            LoanEvent(152, "forgiven", 141, LoanEventType.FORGIVENESS, amountMinor = 50_000, at = at),
            LoanEvent(153, "share", 142, LoanEventType.DISBURSEMENT, amountMinor = 7_000, at = at)
        ),
        recurring = listOf(
            RecurringSeries(161, "rec-jahez", RecurringKind.SUBSCRIPTION, "Jahez Plus", merchantId = 84, amountMinor = 2_900, currency = "SAR", unit = CadenceUnit.MONTH, anchor = LocalDate.parse("2026-10-03"), autoRenew = true, reminderDays = 3, status = SeriesStatus.ACTIVE, createdAt = at),
            RecurringSeries(162, "rec-rent", RecurringKind.BILL, "Rent", amountMinor = 350_000, currency = "SAR", every = 6, unit = CadenceUnit.MONTH, anchor = LocalDate.parse("2026-11-01"), endsOn = LocalDate.parse("2027-08-31"), status = SeriesStatus.ACTIVE, createdAt = at),
            RecurringSeries(163, "rec-khalid", RecurringKind.PLANNED, "Khalid", personId = 121, amountMinor = 1_000, currency = "SAR", unit = CadenceUnit.WEEK, anchor = LocalDate.parse("2026-10-01"), cancelReminder = true, status = SeriesStatus.DISMISSED, createdAt = at)
        ),
        budgets = listOf(
            Budget(171, "bud-all", BudgetScope.TOTAL, amountMinor = 900_000, currency = "SAR", createdAt = at),
            Budget(172, "bud-delivery", BudgetScope.CATEGORY, categoryId = 47, amountMinor = 60_000, currency = "SAR", rollover = true, createdAt = at),
            Budget(173, "bud-jahez", BudgetScope.MERCHANT, merchantId = 84, amountMinor = 30_000, currency = "SAR", createdAt = at),
            Budget(174, "bud-fun", BudgetScope.EXPENSE_TYPE, expenseType = ExpenseType.VARIABLE_DISCRETIONARY, amountMinor = 350_000, currency = "SAR", createdAt = at),
            Budget(175, "bud-wedding", BudgetScope.TAG, amountMinor = 2_000_000, currency = "SAR", createdAt = at, tagId = 2)
        ),
        goals = listOf(
            SavingsGoal(181, "goal-fund", "Emergency fund", 6_000_000, "SAR", LocalDate.parse("2027-03-31"), listOf(22, 21), at),
            SavingsGoal(182, "goal-car", "Car", 9_000_000, "SAR", null, emptyList(), at)
        ),
        assets = listOf(
            Asset(191, "asset-fund", AssetType.FUND, "Al Rajhi Inclusion", quantity = "1234.5678", unitPrice = "12.3456", priceDate = LocalDate.parse("2026-09-28"), costMinor = 1_400_000, currency = "SAR", createdAt = at, priceSource = "fund:6019"),
            Asset(192, "asset-gold", AssetType.GOLD, "Gold", quantity = "95", karat = 21, unitPrice = "410.5", spreadPercent = "3", currency = "SAR", createdAt = at, priceSource = "gold"),
            Asset(193, "asset-car", AssetType.VEHICLE, "Car", valueMinor = 6_000_000, priceDate = LocalDate.parse("2026-01-01"), depreciationPercent = "15", currency = "SAR", createdAt = at)
        ),
        snapshots = listOf(NetWorthSnapshot(LocalDate.parse("2026-09-28"), 12_345_600, "SAR")),
        zakat = ZakatProfile(hijriMonth = 9, hijriDay = 1, goldPricePerGram = "563.25", includeGold = false, otherDebtsMinor = 5_000, paidHijriYear = 1447, remind = true),
        scenarios = listOf(RetirementScenario(201, "scn-55", "Retire at 55", 32, 55, 26_295_000, 400_000, "6", "2.5", 800_000, "SAR", at)),
        savingsTerms = listOf(SavingsTerms(22, SavingsKind.AWAEED, "4.40", LocalDate.parse("2026-05-14"), 6, MaturityChoice.RENEW_WITH_PROFIT)),
        tags = listOf(
            Tag(1, "tag-trip", "Trip to Türkiye", LocalDate.parse("2026-09-12"), LocalDate.parse("2026-09-19"), auto = true, createdAt = at),
            Tag(2, "tag-wedding", "Wedding", createdAt = at)
        ),
        transactionTags = listOf(TransactionTag(31, 1), TransactionTag(31, 2), TransactionTag(34, 1, removed = true)),
        places = listOf(TransactionPlace(31, 247_136_400, 466_753_100, 25)),
        deposits = listOf(
            Deposit(1, "dep-car", 31, goalId = 182, ratePercent = "4.40", tenorMonths = 6, maturityChoice = MaturityChoice.PAY_OUT),
            Deposit(2, "dep-done", 34, closedOn = LocalDate.parse("2026-08-01"))
        )
    )

    private val json = Exporter.json(snapshot, appVersion = "0.2.0", now = at)

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = testDatabase()
    }

    @After
    fun tearDown() = db.close()

    private fun exporter() = Exporter(
        InstitutionsRepository(db.institutionsDao()),
        AccountsRepository(db.accountsDao(), TEST_CLOCK),
        TransactionsRepository(db.transactionsDao(), db.accountsDao(), TEST_CLOCK),
        ClassificationRepository(db.classificationDao(), db.merchantsDao(), db.transactionsDao(), db.peopleDao(), DefinitionsFile(ApplicationProvider.getApplicationContext()), TEST_CLOCK),
        SmsRepository(db.smsDao()),
        PeopleRepository(db.peopleDao()),
        LoansRepository(db.loansDao(), db.transactionsDao(), TEST_CLOCK),
        RecurringRepository(db.recurringDao(), db.transactionsDao(), TEST_CLOCK),
        BudgetsRepository(db.budgetsDao(), db.transactionsDao(), db.tagsDao(), TEST_CLOCK),
        GoalsRepository(db.goalsDao(), db.accountsDao(), db.transactionsDao(), db.savingsDao(), TEST_CLOCK),
        AssetsRepository(db.assetsDao(), TEST_CLOCK),
        ZakatRepository(db.zakatDao(), db.accountsDao(), db.assetsDao(), LoansRepository(db.loansDao(), db.transactionsDao(), TEST_CLOCK), TEST_CLOCK),
        PlannerRepository(db.scenariosDao(), db.accountsDao(), db.assetsDao(), db.transactionsDao(), LoansRepository(db.loansDao(), db.transactionsDao(), TEST_CLOCK), TEST_CLOCK),
        SavingsRepository(db.savingsDao(), db.accountsDao(), db.transactionsDao(), db.goalsDao(), TransactionsRepository(db.transactionsDao(), db.accountsDao(), TEST_CLOCK), TEST_CLOCK),
        TagsRepository(db.tagsDao(), db.transactionsDao(), TEST_CLOCK),
        PlacesRepository(db.placesDao()),
        TEST_CLOCK
    )

    @Test
    fun `what is read exports again as the same file`() {
        assertEquals(json, Exporter.json(Importer.read(json), appVersion = "0.2.0", now = at))
    }

    @Test
    fun `a restore replaces the seeded ledger, and the phone then exports the same file`() = runTest {
        Importer(RestoreRepository(db.restoreDao())).restore(Importer.read(json))

        assertEquals(json, Exporter.json(exporter().snapshot(), appVersion = "0.2.0", now = at))
        // The seeded wallet and categories are gone, not kept beside the file's.
        assertEquals(listOf("acc-cash", "acc-salary"), db.accountsDao().getAll().map { it.uid })
        assertEquals(listOf("cat-delivery"), db.classificationDao().getCategories().map { it.uid })
    }

    @Test
    fun `restoring twice changes nothing more`() = runTest {
        val importer = Importer(RestoreRepository(db.restoreDao()))
        importer.restore(Importer.read(json))
        importer.restore(Importer.read(json))

        assertEquals(json, Exporter.json(exporter().snapshot(), appVersion = "0.2.0", now = at))
    }

    @Test
    fun `a file that isn't a restorable Halala export is refused`() {
        assertThrows(IllegalArgumentException::class.java) { Importer.read("{}") }
        assertThrows(IllegalArgumentException::class.java) { Importer.read(json.replace("\"Halala\"", "\"Other\"")) }
        // Older exports lack the bank messages; newer ones may hold what this version can't read.
        for (version in listOf(4, ExportFile.SCHEMA_VERSION + 1))
            assertThrows(IllegalArgumentException::class.java) {
                Importer.read(json.replace("\"schemaVersion\": ${ExportFile.SCHEMA_VERSION}", "\"schemaVersion\": $version"))
            }
    }

    @Test
    fun `a row pointing at one the file doesn't hold is refused, and nothing is replaced`() = runTest {
        val broken = json.replace("\"accountUid\": \"acc-cash\"", "\"accountUid\": \"acc-gone\"")

        assertThrows(IllegalArgumentException::class.java) { Importer.read(broken) }
        assertEquals(1, db.accountsDao().getAll().size)
    }
}
