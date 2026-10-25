package bassamalim.halala.features.goal

import bassamalim.halala.core.models.TransactionItem
import bassamalim.halala.features.plan.GoalCard

data class GoalUiState(
    val isLoading: Boolean = true,
    /** The goal was deleted (from its form): the screen leaves. */
    val isGone: Boolean = false,
    val name: String = "",
    /** Where it stands, as Plan's card shows it. */
    val card: GoalCard? = null,
    /** What went into or out of it, newest first. */
    val transactions: List<TransactionItem> = emptyList()
)
