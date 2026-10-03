package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.TEST_CLOCK
import bassamalim.halala.core.data.dataSources.room.AppDatabase
import bassamalim.halala.core.data.dataSources.room.entities.SavingsGoal
import bassamalim.halala.core.data.testDatabase
import bassamalim.halala.core.domain.inOut
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.models.TransactionDraft
import bassamalim.halala.core.models.TransferDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class GoalsRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var transactions: TransactionsRepository
    private lateinit var goals: GoalsRepository
    private var cash = 0L
    private var goal = 0L

    @Before
    fun setUp() = runTest {
        db = testDatabase()
        transactions = TransactionsRepository(db.transactionsDao(), db.accountsDao(), TEST_CLOCK)
        goals = GoalsRepository(db.goalsDao(), db.accountsDao(), db.transactionsDao(), db.savingsDao(), db.loansDao(), TEST_CLOCK)
        cash = db.accountsDao().getCashWallet()!!.id
        goal = goals.save(SavingsGoal(uid = "", name = "House", targetMinor = 10_000_000, currency = "SAR", accountIds = emptyList(), createdAt = Instant.EPOCH))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun saved() = goals.observeStates().first().single().savedMinor

    private suspend fun spent() = inOut(transactions.observeAll().first(), "SAR").outMinor

    @Test
    fun `money sent to a broker counts toward the goal, not as spending, until taken back out`() = runTest {
        val sent = transactions.add(TransactionDraft(cash, Direction.DEBIT, 100_000, TEST_CLOCK.instant(), TransactionKind.TRANSFER_OUT, "AL RAJHI CAPITAL"))
        val back = transactions.add(TransactionDraft(cash, Direction.CREDIT, 30_000, TEST_CLOCK.instant(), TransactionKind.TRANSFER_IN, "AL RAJHI CAPITAL"))
        assertEquals(100_000, spent())

        assertTrue(goals.contribute(sent, goal, withdrawn = false))
        assertFalse(goals.contribute(sent, goal, withdrawn = false))
        assertTrue(goals.contribute(back, goal, withdrawn = true))
        assertEquals(70_000, saved())
        assertEquals(0, spent())
        assertEquals(TransactionKind.SAVINGS_DEPOSIT, transactions.get(sent)!!.kind)

        goals.uncontribute(sent)
        assertEquals(TransactionKind.TRANSFER_OUT, transactions.get(sent)!!.kind)
        assertEquals(100_000, spent())
        assertEquals(-30_000, saved())
    }

    @Test
    fun `a move is counted once, whichever leg is marked, and never twice with an account the goal holds`() = runTest {
        val broker = db.accountsDao().insert(db.accountsDao().getCashWallet()!!.copy(id = 0, uid = "b", nickname = "Broker", type = AccountType.INVESTMENT))
        val out = transactions.addTransfer(TransferDraft(cash, broker, 50_000, TEST_CLOCK.instant(), TransactionKind.INTERNAL_TRANSFER, ""))
        val inLeg = transactions.getTransferFor(out)!!.inTransactionId

        assertTrue(goals.contribute(inLeg, goal, withdrawn = false))
        assertFalse(goals.contribute(out, goal, withdrawn = false))
        assertEquals(out, goals.contributionFor(inLeg)!!.transactionId)
        assertEquals(TransactionKind.INTERNAL_TRANSFER, transactions.get(out)!!.kind)
        assertEquals(50_000, saved())

        goals.save(goals.get(goal)!!.copy(accountIds = listOf(broker)))
        assertEquals(50_000, saved())
    }

    @Test
    fun `a purchase is never toward a goal`() = runTest {
        val lunch = transactions.add(TransactionDraft(cash, Direction.DEBIT, 5_000, TEST_CLOCK.instant(), TransactionKind.PURCHASE, "Jahez"))
        assertFalse(goals.contribute(lunch, goal, withdrawn = false))
    }
}
