package bassamalim.halala.features.review

import bassamalim.halala.core.enums.BusinessType
import bassamalim.halala.core.enums.ExpenseType
import bassamalim.halala.core.enums.IdentifiedBy
import bassamalim.halala.core.models.CategoryOption
import bassamalim.halala.core.utils.DayLabel

data class ReviewUiState(
    val isLoading: Boolean = true,
    /** The cards the filter shows. */
    val cards: List<ReviewCard> = emptyList(),
    val filter: ReviewFilter = ReviewFilter.All,
    /** How many there are in all, with a suggestion, and with none: the filter chips' counts. */
    val total: Int = 0,
    val suggested: Int = 0,
    val needsYou: Int = 0,
    val categories: List<CategoryOption> = emptyList(),
    /** The card whose category is being chosen. */
    val picking: ReviewCard? = null,
    /** The answer just given, while its undo is offered. */
    val justFiled: JustFiled? = null
)

/** The Review board's filter row. */
enum class ReviewFilter { All, Suggested, NeedsYou }

data class JustFiled(val batchId: Long, val merchant: String, val category: String)

/** One merchant's uncategorised spending: a single transaction, or many filed with one answer. */
data class ReviewCard(
    /** Merchant key and currency: stable while the list changes under it. */
    val key: String,
    /** The merchant's name, or the newest title for one that isn't a merchant (a person). */
    val title: String,
    /** How the newest one was written: what a rule learns from. */
    val descriptor: String,
    /** The merchant, when it is one: a card for many opens it. */
    val merchantId: Long?,
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
    val transactionId: Long,
    /** What the merchant was identified as, and by whom: the evidence. None when unknown. */
    val businessType: BusinessType? = null,
    val identifiedBy: IdentifiedBy? = null,
    /** The AI's confidence (0–100), shown as "92% sure"; only for what the AI said. */
    val confidence: Int? = null,
    /** The page a web search found it on ("Found online: …"), to judge the match. */
    val webTitle: String? = null,
    val webUrl: String? = null,
    /** The category chosen for you, one tap from confirmed; none when it needs you. */
    val suggestion: Suggestion? = null
)

data class Suggestion(val category: CategoryOption, val expenseType: ExpenseType?)
