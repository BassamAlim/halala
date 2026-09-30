package bassamalim.halala.features.editAccount

import bassamalim.halala.core.data.TEST_CLOCK
import bassamalim.halala.core.data.dataSources.room.AppDatabase
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.InstitutionsRepository
import bassamalim.halala.core.data.testDatabase
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.features.editAccount.EditAccountDomain.Checked
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EditAccountDomainTest {

    private lateinit var db: AppDatabase
    private lateinit var domain: EditAccountDomain
    private lateinit var accounts: AccountsRepository
    private var rajhi = 0L

    @Before
    fun setUp() = runTest {
        db = testDatabase()
        accounts = AccountsRepository(db.accountsDao(), TEST_CLOCK)
        domain = EditAccountDomain(accounts, InstitutionsRepository(db.institutionsDao()))
        rajhi = db.institutionsDao().getAll().first { it.name == "Al Rajhi" }.id
    }

    @After
    fun tearDown() = db.close()

    private fun form(
        institutionId: Long? = 1,
        type: AccountType = AccountType.CURRENT,
        name: String = "Salary",
        last4: String = "4821",
        currency: String = "SAR",
        opening: String = ""
    ) = AccountForm(institutionId, type, name, last4, currency, opening)

    private fun problems(form: AccountForm) = (EditAccountDomain.validate(form) as Checked.Invalid).problems

    @Test
    fun `a complete form becomes a draft in halalas`() {
        val draft = (EditAccountDomain.validate(form(opening = "1,250.75")) as Checked.Valid).draft

        assertEquals(1L, draft.institutionId)
        assertEquals("4821", draft.last4)
        assertEquals(125_075L, draft.openingBalanceMinor)
    }

    @Test
    fun `a blank opening balance is zero, and a minus is money owed`() {
        assertEquals(0L, (EditAccountDomain.validate(form()) as Checked.Valid).draft.openingBalanceMinor)
        assertEquals(-50_000L, (EditAccountDomain.validate(form(opening = "-500")) as Checked.Valid).draft.openingBalanceMinor)
    }

    @Test
    fun `a bank account needs a name, a bank and exactly four digits`() {
        assertEquals(
            setOf(AccountProblem.NameMissing, AccountProblem.BankMissing, AccountProblem.Last4Invalid),
            problems(form(institutionId = null, name = " ", last4 = "482"))
        )
        assertEquals(setOf(AccountProblem.Last4Invalid), problems(form(last4 = "48a1")))
    }

    @Test
    fun `the wallet needs only a name`() {
        val draft = (EditAccountDomain.validate(form(institutionId = 3, type = AccountType.CASH, last4 = "")) as Checked.Valid).draft

        assertEquals(null, draft.institutionId)
        assertEquals(null, draft.last4)
    }

    @Test
    fun `currencies must be real, and the balance an amount in them`() {
        assertEquals(setOf(AccountProblem.CurrencyInvalid), problems(form(currency = "XX")))
        assertEquals(setOf(AccountProblem.OpeningBalanceInvalid), problems(form(opening = "12.345")))
        assertEquals(1_500L, (EditAccountDomain.validate(form(currency = "kwd", opening = "1.5")) as Checked.Valid).draft.openingBalanceMinor)
    }

    @Test
    fun `saving refuses last four digits another account at the bank already has`() = runTest {
        assertEquals(AccountSave.Saved, domain.save(0, form(institutionId = rajhi, name = "Salary")))

        val second = domain.save(0, form(institutionId = rajhi, name = "Other"))

        assertEquals(AccountSave.Invalid(setOf(AccountProblem.Last4Taken("Salary"))), second)
        assertEquals(2, accounts.getAll().size)
    }

    @Test
    fun `an account may keep its own digits when edited`() = runTest {
        domain.save(0, form(institutionId = rajhi, name = "Salary"))
        val id = accounts.getAll().first { it.nickname == "Salary" }.id

        assertEquals(AccountSave.Saved, domain.save(id, form(institutionId = rajhi, name = "Main")))
        assertTrue(accounts.getAll().any { it.nickname == "Main" && it.id == id })
    }
}
