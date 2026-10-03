package bassamalim.halala.features.categorySpending

import bassamalim.halala.core.models.TransactionItem
import bassamalim.halala.features.insights.MerchantBar

data class CategorySpendingUiState(
    val isLoading: Boolean = true,
    /** The category's name; null for spending not yet filed. */
    val name: String? = null,
    /** "September", or "September 2025" in another year. */
    val monthName: String = "",
    /** What it came to, summary style. */
    val total: String = "",
    val currency: String = "",
    val count: Int = 0,
    /** Where it went, the most first. */
    val merchants: List<MerchantBar> = emptyList(),
    val transactions: List<TransactionItem> = emptyList()
)
