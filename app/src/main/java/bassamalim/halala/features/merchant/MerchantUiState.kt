package bassamalim.halala.features.merchant

import bassamalim.halala.core.enums.AliasMatch
import bassamalim.halala.core.models.TransactionItem

data class MerchantUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val initial: String = "",
    /** Spent at it, as a summary shows money: "18,400". */
    val spent: String = "",
    val currency: String = "",
    val count: Int = 0,
    /** The year of the oldest one: "2024"; empty with none. */
    val since: String = "",
    val spellings: List<SpellingRow> = emptyList(),
    /** A merchant known by one spelling has none to take out. */
    val canSplit: Boolean = false,
    val transactions: List<TransactionItem> = emptyList(),
    /** For the merge sheet: the merchants matching its search. */
    val mergeOptions: List<MerchantOption> = emptyList(),
    val sheet: MerchantSheet? = null
)

/** One way the bank writes it. */
data class SpellingRow(val id: Long, val descriptor: String, val matchedBy: AliasMatch, val count: Int)

data class MerchantOption(val id: Long, val name: String, val count: Int)

sealed interface MerchantSheet {
    data class Rename(val name: String, val problem: NameProblem? = null) : MerchantSheet

    /** "Not this one": taking [spelling] out, to confirm. */
    data class Split(val spelling: SpellingRow) : MerchantSheet

    /** Choosing the merchant this one is. */
    data class Merge(val query: String = "") : MerchantSheet

    data class ConfirmMerge(val into: MerchantOption) : MerchantSheet
}
