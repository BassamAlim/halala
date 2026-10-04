package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ForeignRatesTest {

    private val at = Instant.parse("2026-10-01T12:00:00Z")

    private fun quoted(sar: Long, usd: Long, on: String, estimated: Boolean = false) = Transaction(
        uid = on, accountId = 1, direction = Direction.DEBIT, amountMinor = sar, currency = "SAR",
        occurredAt = Instant.parse(on), kind = TransactionKind.PURCHASE, source = TransactionSource.SMS,
        createdAt = Instant.EPOCH, originalAmountMinor = usd, originalCurrency = "USD", estimated = estimated
    )

    @Test
    fun `the nearest charge whose SMS gave both amounts sets the rate`() {
        val far = quoted(sar = 3_800, usd = 1_000, on = "2026-01-01T00:00:00Z") // 3.80
        val near = quoted(sar = 8_810, usd = 2_300, on = "2026-09-30T23:40:00Z") // 3.8304…
        val conversion = ForeignRates.estimate(799, "USD", "SAR", at, listOf(far, near))!!
        assertEquals(3_061L, conversion.amountMinor) // 7.99 × 3.8304… = 30.6052…
        assertEquals("3.8304", ForeignRates.label(conversion.rate))
    }

    @Test
    fun `an estimate never sets the rate for another`() {
        val guess = quoted(sar = 5_000, usd = 1_000, on = "2026-09-30T23:40:00Z", estimated = true)
        assertEquals(3_071L, ForeignRates.estimate(799, "USD", "SAR", at, listOf(guess))!!.amountMinor)
    }

    @Test
    fun `pegged currencies convert at the peg and a card fee, across decimals`() {
        // 10 AED × 3.75 / 3.6725 × 1.025 = 10.4663…
        assertEquals(1_047L, ForeignRates.estimate(1_000, "AED", "SAR", at, emptyList())!!.amountMinor)
        // 1.000 BHD (three places) × 3.75 / 0.376 × 1.025 = 10.2227…
        assertEquals(1_022L, ForeignRates.estimate(1_000, "BHD", "SAR", at, emptyList())!!.amountMinor)
        // A dollar account charged in riyals: 37.50 SAR / 3.75 × 1.025 = 10.25 USD.
        assertEquals(1_025L, ForeignRates.estimate(3_750, "SAR", "USD", at, emptyList())!!.amountMinor)
    }

    @Test
    fun `a floating currency with no quoted charge can't be estimated`() {
        assertNull(ForeignRates.estimate(1_000, "EUR", "SAR", at, emptyList()))
        assertNull(ForeignRates.estimate(1_000, "JPY", "SAR", at, listOf(quoted(3_750, 1_000, "2026-09-01T00:00:00Z"))))
    }
}
