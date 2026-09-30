package bassamalim.halala.features.transaction

import bassamalim.halala.core.enums.AmountTone
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource

data class TransactionUiState(
    val isLoading: Boolean = true,
    /** Blank reads as the kind's name. */
    val title: String = "",
    val initial: String = "",
    val kind: TransactionKind = TransactionKind.OTHER,
    val tone: AmountTone = AmountTone.Spending,
    /** amount-xl, signed as a row signs it: "−62.00". */
    val amount: String = "",
    val currency: String = "",
    /** "Mon 29 Sep · 21:14" */
    val whenLabel: String = "",
    val accountLabel: String = "",
    /** For a move, the sending and receiving accounts; null otherwise. */
    val fromLabel: String? = null,
    val toLabel: String? = null,
    val note: String = "",
    val source: TransactionSource = TransactionSource.MANUAL,
    /** "30 Sep": the day it was written down. */
    val createdLabel: String = "",
    val isConfirmingDelete: Boolean = false
) {
    val isMove get() = fromLabel != null
}
