package bassamalim.halala.features.home

import bassamalim.halala.core.models.TransactionItem

data class HomeUiState(
    val isLoading: Boolean = true,
    /** Null only if the wallet was somehow never seeded. */
    val cashWalletId: Long? = null,
    /** Summary figures: no decimals, as the design writes them ("1,240"). */
    val cashBalance: String = "",
    val bankBalance: String = "",
    val bankAccountCount: Int = 0,
    /** Cards waiting in the review inbox. */
    val reviewCount: Int = 0,
    val recent: List<TransactionItem> = emptyList(),
    /** Open loans each way, summary style; shown once there is any loan. */
    val hasLoans: Boolean = false,
    val owedToYou: String = "",
    val youOwe: String = "",
    /** What is due within a month, the soonest first. */
    val comingUp: List<ComingUp> = emptyList()
)

/** One payment due soon: "Netflix", "3 Oct", "56". */
data class ComingUp(val name: String, val due: String, val amount: String)
