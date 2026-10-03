package bassamalim.halala.core.domain

import bassamalim.halala.core.Globals
import bassamalim.halala.core.data.dataSources.room.entities.Merchant
import bassamalim.halala.core.data.dataSources.room.entities.MerchantAlias
import bassamalim.halala.core.data.dataSources.room.entities.Rule
import bassamalim.halala.core.data.dataSources.room.entities.RuleConditions
import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.data.dataSources.room.relations.AccountWithBalance
import bassamalim.halala.core.data.dataSources.room.relations.RuleWithStats
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.enums.AmountTone
import bassamalim.halala.core.models.RuleWords
import bassamalim.halala.core.utils.accountLabel
import java.time.Instant

/** One merchant's uncategorised spending, to be filed with one answer. */
data class MerchantCluster(
    /** The merchant's id, or for a name that isn't a merchant (a person), its key. */
    val key: String,
    val merchantId: Long?,
    /** The merchant's name, or the newest title for one that isn't a merchant. */
    val name: String,
    val currency: String,
    /** The newest of them: its title names the cluster. */
    val latest: TransactionDetail,
    val count: Int,
    val totalMinor: Long,
    val since: Instant
)

/** A rule, what it has done, and its conditions ready to be put into words. */
data class DescribedRule(val stats: RuleWithStats, val words: RuleWords)

/**
 * Which merchant a key is: through the merchant's aliases, or its own name, so a rule written
 * as "Merchant is Panda Retail" finds Panda whatever its bank calls it.
 */
class MerchantLookup(aliases: List<MerchantAlias> = emptyList(), merchants: List<Merchant> = emptyList()) {
    private val byKey: Map<String, Long> =
        merchants.associate { Merchants.key(it.name) to it.id } + aliases.associate { it.aliasKey to it.merchantId }

    /** The merchant [key] names; null when it names none. */
    fun of(key: String): Long? = byKey[key]
}

object Rules {

    /**
     * Whether every condition that is set holds for [transaction], whose title reads as [key]
     * (`Merchants.key`). A merchant condition holds for every alias of the merchant: the one
     * the rule was taught ([RuleConditions.merchantId]), or the one its words name.
     */
    fun matches(
        conditions: RuleConditions,
        transaction: Transaction,
        merchants: MerchantLookup = MerchantLookup(),
        key: String = Merchants.key(transaction.title)
    ): Boolean =
        sameMerchant(conditions, key, merchants) &&
                (conditions.contains == null || transaction.title.contains(conditions.contains.trim(), ignoreCase = true)) &&
                (conditions.accountId == null || conditions.accountId == transaction.accountId) &&
                (conditions.minMinor == null || transaction.amountMinor >= conditions.minMinor) &&
                (conditions.maxMinor == null || transaction.amountMinor <= conditions.maxMinor)

    private fun sameMerchant(conditions: RuleConditions, key: String, merchants: MerchantLookup): Boolean {
        if (conditions.merchantId != null) return merchants.of(key) == conditions.merchantId
        val merchant = conditions.merchant ?: return true
        val ruleKey = Merchants.key(merchant)
        if (ruleKey == key) return true
        val ruleMerchant = merchants.of(ruleKey) ?: return false
        return ruleMerchant == merchants.of(key)
    }

    /**
     * The rule that files a transaction, among the enabled ones that match it: yours beat
     * learned ones, learned ones beat AI; then the more specific rule, then the newer one. A
     * rule with no conditions matches nothing rather than everything.
     */
    fun matcher(rules: List<Rule>, merchants: MerchantLookup = MerchantLookup()): (Transaction) -> Rule? {
        val ordered = rules
            .filter { it.enabled && it.conditions.size > 0 }
            .sortedWith(compareBy<Rule>({ it.source.ordinal }, { -it.conditions.size }, { -it.id }))

        // Most rules name a merchant, and hold only for a transaction of that merchant: each
        // transaction tries those of its own merchant and the rules naming none, in order,
        // instead of every rule (one learned rule per merchant makes hundreds).
        val anyMerchant = mutableListOf<Int>()
        val byKey = mutableMapOf<String, MutableList<Int>>()
        val byMerchant = mutableMapOf<Long, MutableList<Int>>()
        ordered.forEachIndexed { i, rule ->
            val c = rule.conditions
            when {
                c.merchantId != null -> byMerchant.getOrPut(c.merchantId) { mutableListOf() } += i
                c.merchant != null -> {
                    val ruleKey = Merchants.key(c.merchant)
                    byKey.getOrPut(ruleKey) { mutableListOf() } += i
                    merchants.of(ruleKey)?.let { byMerchant.getOrPut(it) { mutableListOf() } += i }
                }
                else -> anyMerchant += i
            }
        }
        return { transaction ->
            val key = Merchants.key(transaction.title)
            val candidates = (anyMerchant + byKey[key].orEmpty() +
                    merchants.of(key)?.let { byMerchant[it] }.orEmpty()).toSortedSet()
            candidates.firstNotNullOfOrNull { i -> ordered[i].takeIf { matches(it.conditions, transaction, merchants, key) } }
        }
    }

    /** Spending is what has a category: money out that counts in totals. */
    fun canCategorise(detail: TransactionDetail): Boolean =
        toneOf(detail) == AmountTone.Spending && detail.transaction.kind.countsInTotals

    /**
     * The review inbox: uncategorised spending grouped by merchant (every spelling of it
     * together), the most money first, so a few answers file the most. Spending with no name
     * can't be grouped or learned from, and is left for its own detail screen. Only merchants:
     * a transfer's title names a person (People), never a business to file, so transfers to
     * people are filed, if at all, on their own detail screen.
     */
    fun clusters(details: List<TransactionDetail>): List<MerchantCluster> = details
        .filter {
            canCategorise(it) && it.transaction.categoryId == null && it.transaction.title.isNotBlank() &&
                    it.transaction.kind !in People.KINDS && it.personId == null
        }
        .groupBy { (it.merchantId?.let { id -> "m$id" } ?: Merchants.key(it.transaction.title)) to it.transaction.currency }
        .map { (key, group) ->
            val latest = group.maxBy { it.transaction.occurredAt }
            MerchantCluster(
                key = key.first,
                merchantId = latest.merchantId,
                name = latest.merchantName ?: latest.transaction.title,
                currency = key.second,
                latest = latest,
                count = group.size,
                totalMinor = Money.sum(group.map { it.transaction.amountMinor }),
                since = group.minOf { it.transaction.occurredAt }
            )
        }
        .sortedByDescending { it.totalMinor }

    /** Rules with their conditions formatted: the account by its name, amounts as money. */
    fun describe(rules: List<RuleWithStats>, accounts: List<AccountWithBalance>): List<DescribedRule> {
        val accountsById = accounts.associateBy { it.account.id }

        return rules.map { stats ->
            val conditions = stats.rule.conditions
            val account = conditions.accountId?.let(accountsById::get)
            // An amount condition is in its account's currency, or SAR when it names none.
            val currency = account?.account?.currency ?: Globals.PRIMARY_CURRENCY

            DescribedRule(
                stats = stats,
                words = RuleWords(
                    // The merchant as it is called now, when the rule was taught one.
                    merchant = stats.merchantName ?: conditions.merchant,
                    contains = conditions.contains,
                    account = account?.let { accountLabel(it.institutionName, it.account.nickname) },
                    min = conditions.minMinor?.let { Money.format(it, currency) },
                    max = conditions.maxMinor?.let { Money.format(it, currency) }
                )
            )
        }
    }
}
