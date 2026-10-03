package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.definitions.DefinitionsFile
import androidx.test.core.app.ApplicationProvider
import bassamalim.halala.core.data.TEST_CLOCK
import bassamalim.halala.core.data.dataSources.room.AppDatabase
import bassamalim.halala.core.data.dataSources.room.entities.Account
import bassamalim.halala.core.data.dataSources.room.entities.RawMessage
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
        classification = ClassificationRepository(db.classificationDao(), db.merchantsDao(), db.transactionsDao(), db.peopleDao(), DefinitionsFile(ApplicationProvider.getApplicationContext()), TEST_CLOCK)
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
    fun `someone who pays your salary makes their transfers in salary from then on`() = runTest {
        fun at(days: Long, direction: Direction = Direction.CREDIT) = TransactionDraft(
            cash, direction, 900_000, TEST_CLOCK.instant().plusSeconds(days * 86_400),
            if (direction == Direction.DEBIT) TransactionKind.TRANSFER_OUT else TransactionKind.TRANSFER_IN, "ACME TRADING CO"
        )
        val before = transactions.add(at(-30))
        val marked = transactions.add(at(0))
        val sent = transactions.add(at(1, Direction.DEBIT))
        classification.applyRules()
        val employer = personOf(marked).personId!!

        people.setSalarySince(employer, transactions.get(marked)!!.occurredAt)
        val next = transactions.add(at(30))
        classification.applyRules()

        assertEquals(TransactionKind.TRANSFER_IN, transactions.get(before)!!.kind)
        assertEquals(TransactionKind.SALARY, transactions.get(marked)!!.kind)
        assertEquals(TransactionKind.TRANSFER_OUT, transactions.get(sent)!!.kind)
        assertEquals(TransactionKind.SALARY, transactions.get(next)!!.kind)

        people.setSalarySince(employer, null)
        val after = transactions.add(at(60))
        classification.applyRules()
        assertEquals(TransactionKind.TRANSFER_IN, transactions.get(after)!!.kind)
        assertEquals(TransactionKind.SALARY, transactions.get(next)!!.kind)
    }

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

    @Test
    fun `the digits a bank quoted for someone are read from their transfers' SMS`() = runTest {
        val latin = transfer("AHMED ALI", Direction.CREDIT)
        val arabic = transfer("أحمد علي")
        transfer("KHALID SALEH")
        classification.applyRules()
        val bodies = mapOf(
            latin to "حوالة محلية واردة بـSR 3000\nلـ1111\nمن7700;AHMED null ALI\n26/10/1 21:34",
            arabic to "حوالة محلية صادرة بـSR 2500\nمن2222\nلـ7700;أحمد علي\nرسوم:SR 0.58\n26/9/24 04:28"
        )
        for ((id, body) in bodies) {
            val raw = db.smsDao().insertRaw(RawMessage(sender = "AlRajhiBank", body = body, receivedAt = TEST_CLOCK.instant(), hash = "h$id"))
            db.openHelper.writableDatabase.execSQL("UPDATE transactions SET rawMessageId = $raw WHERE id = $id")
        }

        val refs = people.observeAccountRefs().first()

        assertEquals(
            mapOf(personOf(latin).personId to setOf("7700"), personOf(arabic).personId to setOf("7700")),
            refs
        )
    }
}
