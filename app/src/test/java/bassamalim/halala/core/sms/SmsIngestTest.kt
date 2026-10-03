package bassamalim.halala.core.sms

import bassamalim.halala.core.data.dataSources.definitions.DefinitionsFile
import androidx.test.core.app.ApplicationProvider
import bassamalim.halala.core.data.TEST_CLOCK
import bassamalim.halala.core.data.dataSources.room.AppDatabase
import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.InstitutionsRepository
import bassamalim.halala.core.data.repositories.SavingsRepository
import bassamalim.halala.core.data.repositories.SmsRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.data.testDatabase
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.RawStatus
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import bassamalim.halala.core.models.AccountDraft
import bassamalim.halala.core.models.TransactionDraft
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
            ClassificationRepository(db.classificationDao(), db.merchantsDao(), db.transactionsDao(), db.peopleDao(), DefinitionsFile(ApplicationProvider.getApplicationContext()), TEST_CLOCK),
            SavingsRepository(db.savingsDao(), db.accountsDao(), db.transactionsDao(), db.goalsDao(), transactions, TEST_CLOCK),
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
    fun `a sender's fee inside the amount still pairs, and becomes a fee`() = runTest {
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
            حوالة محلية واردة بـSR 1250
            لـ1111
            من4444;أحمد علي
            26/9/12 18:02
        """, minutes = 1)

        assertEquals(1, transactions.getAllTransfers().size)
        assertEquals(-125_025L, balance(snb))
        assertEquals(125_000L, balance(rajhiMain))
        assertEquals(25L, transactions.getAll().single { it.kind == TransactionKind.FEE }.amountMinor)
    }

    @Test
    fun `a move naming only where it went comes out of the bank's one other account in use`() = runTest {
        val unnamed = """
            حوالة بين حساباتك
            مبلغ: SAR 1500
            الى: 1111
            في: 24-3-14 17:03
        """
        // Savings has had nothing yet, so there is no telling where this came from.
        receive("AlRajhiBank", unnamed)
        assertEquals(150_000L, balance(rajhiMain))
        assertEquals(0L, balance(rajhiSavings))

        receive("AlRajhiBank", """
            حوالة محلية
            عبر:SNB
            مبلغ:SAR 5000
            الى:3333
            من:أحمد علي
            في:25-5-16 21:41
        """, minutes = 10)
        receive("AlRajhiBank", unnamed.replace("1500", "2000"), minutes = 20)

        assertEquals(350_000L, balance(rajhiMain))
        assertEquals(300_000L, balance(rajhiSavings))
        assertEquals(SmsIngest.IMPLIED_CONFIDENCE, transactions.getAllTransfers().single().matchConfidence, 0.0)
    }

    @Test
    fun `two orders for the same amount in the same minute are two orders`() {
        val order = "Subscription Order 6600001\n10.000000 Units\nFund A\nUnit Price 10.0000\nAmount 100.00"
        val next = order.replace("6600001", "6600007").replace("Fund A", "Fund B")
        val arabic = "حوالة محلية\nمن:****8888\nالمبلغ:1435.0"

        assertEquals(false, SmsIngest.isDuplicate(order, next))
        assertEquals(true, SmsIngest.isDuplicate(order, order))
        assertEquals(true, SmsIngest.isDuplicate("Local Transfer\nFrom:****8888\nAmount:1435.0", arabic))
    }

    @Test
    fun `each Awaeed deposit is its own, in one hidden holding account, and isn't spending`() = runTest {
        val deposit = """
            انشاء حساب عوائد
            مبلغ:SR 5000
            من:3333
            26/9/24 04:34
        """
        receive("AlRajhiBank", deposit)
        receive("AlRajhiBank", deposit.replace("5000", "3000"), minutes = 60)

        val awaeed = accounts.getAll().single { it.nickname == "Awaeed" }
        assertEquals(AccountType.DEPOSIT, awaeed.type)
        assertEquals(800_000L, balance(awaeed.id))
        val savings = SavingsRepository(db.savingsDao(), db.accountsDao(), db.transactionsDao(), db.goalsDao(), transactions, TEST_CLOCK)
        assertEquals(listOf(500_000L, 300_000L), savings.observeDeposits().first().map { it.amountMinor })
        assertEquals(-800_000L, balance(rajhiSavings))
        assertEquals(2, transactions.getAllTransfers().size)
        assertEquals(false, TransactionKind.SAVINGS_DEPOSIT.countsInTotals)
        assertEquals(false, TransactionKind.INVESTMENT_BUY.countsInTotals)

        // Paying one out moves it back where it came from and leaves the other running.
        savings.payOut(savings.getDeposits().first().id)
        assertEquals(listOf(300_000L), savings.observeDeposits().first().map { it.amountMinor })
        assertEquals(300_000L, balance(awaeed.id))
        assertEquals(-300_000L, balance(rajhiSavings))

        // The bank's own closing SMS ends the one it fits; what is above what went in is profit.
        receive("AlRajhiBank", deposit.replace("انشاء", "اقفال").replace("من:", "الى:").replace("5000", "3068.64"), minutes = 120)
        assertEquals(emptyList<Long>(), savings.observeDeposits().first().map { it.amountMinor })
        assertEquals(0L, balance(awaeed.id))
        assertEquals(6_864L, balance(rajhiSavings))
        assertEquals(TransactionKind.OTHER, transactions.getAll().single { it.amountMinor == 6_864L }.kind)
    }

    @Test
    fun `an account its old messages take below zero must have started with that much`() = runTest {
        val days = 24 * 60L
        receive("AlRajhiBank", purchase, minutes = -10 * days)
        assertEquals(0L, balance(rajhiMain))
        assertEquals(6_200L, accounts.get(rajhiMain)!!.openingBalanceMinor)

        // Today's messages may still be out of order, so they don't move the floor.
        receive("AlRajhiBank", purchase.replace("Jahez", "Keeta"))
        assertEquals(-6_200L, balance(rajhiMain))
    }

    @Test
    fun `buying fund units doesn't change what the investment account is worth`() = runTest {
        val broker = db.institutionsDao().getAll().first { it.name == "Al Rajhi Capital" }.id
        val funds = accounts.create(AccountDraft(broker, "Funds", AccountType.INVESTMENT, "8888", "SAR", 0))
        val days = 24 * 60L

        // Only the purchase came by SMS: the cash for it must already have been there.
        receive("ALRajhiCPTL", "Subscription Order 6600001\n10.000000 Units\nFund A\nUnit Price 10.0000\nAmount 100.00", minutes = -10 * days)

        assertEquals(10_000L, accounts.get(funds)!!.openingBalanceMinor)
        assertEquals(10_000L, balance(funds))
    }

    @Test
    fun `money the bank says came from your broker account leaves it, even with no SMS from the broker`() = runTest {
        val broker = db.institutionsDao().getAll().first { it.name == "Al Rajhi Capital" }.id
        val funds = accounts.create(AccountDraft(broker, "Funds", AccountType.INVESTMENT, "8888", "SAR", 500_000))

        receive("AlRajhiBank", """
            تحويل من حساب الراجحي المالية
            الى:1111
            مبلغ:SR 2700
            26/6/4 16:11
        """)
        assertEquals(270_000L, balance(rajhiMain))
        assertEquals(230_000L, balance(funds))
        assertEquals(SmsIngest.IMPLIED_CONFIDENCE, transactions.getAllTransfers().single().matchConfidence, 0.0)

        // The broker's own SMS for it, a minute later, is the same event.
        receive("ALRajhiCPTL", "Local Transfer\nFrom:****8888\nAmount:2700.0\nTo:****\nRef:21000000", minutes = 1)
        assertEquals(230_000L, balance(funds))
    }

    @Test
    fun `an arrival from your own account at a bank that never reported it still leaves that account`() = runTest {
        val days = 24 * 60L
        receive("AlRajhiBank", """
            حوالة محلية واردة بـSR 9000
            لـ1111
            من4444;أحمد علي
            26/5/25 21:10
        """, minutes = -10 * days)

        assertEquals(900_000L, balance(rajhiMain))
        assertEquals(-900_000L, balance(snb) - accounts.get(snb)!!.openingBalanceMinor)
        assertEquals(SmsIngest.IMPLIED_CONFIDENCE, transactions.getAllTransfers().single().matchConfidence, 0.0)

        // Running again doesn't record it twice.
        ingest.processPending()
        assertEquals(2, transactions.getAll().size)
    }

    @Test
    fun `a one-time code for the amount that then arrives elsewhere shows where it left`() = runTest {
        val broker = db.institutionsDao().getAll().first { it.name == "Al Rajhi Capital" }.id
        val funds = accounts.create(AccountDraft(broker, "Funds", AccountType.INVESTMENT, "8888", "SAR", 0))
        val days = 24 * 60L

        receive("SNB-AlAhli", "لا تشارك رمز التفعيل 0000\n\u202C\u202Aتحويل لبنك محلي\nمبلغ \u202C\u202ASAR \u202C\u202A6300", minutes = -10 * days)
        receive("ALRajhiCPTL", "Local transfer\nAmount6300.0\nTo: ARC account\nFrom: أحمد علي", minutes = -10 * days + 1)

        assertEquals(630_000L, balance(funds))
        assertEquals(-630_000L, balance(snb) - accounts.get(snb)!!.openingBalanceMinor)
    }

    @Test
    fun `an SMS that names no account goes to its bank's busiest one`() = runTest {
        // Al Rajhi has two accounts here; the main one is in use.
        receive("AlRajhiBank", purchase)
        receive("AlRajhiBank", """
            سداد فاتورة
            مبلغ:SAR 100
            الخدمة:Electricity
            في:25-2-6 20:44
        """, minutes = 5)

        assertEquals(listOf(RawStatus.RECORDED, RawStatus.RECORDED), statuses())
        assertEquals(-16_200L, balance(rajhiMain))
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

    private val dollarsOnly = """
        شراء انترنت
        بطاقة:1111;مدى
        مبلغ:USD 7.99
        لدى:PLAYSTATIONNETWORK
        في:23-11-17 12:32
    """

    @Test
    fun `a charge given only in dollars is estimated at the peg and a card fee`() = runTest {
        receive("AlRajhiBank", dollarsOnly)

        assertEquals(listOf(RawStatus.RECORDED), statuses())
        // 7.99 × 3.75 × 1.025 = 30.7116…
        assertEquals(-3_071L, balance(rajhiMain))
        val charge = transactions.getAll().single()
        assertEquals(true, charge.estimated)
        assertEquals(799L, charge.originalAmountMinor)
        assertEquals("USD", charge.originalCurrency)
    }

    @Test
    fun `an estimate takes the rate a bank last quoted for that currency`() = runTest {
        // A charge whose SMS gave both: 23 USD cost 88.10 SAR (3.8304… a dollar).
        transactions.addParsed(
            Transaction(
                uid = "quoted", accountId = rajhiMain, direction = Direction.DEBIT, amountMinor = 8_810, currency = "SAR",
                occurredAt = t0, kind = TransactionKind.PURCHASE, title = "ANTHRO", source = TransactionSource.SMS,
                createdAt = t0, originalAmountMinor = 2_300, originalCurrency = "USD"
            )
        )
        receive("AlRajhiBank", dollarsOnly, minutes = 1)

        // 7.99 × 88.10 / 23 = 30.6052…
        assertEquals(3_061L, transactions.getAll().single { it.estimated }.amountMinor)
    }

    @Test
    fun `a charge in a currency with no rate waits, and saying what was charged ends the estimate`() = runTest {
        receive("AlRajhiBank", dollarsOnly.replace("USD", "EUR"))
        assertEquals(listOf(RawStatus.FOREIGN), statuses())
        assertEquals(0L, balance(rajhiMain))

        receive("AlRajhiBank", dollarsOnly, minutes = 1)
        val estimate = transactions.getAll().single()
        transactions.update(
            estimate.id,
            TransactionDraft(
                accountId = estimate.accountId, direction = estimate.direction, amountMinor = 3_112,
                occurredAt = estimate.occurredAt, kind = estimate.kind, title = estimate.title
            )
        )
        assertEquals(false, transactions.getAll().single().estimated)
    }
}
