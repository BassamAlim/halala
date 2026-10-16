package bassamalim.halala.features.recurring

import bassamalim.halala.core.enums.CadenceUnit
import bassamalim.halala.core.enums.RecurringKind

data class RecurringUiState(
    val isLoading: Boolean = true,
    val currency: String = "",
    /** Summary style: "6,112", "73,344". */
    val monthly: String = "",
    val yearly: String = "",
    /** Your usual salary ("25,000") or, without one, what comes in a month on average; null when neither is known. */
    val income: String? = null,
    /** [income] is your salary, not the average of all that comes in. */
    val incomeFromSalary: Boolean = false,
    /** All of them together as a whole percent of [income]. */
    val incomePercent: Int? = null,
    /** What each one costs a month, the dearest first. */
    val shares: List<ShareRow> = emptyList(),
    val alerts: List<RecurringAlert> = emptyList(),
    /** Due within the next 30 days, then after. */
    val soon: List<SeriesRow> = emptyList(),
    val later: List<SeriesRow> = emptyList()
) {
    val isEmpty get() = soon.isEmpty() && later.isEmpty() && alerts.isEmpty()
}

/** What the board raises above the list. Amounts are summary style ("29", "35"). */
sealed interface RecurringAlert {
    val seriesId: Long

    /** [name] went up from [from] to [to] a [unit]. */
    data class PriceUp(
        override val seriesId: Long,
        val name: String,
        val from: String,
        val to: String,
        val toMinor: Long,
        val every: Int,
        val unit: CadenceUnit
    ) : RecurringAlert

    /** [name] was expected [expected] and hasn't arrived. */
    data class Missed(override val seriesId: Long, val name: String, val expected: String) : RecurringAlert

    /**
     * [name] charges [amount] on [due]: a yearly renewal ([firstCharge] false) or a free trial
     * ending. "Keep it" quiets it by [key].
     */
    data class Upcoming(
        override val seriesId: Long,
        val key: String,
        val name: String,
        val firstCharge: Boolean,
        val due: String,
        val amount: String,
        val currency: String
    ) : RecurringAlert

    /** Detection found [name], [amount] every [every] [unit]: add it? */
    data class Proposed(
        override val seriesId: Long,
        val name: String,
        val kind: RecurringKind,
        val amount: String,
        val currency: String,
        val every: Int,
        val unit: CadenceUnit
    ) : RecurringAlert
}

/**
 * What one running series costs a month, summary style ("1,500"), and its whole [percent] of
 * your income (0 for under one; null without an income).
 */
data class ShareRow(
    val id: Long,
    val merchantId: Long?,
    val name: String,
    val initial: String,
    val monthly: String,
    val percent: Int?,
    /** Some, but under a whole percent. */
    val underOne: Boolean
)

/**
 * One series as the board lists it: [due] ("3 Oct"), [endsOn] ("Aug 2027") for a contract, and
 * the amount it takes, as spending reads ("−56.00").
 */
data class SeriesRow(
    val id: Long,
    /** What it is paid to, for its logo. */
    val merchantId: Long?,
    val name: String,
    val initial: String,
    val kind: RecurringKind,
    val every: Int,
    val unit: CadenceUnit,
    val autoRenew: Boolean,
    val priceUp: Boolean,
    val due: String,
    val endsOn: String?,
    val reminderDays: Int?,
    val amount: String,
    val currency: String
)
