package bassamalim.halala.core.models

import bassamalim.halala.core.enums.AmountTone
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.utils.DayLabel
import java.time.LocalDate

/**
 * One transaction as a list shows it, everything but translatable words already formatted.
 * A blank [title] reads as the kind's name; [isMove] rows read "Between your accounts"; a
 * [category] takes the kind's place in the second line, and [auto] marks one a rule filed.
 */
data class TransactionItem(
    val id: Long,
    val title: String,
    val kind: TransactionKind,
    val accountLabel: String,
    val isMove: Boolean,
    val amount: String,
    val currency: String,
    val tone: AmountTone,
    val initial: String,
    val date: LocalDate,
    val day: DayLabel,
    val category: String? = null,
    val auto: Boolean = false,
    /** Its merchant, for the logo. */
    val merchantId: Long? = null
)
