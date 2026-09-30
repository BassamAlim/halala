package bassamalim.halala.features.editTransaction

import bassamalim.halala.core.enums.TransactionKind
import java.time.LocalDate
import java.time.LocalTime

data class EditTransactionUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    /** Out and In can trade places; a move stays a move, and a single never becomes one. */
    val modes: List<EntryMode> = EntryMode.entries,
    val form: TransactionForm = TransactionForm(date = LocalDate.MIN, time = LocalTime.MIDNIGHT),
    val accounts: List<AccountOption> = emptyList(),
    val kinds: List<TransactionKind> = emptyList(),
    /** The sending account's currency, shown beside the amount. */
    val currency: String = "",
    /** "Mon 29 Sep" and "21:14". */
    val dateLabel: String = "",
    val timeLabel: String = "",
    val isPickingDate: Boolean = false,
    val isPickingTime: Boolean = false,
    val problems: Set<TransactionProblem> = emptySet()
)
