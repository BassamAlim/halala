package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.BalanceCheckpoint
import bassamalim.halala.core.data.dataSources.room.entities.RawMessage
import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.IdentifiedBy
import bassamalim.halala.core.enums.RawStatus
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class AnomaliesTest {

    private val now = Instant.parse("2026-10-14T12:00:00Z")
    private var nextId = 1L

    private fun tx(at: String, minor: Long, key: String = "panda", merchant: Long? = 1, direction: Direction = Direction.DEBIT, original: String? = null) =
        TransactionDetail(
            transaction = Transaction(
                id = nextId++, uid = "t$nextId", accountId = 1, direction = direction, amountMinor = minor, currency = "SAR",
                occurredAt = Instant.parse(at), kind = if (direction == Direction.DEBIT) TransactionKind.PURCHASE else TransactionKind.TRANSFER_IN,
                source = TransactionSource.SMS, createdAt = Instant.EPOCH, merchantKey = key,
                originalAmountMinor = original?.let { minor }, originalCurrency = original
            ),
            accountNickname = "", institutionName = null, counterpartId = null, counterpartAccountId = null,
            counterpartNickname = null, counterpartInstitutionName = null, isTransferInLeg = false, merchantId = merchant
        )

    private fun find(details: List<TransactionDetail>, checkpoints: List<BalanceCheckpoint> = emptyList(), messages: List<RawMessage> = emptyList(), dismissed: Set<String> = emptySet()) =
        Anomalies.find(details, checkpoints, messages, mapOf(1L to "SAR"), dismissed, now)

    @Test
    fun `the same charge twice within a day is a possible duplicate`() {
        val a = tx("2026-10-13T10:00:00Z", 21_450)
        val b = tx("2026-10-13T10:02:00Z", 21_450)
        val later = tx("2026-10-12T10:00:00Z", 21_450, key = "jahez", merchant = 2)
        val found = find(listOf(a, b, later))
        assertEquals(listOf("dup:${a.transaction.id}:${b.transaction.id}"), found.map { it.key })
        assertEquals(emptyList<Anomaly>(), find(listOf(a, b, later), dismissed = setOf(found.single().key)))
    }

    @Test
    fun `far above the merchant's usual is unusual, unless it is normal for them`() {
        val usual = (1..5).map { tx("2026-09-0${it}T10:00:00Z", 2_000) }
        val big = tx("2026-10-10T10:00:00Z", 50_000)
        assertEquals(listOf("large:${big.transaction.id}"), find(usual + big).map { it.key })
        assertEquals(emptyList<Anomaly>(), find(usual + big, dismissed = setOf(Anomalies.normalFor(1))))
    }

    @Test
    fun `foreign charges and declined cards are raised`() {
        val abroad = tx("2026-10-10T10:00:00Z", 5_000, original = "USD")
        val declined = RawMessage(id = 9, sender = "AlRajhiBank", body = "Declined", receivedAt = Instant.parse("2026-10-11T10:00:00Z"), hash = "h", status = RawStatus.DECLINED, parserVersion = 1)
        assertEquals(listOf("declined:h", "foreign:${abroad.transaction.id}"), find(listOf(abroad), messages = listOf(declined)).map { it.key })
        val known = tx("2026-10-10T11:00:00Z", 7_500, original = "USD").copy(merchantIdentifiedBy = IdentifiedBy.LIST)
        assertEquals(emptyList<Anomaly>(), find(listOf(known)))
    }

    @Test
    fun `a sender whose messages fail to parse above 5% this week is raised`() {
        fun sms(n: Int, status: RawStatus, day: String = "2026-10-12", sender: String = "AlRajhiBank") =
            RawMessage(id = n.toLong(), sender = sender, body = "", receivedAt = Instant.parse("${day}T10:0${n % 10}:00Z"), hash = "h$n", status = status, parserVersion = 1)
        val good = (1..19).map { sms(it, RawStatus.RECORDED) }
        val notices = (40..60).map { sms(it, RawStatus.IGNORED) }
        val lastMonth = (70..75).map { sms(it, RawStatus.UNRECOGNISED, day = "2026-09-20") }
        // 1 of 20 is exactly 5%: not yet.
        assertEquals(emptyList<Anomaly>(), find(emptyList(), messages = good + notices + lastMonth + sms(20, RawStatus.UNRECOGNISED)))
        val found = find(emptyList(), messages = good + sms(20, RawStatus.UNRECOGNISED) + sms(21, RawStatus.UNRECOGNISED, day = "2026-10-13"))
            .single() as Anomaly.ParserFailing
        assertEquals(listOf(2, 21, "h21"), listOf(found.failed, found.total, found.newest.hash))
        val otherBank = (80..99).map { sms(it, RawStatus.RECORDED, sender = "SNB") }
        assertEquals(listOf("AlRajhiBank"), find(emptyList(), messages = good + otherBank + sms(20, RawStatus.UNRECOGNISED) + sms(21, RawStatus.UNRECOGNISED)).map { (it as Anomaly.ParserFailing).sender })
    }

    @Test
    fun `a reported balance that the ledger doesn't reach is a mismatch`() {
        val spend = tx("2026-10-10T10:00:00Z", 1_000, key = "", merchant = null)
        val points = listOf(
            BalanceCheckpoint(1, 1, 100_000, Instant.parse("2026-10-09T10:00:00Z"), null),
            BalanceCheckpoint(2, 1, 99_000, Instant.parse("2026-10-10T10:00:00Z"), null),
            BalanceCheckpoint(3, 1, 90_000, Instant.parse("2026-10-11T10:00:00Z"), null)
        )
        val found = find(listOf(spend), checkpoints = points).single() as Anomaly.Mismatch
        assertEquals(99_000, found.expectedMinor)
        assertEquals(90_000, found.reportedMinor)
    }
}
