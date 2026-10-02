package bassamalim.halala.features.review

import bassamalim.halala.core.models.CategoryOption
import bassamalim.halala.core.utils.DayLabel

data class ReviewUiState(
    val isLoading: Boolean = true,
    val cards: List<ReviewCard> = emptyList(),
    val categories: List<CategoryOption> = emptyList(),
    /** The card whose category is being chosen. */
    val picking: ReviewCard? = null,
    /** The answer just given, while its undo is offered. */
    val justFiled: JustFiled? = null
)

data class JustFiled(val batchId: Long, val merchant: String, val category: String)

/** One merchant's uncategorised spending: a single transaction, or many filed with one answer. */
data class ReviewCard(
    /** Merchant key and currency: stable while the list changes under it. */
    val key: String,
    /** The merchant as its newest transaction writes it. */
    val title: String,
    val initial: String,
    val count: Int,
    /** The total, signed as spending: "−18,400.00". */
    val amount: String,
    val currency: String,
    /** The year of the oldest one: "2024". */
    val since: String,
    /** For a single transaction: its day, account and id. */
    val day: DayLabel,
    val accountLabel: String,
    val transactionId: Long
)
