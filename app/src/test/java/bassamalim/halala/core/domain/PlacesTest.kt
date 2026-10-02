package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.data.dataSources.room.entities.TransactionPlace
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class PlacesTest {

    private val now = Instant.parse("2026-10-02T12:00:00Z")

    private fun tx(id: Long, occurred: String, created: String = "2026-10-02T12:00:00Z", kind: TransactionKind = TransactionKind.PURCHASE, minor: Long = 1_000) = Transaction(
        id = id, uid = "t$id", accountId = 1, direction = Direction.DEBIT, amountMinor = minor, currency = "SAR",
        occurredAt = Instant.parse(occurred), kind = kind, source = TransactionSource.SMS, createdAt = Instant.parse(created)
    )

    @Test
    fun `only purchases that just happened, recorded in this run, take a place`() {
        val since = Instant.parse("2026-10-02T11:59:00Z")
        val all = listOf(
            tx(1, "2026-10-02T11:58:00Z"),
            tx(2, "2026-10-02T10:00:00Z"),                       // an SMS hours late
            tx(3, "2026-10-02T11:58:00Z", kind = TransactionKind.TRANSFER_OUT),
            tx(4, "2026-10-02T11:58:00Z", created = "2026-10-02T11:00:00Z"), // an earlier run
            tx(5, "2026-10-02T11:58:00Z")                        // already placed
        )
        assertEquals(listOf(1L), Places.needingPlace(all, setOf(5), since, now).map { it.id })
    }

    @Test
    fun `places gather nearby purchases under their usual merchant`() {
        fun detail(t: Transaction, merchant: String) = TransactionDetail(
            t, "", null, null, null, null, null, false, merchantName = merchant
        )
        val details = listOf(
            detail(tx(1, "2026-10-01T10:00:00Z", minor = 2_000), "Starbucks"),
            detail(tx(2, "2026-10-01T11:00:00Z", minor = 1_000), "Starbucks"),
            detail(tx(3, "2026-10-01T12:00:00Z", minor = 500), "Kiosk"),
            detail(tx(4, "2026-10-01T13:00:00Z", minor = 9_000), "Panda")
        )
        val places = listOf(
            TransactionPlace.of(1, 24.71361, 46.67531, 20f),
            TransactionPlace.of(2, 24.71370, 46.67540, 20f),
            TransactionPlace.of(3, 24.71365, 46.67535, 20f),
            TransactionPlace.of(4, 24.80000, 46.60000, 20f)
        )
        val spots = Places.spots(details, places, "SAR") { true }
        val top = Places.top(spots)
        assertEquals(listOf("Panda" to 9_000L, "Starbucks" to 3_500L), top.map { it.name to it.spentMinor })
        assertEquals(3, top[1].count)
    }
}
