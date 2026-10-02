package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.Tag
import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.data.dataSources.room.entities.TransactionTag
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class TagsTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 2)
    private var next = 1L

    private fun spend(day: String, original: String? = "TRY") = TransactionDetail(
        transaction = Transaction(
            id = next++, uid = "t$next", accountId = 1, direction = Direction.DEBIT, amountMinor = 10_000, currency = "SAR",
            occurredAt = Instant.parse("${day}T10:00:00Z"), kind = TransactionKind.PURCHASE, source = TransactionSource.SMS,
            createdAt = Instant.EPOCH, originalAmountMinor = original?.let { 80_000 }, originalCurrency = original
        ),
        accountNickname = "", institutionName = null, counterpartId = null, counterpartAccountId = null,
        counterpartNickname = null, counterpartInstitutionName = null, isTransferInLeg = false
    )

    private val trip = listOf(
        spend("2026-09-12"), spend("2026-09-13"), spend("2026-09-15"), spend("2026-09-19"),
        // Home again; then two purchases online in lira a month later: not a trip.
        spend("2026-09-22", original = null), spend("2026-10-25"), spend("2026-10-26")
    )

    @Test
    fun `a run of purchases in another currency suggests a trip there`() {
        val suggestions = Tags.suggest(trip, emptyList(), emptyMap(), emptySet(), today, zone)
        assertEquals(1, suggestions.size)
        val s = suggestions.single()
        assertEquals("Türkiye", s.country)
        assertEquals(LocalDate.of(2026, 9, 12), s.from)
        assertEquals(LocalDate.of(2026, 9, 19), s.to)
        assertEquals(4, s.count)
        assertFalse(s.ongoing)
    }

    @Test
    fun `a no, a tag on those days, or tagged purchases end the suggestion`() {
        val key = Tags.suggest(trip, emptyList(), emptyMap(), emptySet(), today, zone).single().key
        assertTrue(Tags.suggest(trip, emptyList(), emptyMap(), setOf(key), today, zone).isEmpty())

        val tag = Tag(1, "x", "Istanbul", LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14), createdAt = Instant.EPOCH)
        assertTrue(Tags.suggest(trip, listOf(tag), emptyMap(), emptySet(), today, zone).isEmpty())

        val tagged = Tags.byTransaction(listOf(TransactionTag(1, 9), TransactionTag(2, 9), TransactionTag(3, 9, removed = true)))
        assertTrue(Tags.suggest(trip, emptyList(), tagged, emptySet(), today, zone).isEmpty())
    }

    @Test
    fun `an automatic tag covers its days, to today while it runs`() {
        val open = Tag(1, "x", "Trip", LocalDate.of(2026, 9, 30), null, auto = true, createdAt = Instant.EPOCH)
        assertTrue(Tags.covers(open, today, today))
        assertFalse(Tags.covers(open, LocalDate.of(2026, 9, 29), today))
        assertFalse(Tags.covers(open.copy(auto = false), today, today))
    }

    @Test
    fun `a currency of several countries names none`() {
        assertNull(Tags.countryOf("EUR"))
        assertEquals("Japan", Tags.countryOf("JPY"))
    }
}
