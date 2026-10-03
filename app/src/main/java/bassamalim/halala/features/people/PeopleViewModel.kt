package bassamalim.halala.features.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.Globals
import bassamalim.halala.core.domain.Loans
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.enums.AmountTone
import bassamalim.halala.core.enums.LoanDirection
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.shortDateLabel
import bassamalim.halala.core.utils.initialOf
import bassamalim.halala.core.data.dataSources.room.relations.PersonWithStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val domain: PeopleDomain,
    private val navigator: Navigator
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val view = MutableStateFlow(PeopleView.LOANS)

    val uiState: StateFlow<PeopleUiState> = combine(
        domain.observePeople(),
        domain.observeTransactions(),
        domain.observeLoans(),
        query,
        view
    ) { people, details, loans, query, view ->
        val zone = domain.zone()
        val today = domain.today()
        val currency = Globals.PRIMARY_CURRENCY
        val flows = PeopleDomain.flowsByPerson(details, currency)
        val names = people.associate { it.person.id to it.person.name }
        val (owedToYou, youOwe) = Loans.owed(loans, currency)
        fun day(at: Instant?) = at?.let { shortDateLabel(it.atZone(zone).toLocalDate(), today) }

        val rows = loans.map { state ->
            val lent = state.loan.direction == LoanDirection.LENT
            val name = names[state.loan.personId].orEmpty()
            LoanRow(
                loanId = state.loan.id,
                personId = state.loan.personId,
                name = name,
                initial = initialOf(name),
                lent = lent,
                dueLabel = state.loan.dueOn?.let { shortDateLabel(it, today) },
                lentOnLabel = day(state.lentAt),
                settledLabel = day(state.settledAt),
                forgiven = state.forgivenMinor > 0,
                amount = Money.format(
                    if (lent) state.remainingMinor else -state.remainingMinor,
                    state.loan.currency,
                    showPlus = lent && state.isOpen
                ),
                tone = if (lent) AmountTone.Income else AmountTone.Spending
            ) to state
        }

        PeopleUiState(
            isLoading = false,
            view = view,
            owedToYou = Money.format(owedToYou, currency, decimals = false),
            youOwe = Money.format(youOwe, currency, decimals = false),
            // Open: the one due soonest first, then the oldest. Settled: the latest first.
            openLoans = rows.filter { it.second.isOpen }
                .sortedWith(compareBy({ it.second.loan.dueOn ?: LocalDate.MAX }, { it.second.lentAt }))
                .map { it.first },
            settledLoans = rows.filter { !it.second.isOpen }.sortedByDescending { it.second.settledAt }.map { it.first },
            query = query,
            hasAny = people.isNotEmpty(),
            currency = currency,
            // Most transfers between you first; among equals, the latest.
            people = PeopleDomain.matching(people, query)
                .sortedWith(compareByDescending<PersonWithStats> { it.transactions }.thenByDescending { it.lastAt })
                .map { row ->
                val net = flows[row.person.id]?.netMinor ?: 0
                PersonRow(
                    id = row.person.id,
                    name = row.person.name,
                    initial = initialOf(row.person.name),
                    transfers = row.transactions,
                    lastDate = row.lastAt?.let { shortDateLabel(it.atZone(zone).toLocalDate(), today) }.orEmpty(),
                    net = Money.format(net, currency, showPlus = net > 0),
                    tone = if (net > 0) AmountTone.Income else AmountTone.Spending
                )
            }
        )
    }.combine(domain.observeSuggestions()) { state, offers ->
        state.copy(
            suggestions = offers.map { offer ->
                MergeRow(
                    key = offer.suggestion.key,
                    keepId = offer.keep.id,
                    goesId = offer.goes.id,
                    keepName = offer.keep.name,
                    goesName = offer.goes.name,
                    reason = offer.suggestion.reason,
                    ref = offer.suggestion.ref
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

    fun onViewChange(index: Int) = view.update { PeopleView.entries[index] }

    fun onPersonClick(id: Long) = navigator.navigate(Screen.Person(id))

    fun onMerge(row: MergeRow) {
        viewModelScope.launch { domain.merge(fromId = row.goesId, intoId = row.keepId) }
    }

    fun onNotSame(row: MergeRow) {
        viewModelScope.launch { domain.dismiss(row.key) }
    }
}
