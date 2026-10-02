package bassamalim.halala.core.domain

import bassamalim.halala.core.enums.BusinessType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KnownMerchantsTest {

    private fun identify(vararg descriptors: String) = KnownMerchants.identify(descriptors.map(Merchants::key))

    @Test
    fun `a well-known merchant is found however its bank writes it`() {
        assertEquals(KnownMerchants.Known("Panda", BusinessType.SUPERMARKET), identify("PANDA 1042 RIYADH"))
        assertEquals("Panda", identify("Panda Retail Co")?.name)
        assertEquals("Othaim", identify("ALOTHAIM MARKETS")?.name)
        assertEquals("HungerStation", identify("HUNGERSTATION")?.name)
        assertEquals("Nahdi", identify("النهدي")?.name)
        assertEquals(BusinessType.FOOD_DELIVERY, identify("Jahez 77")?.type)
    }

    @Test
    fun `the longest spelling wins`() {
        assertEquals(BusinessType.MONEY_TRANSFER, identify("STC PAY")?.type)
        assertEquals(BusinessType.TELECOM, identify("STC 900")?.type)
    }

    @Test
    fun `a short name only matches a whole word`() {
        assertNull(identify("NOONDAY CAFE"))
        assertNull(identify("Applebees"))
        assertEquals("Noon", identify("noon")?.name)
    }

    @Test
    fun `a merchant not on the list is left for the AI`() {
        assertNull(identify("ALMTRF TRDG EST 0412"))
    }
}
