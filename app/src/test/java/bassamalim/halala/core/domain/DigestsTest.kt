package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class DigestsTest {

    private fun tx(day: String, minor: Long, category: Long? = 1, direction: Direction = Direction.DEBIT, kind: TransactionKind = TransactionKind.PURCHASE) =
        TransactionDetail(
            transaction = Transaction(
                uid = day + minor, accountId = 1, direction = direction, amountMinor = minor, currency = "SAR",
                occurredAt = Instant.parse("${day}T10:00:00Z"), kind = kind, source = TransactionSource.SMS,
                createdAt = Instant.EPOCH, categoryId = category
            ),
            accountNickname = "", institutionName = null, counterpartId = null, counterpartAccountId = null,
            counterpartNickname = null, counterpartInstitutionName = null, isTransferInLeg = false
        )

    @Test
    fun `periods are weeks from Sunday, calendar months and years`() {
        val wednesday = LocalDate.parse("2026-10-14")
        assertEquals(LocalDate.parse("2026-10-11"), Digests.periodOf(DigestKind.WEEK, wednesday).start)
        assertEquals(LocalDate.parse("2026-10-01"), Digests.periodOf(DigestKind.MONTH, wednesday).start)
        assertEquals(
            listOf("2026-09-01", "2026-08-01"),
            Digests.finished(DigestKind.MONTH, wednesday, 2).map { it.start.toString() }
        )
    }

    @Test
    fun `a month against the one before, where it went, and what moved`() {
        val details = listOf(
            tx("2026-06-05", 20_000, 2), tx("2026-07-05", 20_000, 2), tx("2026-08-05", 20_000, 2),
            tx("2026-08-10", 100_000), tx("2026-09-10", 90_000),
            tx("2026-09-12", 60_000, 2), tx("2026-09-13", 5_000, null),
            tx("2026-09-27", 1_800_000, null, Direction.CREDIT, TransactionKind.SALARY)
        )
        val digest = Digests.build(
            Digests.periodOf(DigestKind.MONTH, LocalDate.parse("2026-09-01")),
            details, mapOf(1L to "Groceries", 2L to "Delivery"), "SAR", ZoneOffset.UTC, 0, emptyList(), emptyList()
        )
        assertEquals(155_000, digest.spentMinor)
        assertEquals(120_000, digest.previousSpentMinor)
        assertEquals(29, digest.changePercent)
        assertEquals(1_645_000, digest.savedMinor)
        assertEquals(listOf("Groceries" to 90_000L, "Delivery" to 60_000L, null to 5_000L), digest.categories)
        // Groceries moved more in money (from an average of 333.33), so it comes first.
        assertEquals(
            listOf(Observation.CategoryMoved("Groceries", 170), Observation.CategoryMoved("Delivery", 200)),
            digest.observations
        )
    }
}
