package bassamalim.halala.core.data.repositories

import android.database.sqlite.SQLiteConstraintException
import bassamalim.halala.core.data.TEST_CLOCK
import bassamalim.halala.core.data.dataSources.room.AppDatabase
import bassamalim.halala.core.data.dataSources.room.Seed
import bassamalim.halala.core.data.testDatabase
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.models.AccountDraft
import bassamalim.halala.core.models.TransactionDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccountsRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var accounts: AccountsRepository
    private lateinit var transactions: TransactionsRepository
    private lateinit var institutions: InstitutionsRepository

    @Before
    fun setUp() {
        db = testDatabase()
        accounts = AccountsRepository(db.accountsDao(), TEST_CLOCK)
        transactions = TransactionsRepository(db.transactionsDao(), db.accountsDao(), TEST_CLOCK)
        institutions = InstitutionsRepository(db.institutionsDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun bank(name: String) = institutions.getAll().first { it.name == name }.id

    private suspend fun create(
        name: String,
        bank: String = "Al Rajhi",
        last4: String? = "4821",
        currency: String = "SAR",
        opening: Long = 0
    ) = accounts.create(
        AccountDraft(
            institutionId = bank(bank),
            nickname = name,
            type = AccountType.CURRENT,
            last4 = last4,
            currency = currency,
            openingBalanceMinor = opening
        )
    )

    @Test
    fun `a new database has the v1 banks and an empty cash wallet`() = runTest {
        assertEquals(Seed.INSTITUTIONS.sorted(), institutions.getAll().map { it.name }.sorted())

        val all = accounts.observeAll().first()
        assertEquals(1, all.size)

        val wallet = all.single()
        assertEquals(AccountType.CASH, wallet.account.type)
        assertEquals("SAR", wallet.account.currency)
        assertNull(wallet.account.institutionId)
        assertEquals(0L, wallet.balanceMinor)
        assertEquals(wallet.account.id, accounts.getCashWallet()?.id)
    }

    @Test
    fun `creating trims the name, uppercases the currency and gives a stable uid`() = runTest {
        val id = accounts.create(
            AccountDraft(bank("SNB"), "  Salary ", AccountType.CURRENT, " 3391 ", "usd", 0)
        )
        val account = accounts.get(id)!!

        assertEquals("Salary", account.nickname)
        assertEquals("3391", account.last4)
        assertEquals("USD", account.currency)
        assertTrue(account.uid.isNotBlank())
        assertEquals(TEST_CLOCK.instant(), account.createdAt)
    }

    @Test
    fun `a blank last four is stored as none`() = runTest {
        val id = create("Funds", bank = "Al Rajhi Capital", last4 = "  ")
        assertNull(accounts.get(id)!!.last4)
    }

    @Test
    fun `several accounts at one bank are told apart by their last four digits`() = runTest {
        val salary = create("Salary", last4 = "4821")
        val awaeed = create("Awaeed", last4 = "0913")

        val rajhi = bank("Al Rajhi")
        assertEquals(salary, accounts.findByLast4(rajhi, "4821")?.id)
        assertEquals(awaeed, accounts.findByLast4(rajhi, "0913")?.id)
        assertNull(accounts.findByLast4(rajhi, "7702"))
        assertNull(accounts.findByLast4(bank("D360"), "4821"))
    }

    @Test(expected = SQLiteConstraintException::class)
    fun `two accounts at one bank can't share their last four digits`() = runTest {
        create("Salary", last4 = "4821")
        create("Other", last4 = "4821")
    }

    @Test
    fun `the same last four at two banks is fine`() = runTest {
        create("Salary", bank = "Al Rajhi", last4 = "4821")
        create("Daily", bank = "STC Bank", last4 = "4821")
        assertEquals(3, accounts.getAll().size)
    }

    @Test
    fun `the balance is the opening balance plus credits less debits`() = runTest {
        val id = create("Salary", opening = 100_000)
        transactions.add(TransactionDraft(id, Direction.CREDIT, 1_800_000, TEST_CLOCK.instant(), TransactionKind.SALARY))
        transactions.add(TransactionDraft(id, Direction.DEBIT, 21_450, TEST_CLOCK.instant(), TransactionKind.PURCHASE))
        transactions.add(TransactionDraft(id, Direction.DEBIT, 6_200, TEST_CLOCK.instant(), TransactionKind.PURCHASE))

        val account = accounts.observe(id).first()!!
        assertEquals(100_000L + 1_800_000 - 21_450 - 6_200, account.balanceMinor)
        assertEquals(3, account.transactionCount)
        assertEquals("Al Rajhi", account.institutionName)
    }

    @Test
    fun `a negative opening balance is kept, for money owed`() = runTest {
        val id = create("Card", opening = -250_000)
        assertEquals(-250_000L, accounts.observe(id).first()!!.balanceMinor)
    }

    @Test
    fun `archiving keeps the account and its history, and lists it last`() = runTest {
        val id = create("Old card")
        transactions.add(TransactionDraft(id, Direction.DEBIT, 500, TEST_CLOCK.instant(), TransactionKind.FEE))
        create("Salary", last4 = "0001")

        accounts.setArchived(id, true)

        val all = accounts.observeAll().first()
        assertEquals(id, all.last().account.id)
        assertTrue(all.last().account.archived)
        assertEquals(-500L, all.last().balanceMinor)
        assertEquals(AccountType.CASH, all.first().account.type)

        accounts.setArchived(id, false)
        assertFalse(accounts.get(id)!!.archived)
    }

    @Test
    fun `an update rewrites the row in place and keeps its uid`() = runTest {
        val id = create("Salary")
        val before = accounts.get(id)!!

        accounts.update(id, AccountDraft(bank("Al Rajhi"), "Main", AccountType.SAVINGS, "4821", "SAR", 5_000))
        val after = accounts.get(id)!!

        assertEquals(before.uid, after.uid)
        assertEquals("Main", after.nickname)
        assertEquals(AccountType.SAVINGS, after.type)
        assertEquals(5_000L, after.openingBalanceMinor)
        assertNotEquals(before, after)
        assertNotNull(accounts.getAllWithBalance().firstOrNull { it.account.id == id })
    }
}
