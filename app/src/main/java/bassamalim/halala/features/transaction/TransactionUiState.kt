package bassamalim.halala.features.transaction

import bassamalim.halala.core.enums.AmountTone
import bassamalim.halala.core.enums.ExpenseType
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import bassamalim.halala.core.models.CategoryOption
import bassamalim.halala.core.models.RuleWords
import bassamalim.halala.core.enums.BusinessType
import bassamalim.halala.core.enums.IdentifiedBy
import java.time.LocalDate

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
    /** The merchant it was at, when its title names one: the row that opens it. */
    val merchantId: Long? = null,
    val merchantName: String? = null,
    /** The person a transfer's title names: the row that opens them. */
    val personId: Long? = null,
    val personName: String? = null,
    /** How it stands with a loan: one it could open or repay, or the one it is part of. */
    val loan: LoanLink? = null,
    val sheet: TransactionSheet? = null
) {
    val isMove get() = fromLabel != null
}

/** The rule behind an automatic filing, as the "Filed automatically" card words it. */
data class FiledBy(
    val words: RuleWords,
    val category: String,
    val expenseType: ExpenseType?,
    val hits: Int,
    /** For an automatic rule: what the merchant was identified as, by whom, and how sure. */
    val identifiedAs: BusinessType? = null,
    val identifiedBy: IdentifiedBy? = null,
    val confidence: Int? = null
)

/** A transfer to or from someone, and loans. */
sealed interface LoanLink {
    /**
     * A plain transfer: it can be marked as lending ([lent]) or borrowing, to or from [person].
     * [suggestion] is the open loan it would pay back, to ask about.
     */
    data class Open(val lent: Boolean, val person: String, val suggestion: Suggestion?) : LoanLink

    /** Part of a loan: [repays] it or lent it; [remaining] is what is still owed ("1,000.00"). */
    data class Part(
        val lent: Boolean,
        val repays: Boolean,
        val person: String,
        val personId: Long,
        val remaining: String,
        val currency: String,
        val settled: Boolean
    ) : LoanLink
}

/** An open loan a transfer would repay: [remaining] still owed, lent on [lentOn] ("12 Sep"). */
data class Suggestion(val loanId: Long, val lent: Boolean, val remaining: String, val currency: String, val lentOn: String)

sealed interface TransactionSheet {
    /**
     * Marking it as a loan, due [dueOn] ("15 Oct", or none); [picking] the day, from [pickFrom]
     * until one is chosen.
     */
    data class MarkLoan(
        val pickFrom: LocalDate,
        val dueOn: LocalDate? = null,
        val dueLabel: String? = null,
        val picking: Boolean = false
    ) : TransactionSheet

    /** "Not part of a loan", to confirm. */
    data object Unlink : TransactionSheet

    data object Category : TransactionSheet
    data object Type : TransactionSheet

    /**
     * After choosing [category] for a named merchant: this one only, or always? [others] is how
     * many of its other transactions "always" would file.
     */
    data class Always(val category: CategoryOption, val others: Int) : TransactionSheet
}
