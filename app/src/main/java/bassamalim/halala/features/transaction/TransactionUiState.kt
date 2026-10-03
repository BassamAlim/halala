package bassamalim.halala.features.transaction

import bassamalim.halala.core.enums.AmountTone
import bassamalim.halala.core.enums.ExpenseType
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import bassamalim.halala.core.models.CategoryOption
import bassamalim.halala.core.models.RuleWords
import bassamalim.halala.core.enums.BusinessType
import bassamalim.halala.core.enums.IdentifiedBy
import bassamalim.halala.core.domain.SplitProblem
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
    /** The exact amount, for splitting it. */
    val amountMinor: Long = 0,
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
    /** Spending you paid can be split with others; once split, how. */
    val canSplit: Boolean = false,
    /** Spending you paid that isn't a transfer can be marked as paid for someone, all of it owed to you. */
    val canPayFor: Boolean = false,
    val split: SplitInfo? = null,
    /** The savings goal it went toward (or came out of), once marked. */
    val goal: GoalLink? = null,
    /** It can be marked toward a goal: money to or from a broker, say, or a move. */
    val canMarkGoal: Boolean = false,
    val goals: List<PersonChoice> = emptyList(),
    /** Everyone to split with, the latest first. */
    val people: List<PersonChoice> = emptyList(),
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

/**
 * A bill you split: each person's share ("100.00") and what is left as yours; [whole] when one
 * person's share is all of it (you paid it for them).
 */
data class SplitInfo(val shares: List<Pair<String, String>>, val yours: String, val currency: String, val whole: Boolean = false)

data class PersonChoice(val id: Long, val name: String)

/** Toward [name] (or, [withdrawn], taken out of it). */
data class GoalLink(val name: String, val withdrawn: Boolean)

/** A transfer to or from someone, and loans. */
sealed interface LoanLink {
    /**
     * A plain transfer: it can be marked as lending ([lent]) or borrowing, to or from [person]
     * ([personId], null when it names nobody) or someone else.
     * [suggestion] is the open loan it would pay back, to ask about.
     */
    data class Open(val lent: Boolean, val person: String, val personId: Long?, val suggestion: Suggestion?) : LoanLink

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
     * Marking it as a loan with [personId] (the person it names to begin with, or anyone else:
     * someone can ask you to pay for something for them), or, [forPurchase], marking spending as
     * paid for someone; [newName] someone to add. Due [dueOn] ("15 Oct", or none); [picking] the
     * day, from [pickFrom] until one is chosen. [noOne] when it was confirmed with nobody chosen.
     */
    data class MarkLoan(
        val pickFrom: LocalDate,
        val forPurchase: Boolean = false,
        val personId: Long? = null,
        val newName: String = "",
        val noOne: Boolean = false,
        val dueOn: LocalDate? = null,
        val dueLabel: String? = null,
        val picking: Boolean = false
    ) : TransactionSheet

    /** "Not part of a loan", to confirm. */
    data object Unlink : TransactionSheet

    /**
     * Marking it toward a savings goal: [goalId] chosen, put in or [withdrawn] (guessed from where
     * the money went); [noGoal] when confirmed with none chosen.
     */
    data class Goal(val goalId: Long?, val withdrawn: Boolean, val noGoal: Boolean = false) : TransactionSheet

    /** "Not toward a goal", to confirm. */
    data object Ungoal : TransactionSheet

    /**
     * Splitting it with [selected] people, equally or [byAmount] (each person's [amounts] as
     * typed); [preview] is each share and yours as they would be, formatted; [newName] someone
     * to add.
     */
    data class Split(
        val selected: List<Long> = emptyList(),
        val byAmount: Boolean = false,
        val amounts: Map<Long, String> = emptyMap(),
        val newName: String = "",
        val problems: Set<SplitProblem> = emptySet(),
        val preview: Map<Long, String> = emptyMap(),
        val yours: String = ""
    ) : TransactionSheet

    /** Undoing the split, or, [whole], that it was paid for someone. */
    data class Unsplit(val whole: Boolean) : TransactionSheet

    data object Category : TransactionSheet
    data object Type : TransactionSheet

    /**
     * After choosing [category] for a named merchant: this one only, or always? [others] is how
     * many of its other transactions "always" would file.
     */
    data class Always(val category: CategoryOption, val others: Int) : TransactionSheet
}
