package bassamalim.halala.features.activity

import bassamalim.halala.core.models.TransactionItem
import bassamalim.halala.core.utils.DayLabel

data class ActivityUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    /** Null is "All accounts". */
    val selectedAccountId: Long? = null,
    val accountFilters: List<AccountFilter> = emptyList(),
    /** This month's money in and out, SAR, summary style: "+18,000", "−11,760". */
    val monthIn: String = "",
    val monthOut: String = "",
    val groups: List<DayGroup> = emptyList(),
    /** Whether anything at all has been recorded, to tell "nothing yet" from "no match". */
    val hasAny: Boolean = false
)

data class AccountFilter(val id: Long, val label: String)

/** One day's rows under its label. */
data class DayGroup(val day: DayLabel, val items: List<TransactionItem>)
