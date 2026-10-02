package bassamalim.halala.features.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.Globals
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.enums.AmountTone
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.dateLabel
import bassamalim.halala.core.utils.initialOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val domain: PeopleDomain,
    private val navigator: Navigator
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<PeopleUiState> = combine(
        domain.observePeople(),
        domain.observeTransactions(),
        query
    ) { people, details, query ->
        val zone = domain.zone()
        val today = domain.today()
        val currency = Globals.PRIMARY_CURRENCY
        val flows = PeopleDomain.flowsByPerson(details, currency)

        PeopleUiState(
            isLoading = false,
            query = query,
            hasAny = people.isNotEmpty(),
            currency = currency,
            people = PeopleDomain.matching(people, query).map { row ->
                val net = flows[row.person.id]?.netMinor ?: 0
                PersonRow(
                    id = row.person.id,
                    name = row.person.name,
                    initial = initialOf(row.person.name),
                    transfers = row.transactions,
                    lastDate = row.lastAt?.let { dateLabel(it.atZone(zone).toLocalDate(), today) }.orEmpty(),
                    net = Money.format(net, currency, showPlus = net > 0),
                    tone = if (net > 0) AmountTone.Income else AmountTone.Spending
                )
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PeopleUiState()
    )

    fun onBackClick() = navigator.popBackStack()

    fun onQueryChange(text: String) = query.update { text }

    fun onPersonClick(id: Long) = navigator.navigate(Screen.Person(id))
}
