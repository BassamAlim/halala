package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.ZakatProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoField

class ZakatTest {

    private val today = LocalDate.parse("2026-10-14")
    private val worth = NetWorthNow(
        mapOf(
            WealthClass.ACCOUNTS to 4_830_000L, WealthClass.SAVINGS to 12_000_000L, WealthClass.FUNDS to 9_615_000L,
            WealthClass.GOLD to 4_680_000L, WealthClass.OWED_TO_YOU to 120_000L, WealthClass.YOU_OWE to -20_000L
        ),
        31_225_000L
    )

    @Test
    fun `two and a half percent of what counts, less debts, over the nisab`() {
        val state = Zakat.stateOf(ZakatProfile(hijriMonth = 9, hijriDay = 1), worth, BigDecimal("563"), 2, today)
        // 85 g × 563 = 47,855.00
        assertEquals(4_785_500L, state.nisabMinor)
        assertEquals(31_225_000L, state.zakatableMinor)
        assertEquals(780_625L, state.dueMinor)
    }

    @Test
    fun `what you leave out doesn't count, and under the nisab nothing is due`() {
        val state = Zakat.stateOf(
            ZakatProfile(includeSavings = false, includeFunds = false, includeGold = false, includeOwed = false, otherDebtsMinor = 10_000),
            worth, BigDecimal("563"), 2, today
        )
        assertEquals(4_800_000L, state.zakatableMinor)
        assertEquals(120_000L, state.dueMinor)
        val poor = Zakat.stateOf(ZakatProfile(includeSavings = false, includeFunds = false, includeGold = false), worth, BigDecimal("600"), 2, today)
        assertEquals(0L, poor.dueMinor)
    }

    @Test
    fun `the zakat day is the next one in the Umm al-Qura year`() {
        val next = Zakat.nextOn(9, 1, today)
        assertEquals(9, next.get(ChronoField.MONTH_OF_YEAR))
        assertEquals(1, next.get(ChronoField.DAY_OF_MONTH))
        val day = LocalDate.from(next)
        assertTrue(!day.isBefore(today) && day.isBefore(today.plusDays(356)))
        val state = Zakat.stateOf(ZakatProfile(hijriMonth = 9, hijriDay = 1, paidHijriYear = next.get(ChronoField.YEAR)), worth, null, 2, today)
        assertTrue(state.paid)
        assertTrue(state.hawlDays in 354L..356L)
        assertFalse(Zakat.stateOf(ZakatProfile(), worth, null, 2, today).paid)
    }
}
