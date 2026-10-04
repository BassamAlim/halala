package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.Merchant
import bassamalim.halala.core.data.dataSources.room.entities.MerchantAlias
import bassamalim.halala.core.data.dataSources.room.entities.Rule
import bassamalim.halala.core.data.dataSources.room.entities.RuleActions
import bassamalim.halala.core.data.dataSources.room.entities.RuleConditions
import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.enums.AliasMatch
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.RuleSource
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class RulesTest {

    private val merchants = MerchantLookup(
        aliases = listOf(
            MerchantAlias(merchantId = 1, aliasKey = "panda", descriptor = "PANDA 1042", matchedBy = AliasMatch.FIRST),
            MerchantAlias(merchantId = 1, aliasKey = "hyper panda", descriptor = "HYPER PANDA", matchedBy = AliasMatch.FIRST),
            MerchantAlias(merchantId = 2, aliasKey = "jahez", descriptor = "JAHEZ", matchedBy = AliasMatch.FIRST)
        ),
        merchants = listOf(Merchant(id = 1, uid = "m1", name = "Panda Retail"), Merchant(id = 2, uid = "m2", name = "Jahez"))
    )

    private val rules = listOf(
        rule(1, RuleSource.LEARNED, RuleConditions(merchant = "Panda", merchantId = 1)),
        rule(2, RuleSource.MANUAL, RuleConditions(merchant = "Panda Retail", maxMinor = 5_000)),
        rule(3, RuleSource.MANUAL, RuleConditions(contains = "jahez")),
        rule(4, RuleSource.MANUAL, RuleConditions(merchant = "HYPER PANDA 99", accountId = 7)),
        rule(5, RuleSource.MANUAL, RuleConditions(merchant = "Unknown Shop")),
        rule(6, RuleSource.LEARNED, RuleConditions(merchantId = 2)),
        rule(7, RuleSource.MANUAL, RuleConditions(minMinor = 100_000)),
        rule(8, RuleSource.MANUAL, RuleConditions(merchant = "Off", minMinor = 1), enabled = false)
    )

    @Test
    fun `the indexed matcher picks what trying every rule in order picks`() {
        val ordered = rules.filter { it.enabled && it.conditions.size > 0 }
            .sortedWith(compareBy<Rule>({ it.source.ordinal }, { -it.conditions.size }, { -it.id }))
        val match = Rules.matcher(rules, merchants)

        val titles = listOf("PANDA 1042 RIYADH", "HYPER PANDA 3", "JAHEZ", "Unknown Shop 4", "OFF", "Somewhere", "")
        var compared = 0
        for (title in titles) for (amount in listOf(1L, 4_000L, 9_000L, 200_000L)) for (account in listOf(7L, 8L)) {
            val t = transaction(title, amount, account)
            assertEquals("$title $amount $account", ordered.firstOrNull { Rules.matches(it.conditions, t, merchants) }?.id, match(t)?.id)
            compared++
        }
        assertEquals(56, compared)
    }

    private fun rule(id: Long, source: RuleSource, conditions: RuleConditions, enabled: Boolean = true) =
        Rule(id, "r$id", conditions, RuleActions(categoryId = id), source, enabled, Instant.EPOCH)

    private fun transaction(title: String, amountMinor: Long, accountId: Long) = Transaction(
        uid = title, accountId = accountId, direction = Direction.DEBIT, amountMinor = amountMinor, currency = "SAR",
        occurredAt = Instant.EPOCH, kind = TransactionKind.entries.first { it.countsInTotals }, title = title,
        source = TransactionSource.entries.first(), createdAt = Instant.EPOCH
    )
}
