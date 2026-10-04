package bassamalim.halala.features.merchant

import bassamalim.halala.core.ai.LookupFound
import bassamalim.halala.core.ai.LookupOutcome
import bassamalim.halala.core.enums.AliasMatch
import bassamalim.halala.core.enums.BusinessType
import bassamalim.halala.core.enums.IdentifiedBy
import bassamalim.halala.core.models.TransactionItem

data class MerchantUiState(
    val isLoading: Boolean = true,
    val id: Long = 0,
    val name: String = "",
    val initial: String = "",
    /** Spent at it, as a summary shows money: "18,400". */
    val spent: String = "",
    val currency: String = "",
    val count: Int = 0,
    /** The year of the oldest one: "2024"; empty with none. */
    val since: String = "",
    /** What the business is, who said so and how sure; none until it is identified. */
    val businessType: BusinessType? = null,
    val identifiedBy: IdentifiedBy? = null,
    val confidence: Int? = null,
    /** The page a web search found it on, while the AI's answer (or one you took) stands. */
    val webTitle: String? = null,
    val webUrl: String? = null,
    /**
     * It can be looked up (the AI, and online when a search can be had) while nothing surer than
     * the AI has said what it is; [lookup] is that, under way or how it went.
     */
    val canLookUp: Boolean = false,
    val lookup: Lookup? = null,
    /** The category its spending files under, by what it is: none when no category takes it. */
    val filesUnder: String? = null,
    /** The website its logo is the icon of; none when it has no logo to fetch. */
    val website: String? = null,
    val spellings: List<SpellingRow> = emptyList(),
    /** A merchant known by one spelling has none to take out. */
    val canSplit: Boolean = false,
    val transactions: List<TransactionItem> = emptyList(),
    /** For the merge sheet: the merchants matching its search. */
    val mergeOptions: List<MerchantOption> = emptyList(),
    val sheet: MerchantSheet? = null
)

/** Looking it up: [working] while it goes, then its [outcome] (what it found opens a sheet). */
data class Lookup(val working: Boolean, val outcome: LookupOutcome? = null)

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

    /** Saying what the business is. */
    data object BusinessType : MerchantSheet

    /** Correcting its logo: the website it comes from, [working] while it is fetched. */
    data class Logo(val website: String, val working: Boolean = false, val problem: LogoProblem? = null) : MerchantSheet

    /** What looking it up found, to take or leave. */
    data class Found(val found: LookupFound) : MerchantSheet
}
