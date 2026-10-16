package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.definitions.DefinitionsFile
import androidx.test.core.app.ApplicationProvider
import bassamalim.halala.core.data.TEST_CLOCK
import bassamalim.halala.core.data.dataSources.room.AppDatabase
import bassamalim.halala.core.data.testDatabase
import bassamalim.halala.core.data.dataSources.room.entities.RecurringSeries
import bassamalim.halala.core.enums.CadenceUnit
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.RecurringKind
import bassamalim.halala.core.enums.SeriesStatus
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.models.TransactionDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class RecurringRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var transactions: TransactionsRepository
    private lateinit var classification: ClassificationRepository
    private lateinit var recurring: RecurringRepository
    private var cash = 0L

    @Before
    fun setUp() = runTest {
        db = testDatabase()
        transactions = TransactionsRepository(db.transactionsDao(), db.accountsDao(), TEST_CLOCK)
        classification = ClassificationRepository(db.classificationDao(), db.merchantsDao(), db.transactionsDao(), db.peopleDao(), DefinitionsFile(ApplicationProvider.getApplicationContext()), TEST_CLOCK)
        recurring = RecurringRepository(db.recurringDao(), db.transactionsDao(), TEST_CLOCK)
        cash = db.accountsDao().getCashWallet()!!.id
    }

    @After
    fun tearDown() = db.close()

    private suspend fun pay(title: String, day: String, minor: Long = 5_600, kind: TransactionKind = TransactionKind.PURCHASE) =
        transactions.add(
            TransactionDraft(
                cash, Direction.DEBIT, minor,
                LocalDate.parse(day).atTime(12, 0).atZone(ZoneId.of("Asia/Riyadh")).toInstant(), kind, title
            )
        )

    @Test
    fun `detection proposes once, and a dismissed one stays dismissed`() = runTest {
        for (day in listOf("2026-07-03", "2026-08-03", "2026-09-03")) pay("NETFLIX.COM", day)
        for (day in listOf("2026-07-27", "2026-08-27", "2026-09-27")) pay("MOTHER", day, 200_000, TransactionKind.TRANSFER_OUT)
        pay("Panda", "2026-09-10")
        classification.applyRules()

        assertEquals(2, recurring.detect())
        assertEquals(0, recurring.detect())
        val all = recurring.getAll()
        assertEquals(setOf(RecurringKind.SUBSCRIPTION, RecurringKind.PLANNED), all.map { it.kind }.toSet())

        val netflix = all.single { it.kind == RecurringKind.SUBSCRIPTION }
        recurring.setStatus(netflix.id, SeriesStatus.ACTIVE)
        val state = recurring.observeStates().first().single { it.series.id == netflix.id }
        assertEquals(LocalDate.parse("2026-10-03"), state.nextDue)

        recurring.setStatus(netflix.id, SeriesStatus.DISMISSED)
        assertEquals(0, recurring.detect())
        assertEquals(1, recurring.observeStates().first().size)
    }

    @Test
    fun `a merged merchant's series follows it`() = runTest {
        for (day in listOf("2026-07-03", "2026-08-03", "2026-09-03")) pay("NETFLIX.COM", day)
        pay("NFLX Digital", "2026-09-05")
        classification.applyRules()
        recurring.detect()
        val merchants = classification.getMerchants()
        val netflix = merchants.single { it.name.startsWith("NETFLIX", ignoreCase = true) }
        val other = merchants.single { it.id != netflix.id }

        classification.mergeMerchants(netflix.id, other.id)
        assertEquals(other.id, recurring.getAll().single().merchantId)
    }

    @Test
    fun `a transfer linked by hand pays its series, and only that one`() = runTest {
        val rent = recurring.save(
            RecurringSeries(
                uid = "", kind = RecurringKind.BILL, name = "Rent", amountMinor = 400_000, currency = "SAR",
                unit = CadenceUnit.MONTH, anchor = LocalDate.parse("2026-09-01"), status = SeriesStatus.ACTIVE, createdAt = Instant.EPOCH
            )
        )
        val transfer = pay("TRANSFER 2093", "2026-09-28", 400_000, TransactionKind.TRANSFER_OUT)

        recurring.link(transfer, rent)
        assertEquals(mapOf(transfer to rent), recurring.observePayments().first())
        val state = recurring.observeStates().first().single { it.series.id == rent }
        assertEquals(LocalDate.parse("2026-11-01"), state.nextDue)
        assertEquals(setOf(transfer), state.chargeIds)

        recurring.unlink(transfer)
        assertEquals(emptyMap<Long, Long>(), recurring.observePayments().first())
        assertEquals(emptySet<Long>(), recurring.observeStates().first().single { it.series.id == rent }.chargeIds)
    }

    @Test
    fun `charges linked to another series aren't proposed again`() = runTest {
        val rent = recurring.save(
            RecurringSeries(
                uid = "", kind = RecurringKind.BILL, name = "Rent", amountMinor = 5_600, currency = "SAR",
                unit = CadenceUnit.MONTH, anchor = LocalDate.parse("2026-07-01"), status = SeriesStatus.ACTIVE, createdAt = Instant.EPOCH
            )
        )
        for (day in listOf("2026-07-03", "2026-08-03", "2026-09-03")) recurring.link(pay("NETFLIX.COM", day), rent)
        classification.applyRules()

        assertEquals(0, recurring.detect())
    }
}
