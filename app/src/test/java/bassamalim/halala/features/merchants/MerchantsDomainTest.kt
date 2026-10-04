package bassamalim.halala.features.merchants

import bassamalim.halala.core.data.dataSources.room.entities.Merchant
import bassamalim.halala.core.data.dataSources.room.entities.MerchantAlias
import bassamalim.halala.core.data.dataSources.room.relations.MerchantWithStats
import bassamalim.halala.core.domain.People
import bassamalim.halala.core.enums.AliasMatch
import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantsDomainTest {

    private fun merchant(id: Long, website: String?, transactions: Int, namedByYou: Boolean = false) =
        MerchantWithStats(Merchant(id = id, uid = "u$id", name = "m$id", website = website, namedByYou = namedByYou), 1, transactions, null)

    @Test
    fun `merchants with one website are offered as one, joining the one you named or the busier`() {
        val merchants = listOf(
            merchant(1, "jarir.com", transactions = 2),
            merchant(2, "jarir.com", transactions = 9),
            merchant(3, "jarir.com", transactions = 1, namedByYou = true),
            merchant(4, "panda.com.sa", transactions = 5),
            merchant(5, null, transactions = 5),
            merchant(6, null, transactions = 5)
        )

        val offers = MerchantsDomain.sameWebsite(merchants, dismissed = emptySet())
        assertEquals(listOf(3L to 2L, 3L to 1L), offers.map { it.keep.merchant.id to it.goes.merchant.id })

        // "Not the same" stays said.
        val left = MerchantsDomain.sameWebsite(merchants, dismissed = setOf(People.pairKey("u1", "u3")))
        assertEquals(listOf(3L to 2L), left.map { it.keep.merchant.id to it.goes.merchant.id })
    }

    @Test
    fun `a name cut short is offered once, first, even when the website agrees`() {
        val merchants = listOf(merchant(1, "jarir.com", transactions = 2), merchant(2, "jarir.com", transactions = 9))
        val aliases = listOf(alias(1, "jarir book"), alias(2, "jarir bookstore"))

        val offers = MerchantsDomain.suggest(merchants, aliases, dismissed = emptySet())
        assertEquals(listOf(Triple(2L, 1L, null)), offers.map { Triple(it.keep.merchant.id, it.goes.merchant.id, it.website) })
        assertEquals(emptyList<Any>(), MerchantsDomain.suggest(merchants, aliases, dismissed = setOf(People.pairKey("u1", "u2"))))
    }

    private fun alias(merchantId: Long, key: String) =
        MerchantAlias(merchantId = merchantId, aliasKey = key, descriptor = key, matchedBy = AliasMatch.FIRST)
}
