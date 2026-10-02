package bassamalim.halala.features.people

import bassamalim.halala.core.enums.AmountTone

/** The People board's two views: loans, and everyone you transfer with. */
enum class PeopleView { LOANS, TRANSFERS }

data class PeopleUiState(
    val isLoading: Boolean = true,
    val view: PeopleView = PeopleView.LOANS,
    /** Open loans each way, summary style: "1,200", "0". */
    val owedToYou: String = "",
    val youOwe: String = "",
    val openLoans: List<LoanRow> = emptyList(),
    val settledLoans: List<LoanRow> = emptyList(),
    val query: String = "",
    /** Whether there are any at all, so an empty search isn't mistaken for none yet. */
    val hasAny: Boolean = false,
    val currency: String = "",
    val people: List<PersonRow> = emptyList()
)

/**
 * One person: [net] is what came back less what went ("−1,000.00", "+200.00"), toned as money
 * in or out; [lastDate] is the latest transfer's day ("26 Sep"), blank with none.
 */
data class PersonRow(
    val id: Long,
    val name: String,
    val initial: String,
    val transfers: Int,
    val lastDate: String,
    val net: String,
    val tone: AmountTone
)

/**
 * One loan as the Loans view lists it: who, which way ([lent]: they owe you), when it is due or
 * was lent (open) or was settled, and [amount] still owed, signed as money that will come in
 * ("+1,000.00") or go out ("−300.00"); "0.00" once settled.
 */
data class LoanRow(
    val loanId: Long,
    val personId: Long,
    val name: String,
    val initial: String,
    val lent: Boolean,
    val dueLabel: String? = null,
    val lentOnLabel: String? = null,
    val settledLabel: String? = null,
    val forgiven: Boolean = false,
    val amount: String,
    val tone: AmountTone
)
