package bassamalim.halala.core.domain

import androidx.test.core.app.ApplicationProvider
import bassamalim.halala.core.data.TEST_CLOCK
import bassamalim.halala.core.data.dataSources.definitions.DefinitionsFile
import bassamalim.halala.core.data.dataSources.room.AppDatabase
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.LedgerQueryRepository
import bassamalim.halala.core.data.repositories.LoansRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.data.testDatabase
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.models.QueryResult
import bassamalim.halala.core.models.TransactionDraft
import bassamalim.halala.features.assistant.AssistantViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AskingTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = testDatabase()
    }

    @After
    fun tearDown() = db.close()

    private fun run(sql: String): QueryResult =
        db.query(Asking.wrap(sql)!!, null).use { LedgerQueryRepository.read(it, Asking.MAX_ROWS) }

    @Test
    fun `tx reads the ledger as the app's totals do`() = runTest {
        val transactions = TransactionsRepository(db.transactionsDao(), db.accountsDao(), TEST_CLOCK)
        val classification = ClassificationRepository(db.classificationDao(), db.merchantsDao(), db.transactionsDao(), db.peopleDao(), DefinitionsFile(ApplicationProvider.getApplicationContext()), TEST_CLOCK)
        val loans = LoansRepository(db.loansDao(), db.transactionsDao(), TEST_CLOCK)
        val cash = db.accountsDao().getCashWallet()!!.id
        suspend fun add(direction: Direction, minor: Long, kind: TransactionKind, title: String) =
            transactions.add(TransactionDraft(cash, direction, minor, TEST_CLOCK.instant(), kind, title))

        val dinner = add(Direction.DEBIT, 30_000, TransactionKind.PURCHASE, "PANDA 1042 RIYADH")
        add(Direction.DEBIT, 5_000, TransactionKind.PURCHASE, "PANDA 77 JEDDAH")
        add(Direction.CREDIT, 900_000, TransactionKind.SALARY, "Salary")
        val lent = add(Direction.DEBIT, 150_000, TransactionKind.TRANSFER_OUT, "KHALID ALI")
        classification.applyRules()
        loans.open(lent, null)
        val khalid = db.peopleDao().getPeople().single().id
        assertTrue(loans.split(dinner, mapOf(khalid to 10_000)))

        val totals = inOut(transactions.observeAll().first(), "SAR")
        val byFlow = run("SELECT flow, SUM(amount_minor) AS total_minor, COUNT(*) AS n FROM tx WHERE currency = 'SAR' GROUP BY flow")
            .rows.associate { it[0] to it[1] }
        assertEquals(totals.outMinor, byFlow["spent"])
        assertEquals(25_000L, byFlow["spent"])
        assertEquals(totals.inMinor, byFlow["income"])
        assertEquals(150_000L, byFlow["moved"])

        // One merchant however the bank spells it; a transfer names a person.
        assertEquals(listOf(listOf<Any?>("Panda", 2L)), run("SELECT merchant, COUNT(*) FROM tx WHERE merchant IS NOT NULL GROUP BY merchant").rows)
        assertEquals("Khalid Ali", run("SELECT person FROM tx WHERE person IS NOT NULL").rows.single().single())
        assertNotNull(run("SELECT day, month, weekday, hour, tags, bank, account, category FROM tx LIMIT 1").rows.single()[0])

        val limited = db.query(Asking.wrap("SELECT id FROM tx")!!, null).use { LedgerQueryRepository.read(it, 3) }
        assertEquals(3, limited.rows.size)
        assertTrue(limited.more)
    }

    @Test
    fun `only one SELECT is a query`() {
        assertNull(Asking.wrap("DELETE FROM transactions"))
        assertNull(Asking.wrap("SELECT 1; DROP TABLE transactions"))
        assertNull(Asking.wrap("PRAGMA key = 'x'"))
        assertEquals(1L, run(" select 1 ; ").rows.single().single())
        assertEquals(2L, run("WITH big AS (SELECT 2 AS n) SELECT n FROM big").rows.single().single())
    }

    @Test
    fun `money columns are formatted as money, and every figure hides with amounts`() {
        assertEquals("Average", Asking.heading("average_minor"))
        assertEquals("Business type", Asking.heading("business_type"))

        val result = QueryResult(listOf("merchant", "purchases", "total_minor", "average_minor"), listOf(listOf("Panda", 1200L, 123_456L, 2_500.5), listOf(null, 0L, 0L, null)), more = false)
        val table = AssistantViewModel.table("sql", result)
        assertEquals(listOf("Merchant", "Purchases", "Total", "Average"), table.headings)
        assertEquals(listOf("Panda", "1,200", "1,234.56", "25.01"), table.rows[0].map { it.text })
        assertEquals("–", table.rows[1][0].text)

        Money.masked = true
        try {
            assertEquals(listOf("Panda", Money.MASK, Money.MASK, Money.MASK), AssistantViewModel.table("sql", result).rows[0].map { it.text })
        } finally {
            Money.masked = false
        }
    }
}
