package bassamalim.halala.core.prices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class PriceProtocolTest {

    @Test
    fun `gold in dollars an ounce becomes riyals a gram, exactly`() {
        val body = """{"currency":"USD","currencySymbol":"$","exchangeRate":1.0,"name":"Gold","price":4133.799805,"symbol":"XAU","updatedAt":"2026-10-02T16:13:57Z"}"""
        val gold = PriceProtocol.gold(body)
        // 4133.799805 × 3.75 ÷ 31.1034768 = 498.3928…
        assertEquals(BigDecimal("498.3928"), gold.perGram)
        assertEquals(LocalDate.of(2026, 10, 2), gold.date)
    }

    @Test
    fun `the fund list is read, and rows without a price are left out`() {
        val body = """{"rows":[
            {"fundId":6019,"name":"Al Rajhi Inclusion Fund ","owner":"Al Rajhi Capital","price":11.9441,"date":"16 September 2026"},
            {"fundId":1,"name":"Closed","owner":null,"price":null,"date":null},
            {"fundId":2,"name":"Odd date","owner":null,"price":1.50,"date":"yesterday"}
        ],"numberOfPages":1}"""
        val funds = PriceProtocol.funds(body)
        assertEquals(listOf(6019L, 2L), funds.map { it.id })
        assertEquals(ListedFund(6019, "Al Rajhi Inclusion Fund", "Al Rajhi Capital", BigDecimal("11.9441"), LocalDate.of(2026, 9, 16)), funds[0])
        assertEquals(BigDecimal("1.5"), funds[1].price)
        assertNull(funds[1].date)
    }

    @Test
    fun `sources name gold or a fund`() {
        assertEquals(6019L, PriceProtocol.fundIdOf(PriceProtocol.fundSource(6019)))
        assertNull(PriceProtocol.fundIdOf(PriceProtocol.GOLD_SOURCE))
        assertNull(PriceProtocol.fundIdOf(null))
    }
}
