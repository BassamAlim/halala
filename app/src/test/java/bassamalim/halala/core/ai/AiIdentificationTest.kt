package bassamalim.halala.core.ai

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import bassamalim.halala.core.data.TEST_CLOCK
import bassamalim.halala.core.data.dataSources.room.AppDatabase
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.IdentifiedAs
import bassamalim.halala.core.data.repositories.PreferencesRepository
import bassamalim.halala.core.data.repositories.SmsRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.data.testDatabase
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.BusinessType
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.IdentifiedBy
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.models.AccountDraft
import bassamalim.halala.core.models.TransactionDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AiIdentificationTest {

    /** Groq, as a test sees it: what it was sent, and what it answers. */
    private class FakeGroq(var answer: (String) -> IdentifiedAs? = { IdentifiedAs(it, BusinessType.CAFE, 95) }) :
        MerchantIdentifier {
        val sent = mutableListOf<List<String>>()

        override suspend fun identify(names: List<String>): List<IdentifiedAs?> {
            sent += names
            return names.map(answer)
        }
    }

    private class FakeKeys(var key: String? = "gsk_test") : ApiKeys {
        override fun groq() = key
    }

    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var transactions: TransactionsRepository
    private lateinit var classification: ClassificationRepository
    private lateinit var accounts: AccountsRepository
    private lateinit var preferences: PreferencesRepository
    private val groq = FakeGroq()
    private val keys = FakeKeys()
    private lateinit var identification: AiIdentification
    private var bank = 0L

    @Before
    fun setUp() = runTest {
        db = testDatabase()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "ai-test.preferences_pb")
        file.delete()
        preferences = PreferencesRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
        transactions = TransactionsRepository(db.transactionsDao(), db.accountsDao(), TEST_CLOCK)
        classification = ClassificationRepository(db.classificationDao(), db.merchantsDao(), db.transactionsDao(), TEST_CLOCK)
        accounts = AccountsRepository(db.accountsDao(), TEST_CLOCK)
        val rajhi = db.institutionsDao().getAll().first { it.name == "Al Rajhi" }.id
        bank = accounts.create(AccountDraft(rajhi, "Salary", AccountType.CURRENT, "5521", "SAR", 0))
        identification = AiIdentification(classification, accounts, SmsRepository(db.smsDao()), preferences, keys, groq)
        preferences.setAiEnabled(true)
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    private suspend fun spend(title: String): Long {
        val id = transactions.add(
            TransactionDraft(bank, Direction.DEBIT, 1000, TEST_CLOCK.instant(), TransactionKind.PURCHASE, title)
        )
        classification.applyRules()
        return id
    }

    private suspend fun merchantOf(id: Long) =
        classification.getMerchant(transactions.observe(id).first()!!.merchantId!!)!!

    @Test
    fun `only the names nothing else knows are sent, and each is asked about once`() = runTest {
        spend("PANDA 1042")
        val cafe = spend("QAHWAT HUDA 77")

        identification.run()
        identification.run()

        assertEquals(listOf(listOf("QAHWAT HUDA 77")), groq.sent)
        assertEquals(IdentifiedBy.AI, merchantOf(cafe).identifiedBy)
        assertEquals("Restaurants", classification.getCategories().single { it.id == transactions.get(cafe)!!.categoryId }.name)
    }

    @Test
    fun `a name holding your account's digits is kept back, never sent`() = runTest {
        val risky = spend("PAY 5521 ZZYZX")
        spend("QAHWAT HUDA 77")

        identification.run()

        assertEquals(listOf(listOf("QAHWAT HUDA 77")), groq.sent)
        assertEquals(IdentifiedBy.WITHHELD, merchantOf(risky).identifiedBy)
    }

    @Test
    fun `nothing is sent while it is off or has no key`() = runTest {
        spend("QAHWAT HUDA 77")

        preferences.setAiEnabled(false)
        identification.run()
        preferences.setAiEnabled(true)
        keys.key = null
        identification.run()

        assertTrue(groq.sent.isEmpty())
    }

    @Test
    fun `a merchant left out of the answer waits for you and isn't asked again`() = runTest {
        val left = spend("ZZYZX 9")
        groq.answer = { null }

        identification.run()
        identification.run()

        assertEquals(1, groq.sent.size)
        assertEquals(BusinessType.UNKNOWN, merchantOf(left).businessType)
        assertTrue(classification.toIdentify().isEmpty())
    }
}
