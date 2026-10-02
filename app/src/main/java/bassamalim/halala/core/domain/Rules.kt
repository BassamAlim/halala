package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.Rule
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.enums.AmountTone
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

object Rules {

    /**
     * What makes two descriptors the same merchant: lower case, letters only, so branch numbers
     * and terminal ids drop out ("PANDA 1042 RIYADH" and "Panda-1077 Riyadh" agree).
     */
    // ponytail: city suffixes and near-spellings still split a merchant; fuzzy matching and the
    // Merchant table's aliases come with AI merchant resolution.
    fun merchantKey(title: String): String {
        val lower = title.lowercase()
        val letters = lower.replace(NOT_LETTERS, " ").trim().replace(SPACES, " ")
        return letters.ifEmpty { lower.trim() }
    }

    /**
     * The rule that wins for each merchant key, among the enabled ones: yours beat learned ones,
     * learned ones beat AI.
     */
    fun index(rules: List<Rule>): Map<String, Rule> = rules
        .filter { it.enabled && it.conditions.merchant != null }
        // Later entries replace earlier ones, so the strongest source goes last.
        .sortedByDescending { it.source.ordinal }
        .associateBy { merchantKey(it.conditions.merchant.orEmpty()) }

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

    private val NOT_LETTERS = Regex("[^\\p{L}]+")
    private val SPACES = Regex("\\s+")
}
