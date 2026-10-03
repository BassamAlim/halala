package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.definitions.DefinitionsFile
import androidx.test.core.app.ApplicationProvider
import bassamalim.halala.core.data.TEST_CLOCK
import bassamalim.halala.core.data.dataSources.room.AppDatabase
import bassamalim.halala.core.data.dataSources.room.entities.Account
import bassamalim.halala.core.data.testDatabase
import bassamalim.halala.core.domain.LoanState
import bassamalim.halala.core.domain.inOut
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.LoanDirection
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.models.TransactionDraft
import bassamalim.halala.core.models.TransferDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class LoansRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var transactions: TransactionsRepository
    private lateinit var classification: ClassificationRepository
    private lateinit var loans: LoansRepository
    private lateinit var people: PeopleRepository
    private var cash = 0L

    @Before
    fun setUp() = runTest {
        db = testDatabase()
        transactions = TransactionsRepository(db.transactionsDao(), db.accountsDao(), TEST_CLOCK)
        classification = ClassificationRepository(db.classificationDao(), db.merchantsDao(), db.transactionsDao(), db.peopleDao(), DefinitionsFile(ApplicationProvider.getApplicationContext()), TEST_CLOCK)
        loans = LoansRepository(db.loansDao(), db.transactionsDao(), TEST_CLOCK)
        people = PeopleRepository(db.peopleDao())
        cash = db.accountsDao().getCashWallet()!!.id
    }

    @After
    fun tearDown() = db.close()

    private suspend fun transfer(title: String, minor: Long, direction: Direction = Direction.DEBIT): Long {
        val id = transactions.add(
            TransactionDraft(
                cash, direction, minor, TEST_CLOCK.instant(),
                if (direction == Direction.DEBIT) TransactionKind.TRANSFER_OUT else TransactionKind.TRANSFER_IN,
                title
            )
        )
        classification.applyRules()
        return id
    }

    private suspend fun state(): LoanState = loans.observeStates().first().single()

    @Test
    fun `lending opens a loan, repayments pay it down, and none of it is spending`() = runTest {
        val lent = transfer("KHALID ALI", 150_000)
        val loanId = loans.open(lent, LocalDate.parse("2026-10-15"))!!

        assertEquals(TransactionKind.LOAN_GIVEN, transactions.get(lent)!!.kind)
        assertEquals(LoanDirection.LENT, state().loan.direction)
        assertEquals(150_000, state().remainingMinor)

        val back = transfer("Khalid Ali", 50_000, Direction.CREDIT)
        assertTrue(loans.repay(loanId, back))
        assertEquals(TransactionKind.LOAN_REPAYMENT, transactions.get(back)!!.kind)
        assertEquals(100_000, state().remainingMinor)

        // Neither the lending nor the repayment is spending or income.
        val totals = inOut(transactions.observeAll().first(), "SAR")
        assertEquals(0, totals.inMinor)
        assertEquals(0, totals.outMinor)
    }

    @Test
    fun `a transfer can't repay the wrong way, twice, or be lent twice`() = runTest {
        val lent = transfer("KHALID ALI", 150_000)
        val loanId = loans.open(lent, null)!!
        val outAgain = transfer("KHALID ALI", 10_000)

        assertFalse(loans.repay(loanId, outAgain))
        assertNull(loans.open(lent, null))
        val back = transfer("KHALID ALI", 10_000, Direction.CREDIT)
        assertTrue(loans.repay(loanId, back))
        assertFalse(loans.repay(loanId, back))
    }

    @Test
    fun `forgiving settles it, and paying more than owed settles it no further`() = runTest {
        val loanId = loans.open(transfer("KHALID ALI", 150_000), null)!!
        loans.repay(loanId, transfer("KHALID ALI", 200_000, Direction.CREDIT))
        assertEquals(0, state().remainingMinor)
        assertNotNull(state().settledAt)

        val other = loans.open(transfer("SAAD", 30_000), null)!!
        loans.forgive(other)
        assertEquals(listOf(0L, 0L), loans.observeStates().first().map { it.remainingMinor })
    }

    @Test
    fun `taking the lending out drops the loan and frees its repayments`() = runTest {
        val lent = transfer("KHALID ALI", 150_000)
        val loanId = loans.open(lent, null)!!
        val back = transfer("KHALID ALI", 50_000, Direction.CREDIT)
        loans.repay(loanId, back)

        loans.unlink(back)
        assertEquals(TransactionKind.TRANSFER_IN, transactions.get(back)!!.kind)
        assertEquals(150_000, state().remainingMinor)

        loans.repay(loanId, back)
        loans.unlink(lent)
        assertEquals(emptyList<LoanState>(), loans.observeStates().first())
        assertEquals(TransactionKind.TRANSFER_OUT, transactions.get(lent)!!.kind)
        assertEquals(TransactionKind.TRANSFER_IN, transactions.get(back)!!.kind)
    }

    @Test
    fun `merging people moves their loans`() = runTest {
        transfer("KHALID ALI", 1_000)
        transfer("KHALID A ALQAHTANI", 150_000)
        val (khalid, other) = db.peopleDao().getPeople()
        loans.open(db.transactionsDao().getAll().last().id, null)

        people.merge(other.id, khalid.id)
        assertEquals(khalid.id, state().loan.personId)
    }

    @Test
    fun `a move between your own accounts is never a loan`() = runTest {
        transfer("KHALID ALI", 1_000)
        val bank = db.accountsDao().insert(
            Account(uid = "b", institutionId = null, nickname = "Salary", type = AccountType.CURRENT, currency = "SAR", createdAt = TEST_CLOCK.instant())
        )
        // A move titled like someone you know is still your own money.
        val leg = transactions.addTransfer(TransferDraft(bank, cash, 5_000, TEST_CLOCK.instant(), TransactionKind.TRANSFER_OUT, "KHALID ALI"))
        assertNull(loans.open(leg, null))
    }

    @Test
    fun `a split bill is your share of spending, and each share is owed to you`() = runTest {
        val dinner = transactions.add(TransactionDraft(cash, Direction.DEBIT, 30_000, TEST_CLOCK.instant(), TransactionKind.PURCHASE, "Al Romansiah"))
        val faisal = people.add("Faisal")!!
        val khalid = people.add("Khalid")!!

        assertFalse(loans.split(dinner, mapOf(faisal to 20_000, khalid to 20_000)))
        assertTrue(loans.split(dinner, mapOf(faisal to 10_000, khalid to 10_000)))
        assertFalse(loans.split(dinner, mapOf(faisal to 1)))

        assertEquals(10_000, inOut(transactions.observeAll().first(), "SAR").outMinor)
        assertEquals(2, loans.observeStates().first().count { it.isOpen && it.loan.splitOf == dinner })

        val back = transactions.add(TransactionDraft(cash, Direction.CREDIT, 10_000, TEST_CLOCK.instant(), TransactionKind.TRANSFER_IN, "Faisal"))
        loans.repay(loans.observeStates().first().first { it.loan.personId == faisal }.loan.id, back)
        loans.unsplit(dinner)
        assertEquals(emptyList<LoanState>(), loans.observeStates().first())
        assertEquals(TransactionKind.TRANSFER_IN, transactions.get(back)!!.kind)
        assertEquals(30_000, inOut(transactions.observeAll().first(), "SAR").outMinor)
    }

    @Test
    fun `a transfer can be lent to someone other than the person it pays`() = runTest {
        val shop = transfer("MOHAMMED SALEH", 50_000)
        val faisal = people.add("Faisal")!!
        assertNotNull(loans.open(shop, LocalDate.of(2026, 11, 1), faisal))

        val loan = state()
        assertEquals(faisal, loan.loan.personId)
        assertEquals(50_000, loan.remainingMinor)
        assertEquals(TransactionKind.LOAN_GIVEN, transactions.get(shop)!!.kind)

        val back = transfer("FAISAL", 50_000, Direction.CREDIT)
        assertTrue(loans.repay(loan.loan.id, back))
        assertFalse(state().isOpen)
    }

    @Test
    fun `a purchase paid for someone is all owed to you and none of it your spending`() = runTest {
        val gift = transactions.add(TransactionDraft(cash, Direction.DEBIT, 25_000, TEST_CLOCK.instant(), TransactionKind.PURCHASE, "Jarir"))
        val faisal = people.add("Faisal")!!
        val due = LocalDate.of(2026, 11, 1)

        assertTrue(loans.split(gift, mapOf(faisal to 25_000), due))
        assertEquals(0, inOut(transactions.observeAll().first(), "SAR").outMinor)
        assertEquals(25_000, state().remainingMinor)
        assertEquals(due, state().loan.dueOn)

        loans.unsplit(gift)
        assertEquals(25_000, inOut(transactions.observeAll().first(), "SAR").outMinor)
    }

    @Test
    fun `a transfer naming nobody can be lent to someone chosen`() = runTest {
        assertNotNull(loans.open(transfer("", 1_000), null, people.add("Faisal")!!))
    }

    @Test
    fun `nobody named, nothing to lend to`() = runTest {
        assertNull(loans.open(transfer("", 1_000), null))
    }
}
