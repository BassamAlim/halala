package bassamalim.halala.features.person

import bassamalim.halala.core.enums.AmountTone
import bassamalim.halala.core.enums.LoanEventType
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.models.TransactionItem
import java.time.LocalDate

data class PersonUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val initial: String = "",
    val currency: String = "",
    /** All transfers with them, summary style: "6,500", "5,500", "−1,000". */
    val sent: String = "",
    val received: String = "",
    val net: String = "",
    val netTone: AmountTone = AmountTone.Spending,
    val count: Int = 0,
    /** When they began paying your salary ("1 Oct"); null when they don't. */
    val salarySince: String? = null,
    val spellings: List<SpellingRow> = emptyList(),
    /** Someone known by one spelling has none to take out. */
    val canSplit: Boolean = false,
    val transactions: List<TransactionItem> = emptyList(),
    /** Their loans: open ones first (as the board's loan card), then settled. */
    val loans: List<PersonLoan> = emptyList(),
    /** For the merge sheet: the people matching its search. */
    val mergeOptions: List<PersonOption> = emptyList(),
    val sheet: PersonSheet? = null
)

/** One way the bank writes their name. */
data class SpellingRow(val id: Long, val descriptor: String, val count: Int)

data class PersonOption(val id: Long, val name: String, val count: Int)

/**
 * One loan with them. [lent]: they owe you. Amounts are formatted: [remaining] with decimals
 * ("1,000.00"), [lentTotal] and [repaid] as a summary writes them ("1,500", "500"). [progress]
 * is how much of it is paid back, 0 to 1.
 */
data class PersonLoan(
    val loanId: Long,
    val lent: Boolean,
    val open: Boolean,
    val remaining: String,
    val currency: String,
    val lentTotal: String,
    val lentOn: String,
    val repaid: String,
    val hasRepaid: Boolean,
    val dueLabel: String?,
    /** The day the due-date picker opens on: the due day, or a month from today. */
    val pickDueFrom: LocalDate,
    val progress: Float,
    val settledLabel: String?,
    val forgiven: Boolean,
    val events: List<LoanEventItem>,
    /** Their transfers that could pay it back: the repaying way, not yet part of a loan. */
    val candidates: List<TransactionItem>
)

/** One thing that happened to a loan, as the loan card lists it. */
data class LoanEventItem(
    val transactionId: Long?,
    val type: LoanEventType,
    val day: String,
    /** The transfer's kind and account; none for one without a transfer (forgiving). */
    val kind: TransactionKind?,
    val accountLabel: String?,
    /** Signed as money in or out: "+500.00", "−1,500.00"; forgiving is unsigned. */
    val amount: String,
    val tone: AmountTone
)

sealed interface PersonSheet {
    /** Choosing the transfer that pays [loanId] back. */
    data class Repay(val loanId: Long) : PersonSheet

    /** Letting go of what is left on [loanId], to confirm. */
    data class Forgive(val loanId: Long) : PersonSheet

    /** Changing when [loanId] is due, from [date]. */
    data class Due(val loanId: Long, val date: LocalDate) : PersonSheet

    data class Rename(val name: String, val problem: NameProblem? = null) : PersonSheet

    /** "Not this person": taking [spelling] out, to confirm. */
    data class Split(val spelling: SpellingRow) : PersonSheet

    /** Choosing who this is. */
    data class Merge(val query: String = "") : PersonSheet

    data class ConfirmMerge(val into: PersonOption) : PersonSheet
}
