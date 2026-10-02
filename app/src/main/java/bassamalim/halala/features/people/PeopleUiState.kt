package bassamalim.halala.features.people

import bassamalim.halala.core.enums.AmountTone

data class PeopleUiState(
    val isLoading: Boolean = true,
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
