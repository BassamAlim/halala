package bassamalim.halala.core.export

import bassamalim.halala.core.data.dataSources.room.AppDatabase
import bassamalim.halala.core.data.dataSources.room.Seed
import bassamalim.halala.core.data.dataSources.room.entities.Account
import bassamalim.halala.core.data.dataSources.room.entities.AccountRef
import bassamalim.halala.core.data.dataSources.room.entities.BalanceCheckpoint
import bassamalim.halala.core.data.dataSources.room.entities.Category
import bassamalim.halala.core.data.dataSources.room.entities.Institution
import bassamalim.halala.core.data.dataSources.room.entities.InternalTransfer
import bassamalim.halala.core.data.dataSources.room.entities.Merchant
import bassamalim.halala.core.data.dataSources.room.entities.MerchantAlias
import bassamalim.halala.core.data.dataSources.room.entities.RawMessage
import bassamalim.halala.core.data.dataSources.room.entities.Rule
import bassamalim.halala.core.data.dataSources.room.entities.RuleActions
import bassamalim.halala.core.data.dataSources.room.entities.RuleConditions
import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.InstitutionsRepository
import bassamalim.halala.core.data.repositories.RestoreRepository
import bassamalim.halala.core.data.repositories.SmsRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.data.TEST_CLOCK
import bassamalim.halala.core.data.testDatabase
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.AliasMatch
import bassamalim.halala.core.enums.BusinessType
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.ExpenseType
import bassamalim.halala.core.enums.IdentifiedBy
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
            Transaction(33, "tx-in", 21, Direction.CREDIT, 50_000, "SAR", at, TransactionKind.ATM_WITHDRAWAL, "", "", TransactionSource.MANUAL, at)
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
            Merchant(84, "mer-jahez", "Jahez", BusinessType.FOOD_DELIVERY, IdentifiedBy.AI, 93, namedByYou = true, autoRuled = true)
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
        ClassificationRepository(db.classificationDao(), db.merchantsDao(), db.transactionsDao(), TEST_CLOCK),
        SmsRepository(db.smsDao()),
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
