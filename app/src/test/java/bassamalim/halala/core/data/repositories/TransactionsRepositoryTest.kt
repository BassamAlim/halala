package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.TEST_CLOCK
import bassamalim.halala.core.data.dataSources.room.AppDatabase
import bassamalim.halala.core.data.testDatabase
import bassamalim.halala.core.domain.feedOf
import bassamalim.halala.core.domain.inOut
import bassamalim.halala.core.domain.signedAmount
import bassamalim.halala.core.domain.titleOf
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import bassamalim.halala.core.models.AccountDraft
import bassamalim.halala.core.models.TransactionDraft
import bassamalim.halala.core.models.TransferDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class TransactionsRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var accounts: AccountsRepository
    private lateinit var transactions: TransactionsRepository

    private var cash = 0L
    private var salary = 0L
    private var dollars = 0L
    private val now: Instant = TEST_CLOCK.instant()

    @Before
    fun setUp() = runTest {
        db = testDatabase()
        accounts = AccountsRepository(db.accountsDao(), TEST_CLOCK)
        transactions = TransactionsRepository(db.transactionsDao(), db.accountsDao(), TEST_CLOCK)

        val rajhi = db.institutionsDao().getAll().first { it.name == "Al Rajhi" }.id
        cash = accounts.getCashWallet()!!.id
        salary = accounts.create(AccountDraft(rajhi, "Salary", AccountType.CURRENT, "4821", "SAR", 0))
        dollars = accounts.create(AccountDraft(rajhi, "Dollars", AccountType.CURRENT, "5518", "USD", 0))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun balance(id: Long) = accounts.observe(id).first()!!.balanceMinor

    @Test
    fun `a transaction takes its account's currency and trims what you wrote`() = runTest {
        val id = transactions.add(
            TransactionDraft(dollars, Direction.DEBIT, 1_200, now, TransactionKind.PURCHASE, title = " Amazon ", note = " ")
        )
        val tx = transactions.get(id)!!

        assertEquals("USD", tx.currency)
        assertEquals("Amazon", tx.title)
        assertEquals("", tx.note)
        assertEquals(TransactionSource.MANUAL, tx.source)
        assertTrue(tx.uid.isNotBlank())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a zero amount is refused`() = runTest {
        transactions.add(TransactionDraft(cash, Direction.DEBIT, 0, now, TransactionKind.PURCHASE))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a negative amount is refused, the direction carries the sign`() = runTest {
        transactions.add(TransactionDraft(cash, Direction.DEBIT, -500, now, TransactionKind.PURCHASE))
    }

    @Test
    fun `an edit rewrites in place, keeping uid, source and creation time`() = runTest {
        val id = transactions.add(
            TransactionDraft(cash, Direction.DEBIT, 5_000, now, TransactionKind.PURCHASE, source = TransactionSource.RECONCILE)
        )
        val before = transactions.get(id)!!

        transactions.update(id, TransactionDraft(salary, Direction.CREDIT, 7_000, now, TransactionKind.REFUND, title = "Refund"))
        val after = transactions.get(id)!!

        assertEquals(before.uid, after.uid)
        assertEquals(before.createdAt, after.createdAt)
        assertEquals(TransactionSource.RECONCILE, after.source)
        assertEquals(salary, after.accountId)
        assertEquals(7_000L, after.amountMinor)
        assertEquals(0L, balance(cash))
        assertEquals(7_000L, balance(salary))
    }

    @Test
    fun `a move debits one account and credits the other, and pairs the legs`() = runTest {
        transactions.add(TransactionDraft(salary, Direction.CREDIT, 1_800_000, now, TransactionKind.SALARY))

        val outId = transactions.addTransfer(TransferDraft(salary, cash, 50_000, now, TransactionKind.ATM_WITHDRAWAL))

        assertEquals(1_750_000L, balance(salary))
        assertEquals(50_000L, balance(cash))

        val pair = transactions.getTransferFor(outId)!!
        assertEquals(outId, pair.outTransactionId)
        assertEquals(1.0, pair.matchConfidence, 0.0)
        assertEquals(Direction.CREDIT, transactions.get(pair.inTransactionId)!!.direction)
        assertEquals(pair, transactions.getTransferFor(pair.inTransactionId))
    }

    @Test
    fun `a move reads as one row titled by its two accounts`() = runTest {
        val outId = transactions.addTransfer(TransferDraft(salary, cash, 50_000, now, TransactionKind.ATM_WITHDRAWAL))
        val details = transactions.observeAll().first()

        assertEquals(2, details.size)
        assertTrue(details.all { it.isInternalTransfer })

        val feed = feedOf(details)
        assertEquals(listOf(outId), feed.map { it.transaction.id })
        assertEquals("Salary → Cash", titleOf(feed.single()))
        assertEquals("500.00", signedAmount(feed.single()))

        // Narrowed to the wallet, its own leg shows, still titled sending side first.
        val walletFeed = feedOf(details, accountId = cash)
        assertTrue(walletFeed.single().isTransferInLeg)
        assertEquals("Salary → Cash", titleOf(walletFeed.single()))
    }

    @Test
    fun `moves and corrections are neither income nor spending`() = runTest {
        transactions.add(TransactionDraft(salary, Direction.CREDIT, 1_800_000, now, TransactionKind.SALARY))
        transactions.add(TransactionDraft(cash, Direction.DEBIT, 21_450, now, TransactionKind.PURCHASE))
        transactions.addTransfer(TransferDraft(salary, cash, 50_000, now, TransactionKind.ATM_WITHDRAWAL))
        transactions.add(TransactionDraft(cash, Direction.CREDIT, 2_000, now, TransactionKind.ADJUSTMENT))
        transactions.add(TransactionDraft(dollars, Direction.DEBIT, 9_999, now, TransactionKind.PURCHASE))

        val totals = inOut(transactions.observeAll().first(), "SAR")

        assertEquals(1_800_000L, totals.inMinor)
        assertEquals(21_450L, totals.outMinor)
    }

    @Test
    fun `deleting either leg of a move deletes both`() = runTest {
        val outId = transactions.addTransfer(TransferDraft(salary, cash, 50_000, now, TransactionKind.ATM_WITHDRAWAL))
        val inId = transactions.getTransferFor(outId)!!.inTransactionId

        transactions.delete(inId)

        assertNull(transactions.get(outId))
        assertNull(transactions.get(inId))
        assertTrue(transactions.getAllTransfers().isEmpty())
        assertEquals(0L, balance(salary))
        assertEquals(0L, balance(cash))
    }

    @Test
    fun `deleting a single transaction leaves the rest alone`() = runTest {
        val keep = transactions.add(TransactionDraft(cash, Direction.DEBIT, 1_000, now, TransactionKind.PURCHASE))
        val drop = transactions.add(TransactionDraft(cash, Direction.DEBIT, 2_000, now, TransactionKind.PURCHASE))

        transactions.delete(drop)

        assertEquals(listOf(keep), transactions.getAll().map { it.id })
        assertEquals(-1_000L, balance(cash))
    }

    @Test
    fun `editing a move rewrites both legs, keeping their ids and uids`() = runTest {
        val outId = transactions.addTransfer(TransferDraft(salary, cash, 50_000, now, TransactionKind.ATM_WITHDRAWAL))
        val pair = transactions.getTransferFor(outId)!!
        val uids = listOf(transactions.get(pair.outTransactionId)!!.uid, transactions.get(pair.inTransactionId)!!.uid)

        transactions.updateTransfer(pair.inTransactionId, TransferDraft(cash, salary, 20_000, now, TransactionKind.CASH_DEPOSIT, note = "back"))

        val outLeg = transactions.get(pair.outTransactionId)!!
        val inLeg = transactions.get(pair.inTransactionId)!!
        assertEquals(uids, listOf(outLeg.uid, inLeg.uid))
        assertEquals(cash, outLeg.accountId)
        assertEquals(Direction.DEBIT, outLeg.direction)
        assertEquals(salary, inLeg.accountId)
        assertEquals("back", inLeg.note)
        assertEquals(-20_000L, balance(cash))
        assertEquals(20_000L, balance(salary))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a move needs two different accounts`() = runTest {
        transactions.addTransfer(TransferDraft(cash, cash, 1_000, now, TransactionKind.INTERNAL_TRANSFER))
    }

    @Test
    fun `a move across currencies is refused and writes nothing`() = runTest {
        val refused = runCatching {
            transactions.addTransfer(TransferDraft(salary, dollars, 1_000, now, TransactionKind.INTERNAL_TRANSFER))
        }

        assertTrue(refused.exceptionOrNull() is IllegalArgumentException)
        assertTrue(transactions.getAll().isEmpty())
        assertFalse(transactions.observeAll().first().any { it.isInternalTransfer })
    }

    @Test
    fun `the feed is newest first`() = runTest {
        val older = transactions.add(TransactionDraft(cash, Direction.DEBIT, 100, now.minusSeconds(3_600), TransactionKind.PURCHASE))
        val newer = transactions.add(TransactionDraft(cash, Direction.DEBIT, 100, now, TransactionKind.PURCHASE))

        assertEquals(listOf(newer, older), transactions.observeAll().first().map { it.transaction.id })
    }
}
