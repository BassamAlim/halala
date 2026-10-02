package bassamalim.halala.core.domain

import bassamalim.halala.core.Globals
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
    val key: String,
    val currency: String,
    /** The newest of them: its title names the cluster. */
    val latest: TransactionDetail,
    val count: Int,
    val totalMinor: Long,
    val since: Instant
)

/** A rule, what it has done, and its conditions ready to be put into words. */
data class DescribedRule(val stats: RuleWithStats, val words: RuleWords)

object Rules {

    /**
     * What makes two descriptors the same merchant: lower case, letters only, so branch numbers
     * and terminal ids drop out ("PANDA 1042 RIYADH" and "Panda-1077 Riyadh" agree).
     */
    // ponytail: city suffixes and near-spellings still split a merchant ("contains" rules cover
    // it by hand); fuzzy matching and the Merchant table's aliases come with AI merchant resolution.
    fun merchantKey(title: String): String {
        val lower = title.lowercase()
        val letters = lower.replace(NOT_LETTERS, " ").trim().replace(SPACES, " ")
        return letters.ifEmpty { lower.trim() }
    }

    /** Whether every condition that is set holds for [transaction]. */
    fun matches(conditions: RuleConditions, transaction: Transaction): Boolean =
        (conditions.merchant == null || merchantKey(conditions.merchant) == merchantKey(transaction.title)) &&
                (conditions.contains == null || transaction.title.contains(conditions.contains.trim(), ignoreCase = true)) &&
                (conditions.accountId == null || conditions.accountId == transaction.accountId) &&
                (conditions.minMinor == null || transaction.amountMinor >= conditions.minMinor) &&
                (conditions.maxMinor == null || transaction.amountMinor <= conditions.maxMinor)

    /**
     * The rule that files a transaction, among the enabled ones that match it: yours beat
     * learned ones, learned ones beat AI; then the more specific rule, then the newer one. A
     * rule with no conditions matches nothing rather than everything.
     */
    fun matcher(rules: List<Rule>): (Transaction) -> Rule? {
        val ordered = rules
            .filter { it.enabled && it.conditions.size > 0 }
            .sortedWith(compareBy<Rule>({ it.source.ordinal }, { -it.conditions.size }, { -it.id }))
        return { transaction -> ordered.firstOrNull { matches(it.conditions, transaction) } }
    }

    /** Spending is what has a category: money out that counts in totals. */
    fun canCategorise(detail: TransactionDetail): Boolean =
        toneOf(detail) == AmountTone.Spending && detail.transaction.kind.countsInTotals

    /**
     * The review inbox: uncategorised spending grouped by merchant, the most money first, so a
     * few answers file the most. Spending with no name can't be grouped or learned from, and is
     * left for its own detail screen.
     */
    fun clusters(details: List<TransactionDetail>): List<MerchantCluster> = details
        .filter { canCategorise(it) && it.transaction.categoryId == null && it.transaction.title.isNotBlank() }
        .groupBy { merchantKey(it.transaction.title) to it.transaction.currency }
        .map { (key, group) ->
            MerchantCluster(
                key = key.first,
                currency = key.second,
                latest = group.maxBy { it.transaction.occurredAt },
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
                    merchant = conditions.merchant,
                    contains = conditions.contains,
                    account = account?.let { accountLabel(it.institutionName, it.account.nickname) },
                    min = conditions.minMinor?.let { Money.format(it, currency) },
                    max = conditions.maxMinor?.let { Money.format(it, currency) }
                )
            )
        }
    }

    private val NOT_LETTERS = Regex("[^\\p{L}]+")
    private val SPACES = Regex("\\s+")
}
