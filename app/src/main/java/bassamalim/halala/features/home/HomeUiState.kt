package bassamalim.halala.features.home

import bassamalim.halala.core.domain.BudgetState
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
    val comingUp: List<ComingUp> = emptyList(),
    /** The balance card, once there is a budget for everything. */
    val balance: BalanceInfo? = null
)

/**
 * The Home board's balance card: [spent] this cycle against [limit] (summary style), how full,
 * in which state, [over] by how much once past it, and days left.
 */
data class BalanceInfo(
    val spent: String,
    val limit: String,
    val over: String?,
    val progress: Float,
    val state: BudgetState,
    val daysLeft: Int,
    /** The forecast end-of-cycle balance, once there is one. */
    val endAbout: String? = null
)

/** One payment due soon: "Netflix", "3 Oct", "56". */
data class ComingUp(val name: String, val due: String, val amount: String)
