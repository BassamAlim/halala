package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.TEST_CLOCK
import bassamalim.halala.core.data.dataSources.room.AppDatabase
import bassamalim.halala.core.data.dataSources.room.entities.Account
import bassamalim.halala.core.data.testDatabase
import bassamalim.halala.core.domain.titleOf
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.models.TransactionDraft
import bassamalim.halala.core.models.TransferDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PeopleRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var transactions: TransactionsRepository
    private lateinit var classification: ClassificationRepository
    private lateinit var people: PeopleRepository
    private var cash = 0L

    @Before
    fun setUp() = runTest {
        db = testDatabase()
        transactions = TransactionsRepository(db.transactionsDao(), db.accountsDao(), TEST_CLOCK)
        classification = ClassificationRepository(db.classificationDao(), db.merchantsDao(), db.transactionsDao(), db.peopleDao(), TEST_CLOCK)
        people = PeopleRepository(db.peopleDao())
        cash = db.accountsDao().getCashWallet()!!.id
    }

    @After
    fun tearDown() = db.close()

    private suspend fun transfer(title: String, direction: Direction = Direction.DEBIT, kind: TransactionKind? = null) =
        transactions.add(
            TransactionDraft(
                cash, direction, 10_000, TEST_CLOCK.instant(),
                kind ?: if (direction == Direction.DEBIT) TransactionKind.TRANSFER_OUT else TransactionKind.TRANSFER_IN,
                title
            )
        )

    private suspend fun personOf(id: Long) = transactions.observe(id).first()!!

    @Test
    fun `each name a transfer is written with becomes one person, and only transfers do`() = runTest {
        val sent = transfer("KHALID ALI")
        val back = transfer("Khalid Ali 0412", Direction.CREDIT)
        val other = transfer("KHALID SALEH")
        val shop = transactions.add(
            TransactionDraft(cash, Direction.DEBIT, 500, TEST_CLOCK.instant(), TransactionKind.PURCHASE, "Khalid Ali")
        )

        classification.applyRules()
        classification.applyRules() // Idempotent: nobody is made twice.

        val all = db.peopleDao().getPeople()
        assertEquals(listOf("Khalid Ali", "Khalid Saleh"), all.map { it.name })
        assertEquals(personOf(sent).personId, personOf(back).personId)
        assertEquals("Khalid Ali", titleOf(personOf(back)))
        assertEquals(all[1].id, personOf(other).personId)
        assertNull(personOf(shop).personId)
    }

    @Test
    fun `a move between your own accounts names nobody`() = runTest {
        val bank = db.accountsDao().insert(
            Account(uid = "b", institutionId = null, nickname = "Salary", type = AccountType.CURRENT, currency = "SAR", createdAt = TEST_CLOCK.instant())
        )
        val id = transactions.addTransfer(
            TransferDraft(bank, cash, 5_000, TEST_CLOCK.instant(), TransactionKind.TRANSFER_OUT, "Bassam Alim")
        )
        classification.applyRules()

        assertEquals(emptyList<String>(), db.peopleDao().getPeople().map { it.name })
        assertNull(personOf(id).personId)
    }

    @Test
    fun `merging moves the spellings, and splitting takes one back out`() = runTest {
        val a = transfer("KHALID ALI")
        val b = transfer("KHALID A ALQAHTANI")
        classification.applyRules()
        val (khalid, other) = db.peopleDao().getPeople()

        people.rename(khalid.id, "Khalid A.")
        people.merge(other.id, khalid.id)
        assertEquals(listOf("Khalid A."), db.peopleDao().getPeople().map { it.name })
        assertEquals(khalid.id, personOf(b).personId)
        assertEquals("Khalid A.", titleOf(personOf(b)))

        val alias = db.peopleDao().getAliases().single { it.descriptor == "KHALID A ALQAHTANI" }
        val split = people.split(alias.id)!!
        assertEquals(split, personOf(b).personId)
        assertEquals(khalid.id, personOf(a).personId)
        // The last spelling can't be taken out: it is the person.
        assertNull(people.split(db.peopleDao().getAliases().single { it.personId == khalid.id }.id))
    }
}
