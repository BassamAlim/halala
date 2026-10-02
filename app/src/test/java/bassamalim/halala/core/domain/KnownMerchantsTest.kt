package bassamalim.halala.core.domain

import bassamalim.halala.core.enums.BusinessType
import bassamalim.halala.core.enums.ExpenseType
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

    @Test
    fun `the owner's own places are on the list`() {
        assertEquals(BusinessType.CHARITY, identify("EHSAN 01")?.type)
        assertEquals("Health Endowment Fund", identify("HEALTH INDO FUND")?.name)
        assertEquals(BusinessType.FOOD_DELIVERY, identify("KEETA RIYADH")?.type)
        assertEquals(BusinessType.INSURANCE, identify("Tameeni")?.type)
        assertEquals("Anthropic", identify("CLAUDE.AI SUBSCRIPTION")?.name)
        assertEquals(BusinessType.PARKING, identify("RIYADH PARKING 22")?.type)
        assertEquals("Amazon", identify("AMAZON SA")?.name)
    }

    @Test
    fun `the definitions file adds merchants, beats the list, and names categories`() {
        val defined = KnownMerchants.parse(
            """{"categories": [{"name": "Car Insurance", "expenseType": "FIXED_ESSENTIAL"}],
                "merchants": [
                  {"name": "Tameeni", "type": "INSURANCE", "category": "Car Insurance"},
                  {"name": "Corner Shop", "category": "Snacks", "spellings": ["CRNR SHP"]}]}"""
        )
        assertEquals(mapOf("Car Insurance" to ExpenseType.FIXED_ESSENTIAL, "Snacks" to null), defined.categories)
        assertEquals("Car Insurance", KnownMerchants.identify(listOf(Merchants.key("TAMEENI 4")), defined)?.category)
        assertEquals("Corner Shop", KnownMerchants.identify(listOf(Merchants.key("CRNR SHP 12")), defined)?.name)
        assertEquals("Panda", KnownMerchants.identify(listOf("panda"), defined)?.name)
    }

    @Test(expected = Exception::class)
    fun `a definitions file with a type that doesn't exist is refused whole`() {
        KnownMerchants.parse("""{"merchants": [{"name": "X", "type": "SPACESHIP"}]}""")
    }
}
