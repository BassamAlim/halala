package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.Category
import bassamalim.halala.core.data.dataSources.room.entities.Merchant
import bassamalim.halala.core.enums.BusinessType
import bassamalim.halala.core.enums.IdentifiedBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentificationTest {

    private val groceries = Category(1, "g", "Groceries", businessTypes = listOf(BusinessType.SUPERMARKET))
    private val shopping = Category(2, "s", "Shopping", businessTypes = listOf(BusinessType.ELECTRONICS))
    private val categories = listOf(groceries, shopping)

    private fun merchant(type: BusinessType?, by: IdentifiedBy?, confidence: Int? = null) =
        Merchant(1, "m", "Shop", businessType = type, identifiedBy = by, confidence = confidence)

    @Test
    fun `a business type files under the category that takes it`() {
        assertEquals(groceries, Identification.categoryFor(BusinessType.SUPERMARKET, categories))
        assertEquals(shopping, Identification.categoryFor(BusinessType.ELECTRONICS, categories))
        assertNull(Identification.categoryFor(BusinessType.DEPARTMENT_STORE, categories))
        assertNull(Identification.categoryFor(BusinessType.UNKNOWN, categories))
        assertNull(Identification.categoryFor(null, categories))
    }

    @Test
    fun `the list and you are trusted, the AI as far as it was sure`() {
        assertEquals(Tier.AUTO, Identification.tierOf(merchant(BusinessType.SUPERMARKET, IdentifiedBy.LIST), categories))
        assertEquals(Tier.AUTO, Identification.tierOf(merchant(BusinessType.SUPERMARKET, IdentifiedBy.YOU), categories))
        assertEquals(Tier.AUTO, Identification.tierOf(merchant(BusinessType.SUPERMARKET, IdentifiedBy.AI, 90), categories))
        assertEquals(Tier.SUGGEST, Identification.tierOf(merchant(BusinessType.SUPERMARKET, IdentifiedBy.AI, 89), categories))
        assertEquals(Tier.SUGGEST, Identification.tierOf(merchant(BusinessType.SUPERMARKET, IdentifiedBy.AI, 60), categories))
        assertEquals(Tier.ASK, Identification.tierOf(merchant(BusinessType.SUPERMARKET, IdentifiedBy.AI, 59), categories))
    }

    @Test
    fun `what no category takes, what is unknown, and what was never asked, need you`() {
        assertEquals(Tier.ASK, Identification.tierOf(merchant(BusinessType.DEPARTMENT_STORE, IdentifiedBy.LIST), categories))
        assertEquals(Tier.ASK, Identification.tierOf(merchant(BusinessType.UNKNOWN, IdentifiedBy.AI, 99), categories))
        assertEquals(Tier.ASK, Identification.tierOf(merchant(null, null), categories))
        assertEquals(Tier.ASK, Identification.tierOf(merchant(null, IdentifiedBy.WITHHELD), categories))
    }

    @Test
    fun `a shop's name is sent, digits and all`() {
        assertTrue(Identification.sendable("PANDA 1042 RIYADH", listOf("5521")))
        assertTrue(Identification.sendable("ALMTRF TRDG EST 0412", emptyList()))
        // A word and a number together are not an IBAN.
        assertTrue(Identification.sendable("CARREFOUR 12 HYPERMARKET RIYADH", emptyList()))
        assertTrue(Identification.sendable("POS 123456789", emptyList()))
    }

    @Test
    fun `a name that may hold an account, card, phone number or IBAN is kept back`() {
        assertFalse(Identification.sendable("TRANSFER 5521", listOf("5521")))
        assertFalse(Identification.sendable("PAY 448755210000", listOf("5521")))
        assertFalse(Identification.sendable("ref ٥٥٢١", listOf("5521")))
        assertFalse(Identification.sendable("CALL 0551234567", emptyList()))
        assertFalse(Identification.sendable("SA03 8000 0000 6080 1016 7519", emptyList()))
    }
}
