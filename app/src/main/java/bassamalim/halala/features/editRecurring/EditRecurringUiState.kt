package bassamalim.halala.features.editRecurring

import java.time.LocalDate

data class EditRecurringUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val form: SeriesForm = SeriesForm(),
    /** "3 Oct", "Aug 2027 …": the form's days as words. */
    val dueLabel: String = "",
    val endsLabel: String? = null,
    /** Shown once Save has been tried. */
    val problems: Set<SeriesProblem> = emptySet(),
    val sheet: EditRecurringSheet? = null
)

sealed interface EditRecurringSheet {
    /** Choosing the next due day, or the last day it runs ([ends]), from [date]. */
    data class PickDate(val ends: Boolean, val date: LocalDate) : EditRecurringSheet

    data object ConfirmDelete : EditRecurringSheet
}
