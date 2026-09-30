package bassamalim.halala.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    // Parsing what people type

    @Test
    fun `whole and decimal amounts become halalas`() {
        assertEquals(6200L, Money.parse("62", "SAR"))
        assertEquals(6250L, Money.parse("62.5", "SAR"))
        assertEquals(6205L, Money.parse("62.05", "SAR"))
        assertEquals(75L, Money.parse(".75", "SAR"))
        assertEquals(0L, Money.parse("0", "SAR"))
    }

    @Test
    fun `thousands separators are accepted only in groups of three`() {
        assertEquals(123_450L, Money.parse("1,234.50", "SAR"))
        assertEquals(100_000_000L, Money.parse("1,000,000", "SAR"))
        assertNull(Money.parse("1,23", "SAR"))
        assertNull(Money.parse("12,34.5", "SAR"))
    }

    @Test
    fun `more decimals than the currency has is not an amount`() {
        assertNull(Money.parse("62.505", "SAR"))
        assertNull(Money.parse("1.5", "JPY"))
        assertEquals(1_500L, Money.parse("1.5", "KWD"))
        assertEquals(1_234L, Money.parse("1.234", "KWD"))
        assertEquals(1_500L, Money.parse("1,500", "JPY"))
    }

    @Test
    fun `garbage and signs are rejected by the unsigned parse`() {
        listOf("", " ", ".", "1.2.3", "abc", "12a", "-5", "1e3", "٫").forEach {
            assertNull("\"$it\" should not parse", Money.parse(it, "SAR"))
        }
    }

    @Test
    fun `an Arabic keyboard's digits and separators are read`() {
        assertEquals(6250L, Money.parse("٦٢٫٥", "SAR"))
        assertEquals(123_400L, Money.parse("١٬٢٣٤", "SAR"))
        assertEquals(6200L, Money.parse("۶۲", "SAR"))
    }

    @Test
    fun `amounts too large for a Long are rejected, not wrapped`() {
        assertNull(Money.parse("92233720368547758.08", "SAR"))
        assertEquals(Long.MAX_VALUE, Money.parse("92233720368547758.07", "SAR"))
    }

    @Test
    fun `signed parsing takes either minus`() {
        assertEquals(-50_000L, Money.parseSigned("-500", "SAR"))
        assertEquals(-50_000L, Money.parseSigned("−500", "SAR"))
        assertEquals(50_000L, Money.parseSigned("500", "SAR"))
        assertNull(Money.parseSigned("--500", "SAR"))
    }

    // Showing it

    @Test
    fun `lists and detail show two decimals and a comma every three digits`() {
        assertEquals("214.50", Money.format(21_450, "SAR"))
        assertEquals("18,000.00", Money.format(1_800_000, "SAR"))
        assertEquals("1,234,567.89", Money.format(123_456_789, "SAR"))
        assertEquals("0.05", Money.format(5, "SAR"))
        assertEquals("0.00", Money.format(0, "SAR"))
    }

    @Test
    fun `negatives use the true minus sign, never a hyphen`() {
        val text = Money.format(-21_450, "SAR")
        assertEquals("−214.50", text)
        assertFalse(text.contains('-'))
    }

    @Test
    fun `income can carry a plus, and zero never does`() {
        assertEquals("+18,000.00", Money.format(1_800_000, "SAR", showPlus = true))
        assertEquals("0.00", Money.format(0, "SAR", showPlus = true))
        assertEquals("−5.00", Money.format(-500, "SAR", showPlus = true))
    }

    @Test
    fun `summaries drop the decimals, rounding half away from zero`() {
        assertEquals("6,240", Money.format(624_049, "SAR", decimals = false))
        assertEquals("6,241", Money.format(624_050, "SAR", decimals = false))
        assertEquals("−6,241", Money.format(-624_050, "SAR", decimals = false))
        assertEquals("0", Money.format(49, "SAR", decimals = false))
    }

    @Test
    fun `each currency shows its own number of decimals`() {
        assertEquals("1,500", Money.format(1_500, "JPY"))
        assertEquals("1.500", Money.format(1_500, "KWD"))
        assertEquals("15.00", Money.format(1_500, "USD"))
    }

    @Test
    fun `the extremes of a Long still format`() {
        assertEquals("92,233,720,368,547,758.07", Money.format(Long.MAX_VALUE, "SAR"))
        assertEquals("−92,233,720,368,547,758.08", Money.format(Long.MIN_VALUE, "SAR"))
    }

    @Test
    fun `plain is for files - hyphen, no grouping, every decimal`() {
        assertEquals("-1234.50", Money.plain(-123_450, "SAR"))
        assertEquals("0.00", Money.plain(0, "SAR"))
        assertEquals("1500", Money.plain(1_500, "JPY"))
    }

    @Test
    fun `parse and format round-trip every value exactly`() {
        listOf(0L, 1L, 99L, 100L, 21_450L, 123_456_789L, Long.MAX_VALUE).forEach { minor ->
            assertEquals(minor, Money.parse(Money.plain(minor, "SAR"), "SAR"))
            assertEquals(minor, Money.parse(Money.format(minor, "SAR"), "SAR"))
        }
    }

    @Test
    fun `sums are exact where a double would drift`() {
        // 0.1 + 0.2 != 0.3 in binary floating point; in halalas it is exact.
        val sum = Money.sum(listOf(Money.parse("0.1", "SAR")!!, Money.parse("0.2", "SAR")!!))
        assertEquals(Money.parse("0.3", "SAR"), sum)
    }

    // Arithmetic

    @Test(expected = ArithmeticException::class)
    fun `a sum that would overflow fails loudly`() {
        Money.sum(listOf(Long.MAX_VALUE, 1))
    }

    @Test
    fun `sums of nothing are zero, and negatives net off`() {
        assertEquals(0L, Money.sum(emptyList()))
        assertEquals(-100L, Money.sum(listOf(500L, -600L)))
    }

    // Currencies

    @Test
    fun `currency codes are real ISO codes`() {
        assertTrue(Money.isCurrency("SAR"))
        assertTrue(Money.isCurrency("usd"))
        assertFalse(Money.isCurrency("ABC"))
        assertFalse(Money.isCurrency("SA"))
        assertFalse(Money.isCurrency(""))
    }

    @Test
    fun `an unknown currency falls back to two decimals`() {
        assertEquals(2, Money.fractionDigits("XYZ"))
        assertEquals(2, Money.fractionDigits("SAR"))
        assertEquals(0, Money.fractionDigits("JPY"))
        assertEquals(3, Money.fractionDigits("KWD"))
    }
}
