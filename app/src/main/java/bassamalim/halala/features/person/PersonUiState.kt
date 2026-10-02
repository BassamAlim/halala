package bassamalim.halala.features.person

import bassamalim.halala.core.enums.AmountTone
import bassamalim.halala.core.models.TransactionItem

data class PersonUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val initial: String = "",
    val currency: String = "",
    /** All transfers with them, summary style: "6,500", "5,500", "−1,000". */
    val sent: String = "",
    val received: String = "",
    val net: String = "",
    val netTone: AmountTone = AmountTone.Spending,
    val count: Int = 0,
    val spellings: List<SpellingRow> = emptyList(),
    /** Someone known by one spelling has none to take out. */
    val canSplit: Boolean = false,
    val transactions: List<TransactionItem> = emptyList(),
    /** For the merge sheet: the people matching its search. */
    val mergeOptions: List<PersonOption> = emptyList(),
    val sheet: PersonSheet? = null
)

/** One way the bank writes their name. */
data class SpellingRow(val id: Long, val descriptor: String, val count: Int)

data class PersonOption(val id: Long, val name: String, val count: Int)

sealed interface PersonSheet {
    data class Rename(val name: String, val problem: NameProblem? = null) : PersonSheet

    /** "Not this person": taking [spelling] out, to confirm. */
    data class Split(val spelling: SpellingRow) : PersonSheet

    /** Choosing who this is. */
    data class Merge(val query: String = "") : PersonSheet

    data class ConfirmMerge(val into: PersonOption) : PersonSheet
}
