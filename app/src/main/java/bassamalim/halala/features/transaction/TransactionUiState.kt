package bassamalim.halala.features.transaction

import bassamalim.halala.core.enums.AmountTone
import bassamalim.halala.core.enums.ExpenseType
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import bassamalim.halala.core.models.CategoryOption
import bassamalim.halala.core.models.RuleWords

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
    val isConfirmingDelete: Boolean = false,
    /** Spending has a category and a type; income, moves and corrections don't. */
    val canCategorise: Boolean = false,
    val category: CategoryOption? = null,
    val expenseType: ExpenseType? = null,
    val categories: List<CategoryOption> = emptyList(),
    /** The rule that filed it, when one did. */
    val filedBy: FiledBy? = null,
    /** What a rule would match: the title as stored (blank for a nameless one or a move). */
    val merchant: String = "",
    val sheet: TransactionSheet? = null
) {
    val isMove get() = fromLabel != null
}

/** The rule behind an automatic filing, as the "Filed automatically" card words it. */
data class FiledBy(val words: RuleWords, val category: String, val expenseType: ExpenseType?, val hits: Int)

sealed interface TransactionSheet {
    data object Category : TransactionSheet
    data object Type : TransactionSheet

    /**
     * After choosing [category] for a named merchant: this one only, or always? [others] is how
     * many of its other transactions "always" would file.
     */
    data class Always(val category: CategoryOption, val others: Int) : TransactionSheet
}
