package bassamalim.halala.core.sms

import bassamalim.halala.core.data.TEST_CLOCK
import bassamalim.halala.core.data.dataSources.room.AppDatabase
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.InstitutionsRepository
import bassamalim.halala.core.data.repositories.SmsRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.data.testDatabase
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.RawStatus
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.models.AccountDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant

/** The whole pipeline on a real (in-memory) database, with fixture-style messages. */
@RunWith(RobolectricTestRunner::class)
class SmsIngestTest {

    private lateinit var db: AppDatabase
    private lateinit var accounts: AccountsRepository
    private lateinit var transactions: TransactionsRepository
    private lateinit var ingest: SmsIngest

    private var rajhiMain = 0L
    private var rajhiSavings = 0L
    private var snb = 0L
    private var stc = 0L
    private val t0: Instant = TEST_CLOCK.instant()

    @Before
    fun setUp() = runTest {
        db = testDatabase()
        accounts = AccountsRepository(db.accountsDao(), TEST_CLOCK)
        transactions = TransactionsRepository(db.transactionsDao(), db.accountsDao(), TEST_CLOCK)
        ingest = SmsIngest(
            SmsRepository(db.smsDao()),
            transactions,
            accounts,
            InstitutionsRepository(db.institutionsDao()),
            TEST_CLOCK
        )
        val banks = db.institutionsDao().getAll().associate { it.name to it.id }
        rajhiMain = accounts.create(AccountDraft(banks["Al Rajhi"], "Main", AccountType.CURRENT, "1111", "SAR", 0))
        rajhiSavings = accounts.create(AccountDraft(banks["Al Rajhi"], "Savings", AccountType.SAVINGS, "3333", "SAR", 0))
        snb = accounts.create(AccountDraft(banks["SNB"], "SNB", AccountType.CURRENT, "4444", "SAR", 0))
        stc = accounts.create(AccountDraft(banks["STC Bank"], "Daily", AccountType.WALLET, "6666", "SAR", 0))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun receive(sender: String, body: String, minutes: Long = 0) {
        ingest.store(sender, body.trimIndent(), t0 + Duration.ofMinutes(minutes))
        ingest.processPending()
    }

    private suspend fun balance(id: Long) = accounts.observe(id).first()!!.balanceMinor
    private suspend fun statuses() = db.query("SELECT status FROM raw_messages ORDER BY id", null).use { c ->
        buildList { while (c.moveToNext()) add(RawStatus.valueOf(c.getString(0))) }
    }

    private val purchase = """
        شراء انترنت بـSR 62
        عبر9001;مدى
        من1111
        لـJahez
        26/6/4 02:19
    """

    @Test
    fun `a purchase lands on the account its digits name`() = runTest {
        receive("AlRajhiBank", purchase)

        assertEquals(-6_200L, balance(rajhiMain))
        val recorded = transactions.getAll().single()
        assertEquals("Jahez", recorded.title)
        assertEquals(TransactionKind.PURCHASE, recorded.kind)
        assertEquals(listOf(RawStatus.RECORDED), statuses())
    }

    @Test
    fun `the same SMS read twice is kept once`() = runTest {
        val at = t0
        ingest.store("AlRajhiBank", purchase, at)
        assertNull(ingest.store("AlRajhiBank", purchase, at))
        assertNull(ingest.store("Mom", "hi", at))
    }

    @Test
    fun `a resent SMS is a duplicate, two alike purchases are not`() = runTest {
        receive("AlRajhiBank", purchase)
        receive("AlRajhiBank", purchase, minutes = 1)
        receive("AlRajhiBank", purchase.replace("Jahez", "Keeta"), minutes = 2)

        assertEquals(listOf(RawStatus.RECORDED, RawStatus.DUPLICATE, RawStatus.RECORDED), statuses())
        assertEquals(-12_400L, balance(rajhiMain))
    }

    @Test
    fun `unknown digits wait for you, then go where you say`() = runTest {
        val cardOnly = """
            شراء
            بطاقة:9002;مدى
            مبلغ:SAR 50
            لدى:ALDREES
            في:23-11-22 09:05
        """
        receive("AlRajhiBank", cardOnly)
        assertEquals(listOf(RawStatus.UNROUTED), statuses())

        ingest.assign("AlRajhiBank", "9002", rajhiMain)
        assertEquals(listOf(RawStatus.RECORDED), statuses())
        assertEquals(-5_000L, balance(rajhiMain))

        receive("AlRajhiBank", cardOnly.replace("ALDREES", "SASCO"), minutes = 10)
        assertEquals(-10_000L, balance(rajhiMain))
    }

    @Test
    fun `a move between your accounts at one bank records both legs, paired`() = runTest {
        receive("AlRajhiBank", """
            حوالة بين حساباتك
            مبلغ:SR 500
            من:1111
            الى:3333
            26/9/24 04:35
        """)

        assertEquals(-50_000L, balance(rajhiMain))
        assertEquals(50_000L, balance(rajhiSavings))
        assertEquals(1, transactions.getAllTransfers().size)
    }

    @Test
    fun `a transfer to your other bank pairs with its arrival`() = runTest {
        receive("SNB-AlAhli", """
            حوالة صادرة محلية
            من:4444*
            إلى:أحمد -. -
            عبر:AL RAJHI BANK
            آيبان:*1111
            مبلغ:1250.25 SAR
            في:12/09/26 18:01
        """)
        receive("AlRajhiBank", """
            حوالة محلية واردة بـSR 1250.25
            لـ1111
            من4444;أحمد علي
            26/9/12 18:30
        """, minutes = 30)

        val pair = transactions.getAllTransfers().single()
        assertEquals(SmsIngest.LINKED_CONFIDENCE, pair.matchConfidence, 0.0)
        assertEquals(snb, transactions.get(pair.outTransactionId)!!.accountId)
        assertEquals(rajhiMain, transactions.get(pair.inTransactionId)!!.accountId)
    }

    @Test
    fun `fees are their own debit`() = runTest {
        receive("AlRajhiBank", """
            حوالة محلية صادرة بـSR 2500
            من1111
            لـ7700;أحمد علي
            رسوم:SR 0.58
            26/9/24 04:28
        """)

        assertEquals(-250_058L, balance(rajhiMain))
        assertEquals(1, transactions.getAll().count { it.kind == TransactionKind.FEE })
    }

    @Test
    fun `a reported balance becomes the account's balance`() = runTest {
        receive("STC Bank", """
            Online Purchase
            Via: *9003,Visa
            Amount: 23 USD
            From: ANTHRO
            Total due amount: 88.1 SAR
            Remaining balance: 80.47 SAR
            At: 30/09/26 23:40
        """.replace("*9003", "*6666"))
        assertEquals(8_047L, balance(stc))

        receive("STC Bank", """
            Notification: Refund
            Transaction: Noon One Subscription
            Card: ***6666
            Amount: 1 SAR
            Date: 27/09/26 07:10
        """, minutes = 5)
        assertEquals(8_147L, balance(stc))

        val charge = transactions.getAll().first { it.kind == TransactionKind.PURCHASE }
        assertEquals(2_300L, charge.originalAmountMinor)
        assertEquals("USD", charge.originalCurrency)
    }

    @Test
    fun `a charge in another currency with no converted amount waits`() = runTest {
        receive("AlRajhiBank", """
            شراء انترنت
            بطاقة:1111;مدى
            مبلغ:USD 7.99
            لدى:PLAYSTATIONNETWORK
            في:23-11-17 12:32
        """)
        assertEquals(listOf(RawStatus.FOREIGN), statuses())
        assertEquals(0L, balance(rajhiMain))
    }
}
