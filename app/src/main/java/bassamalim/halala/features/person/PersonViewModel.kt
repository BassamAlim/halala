package bassamalim.halala.features.person

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.halala.core.Globals
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.feedOf
import bassamalim.halala.core.domain.toItem
import bassamalim.halala.core.enums.AmountTone
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.initialOf
import bassamalim.halala.features.people.PeopleDomain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PersonViewModel @Inject constructor(
    private val domain: PersonDomain,
    private val navigator: Navigator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val id = savedStateHandle.toRoute<Screen.Person>().id

    private val sheet = MutableStateFlow<PersonSheet?>(null)

    val uiState: StateFlow<PersonUiState> = combine(
        domain.observePerson(id),
        domain.observeAliases(id),
        domain.observePeople(),
        domain.observeTransactions(),
        sheet
    ) { person, aliases, people, details, sheet ->
        // Gone (merged into someone else): the screen stays as it was while it leaves.
        if (person == null) return@combine PersonUiState(isLoading = true)

        val zone = domain.zone()
        val today = domain.today()
        val mine = feedOf(details.filter { it.personId == id })
        val currency = Globals.PRIMARY_CURRENCY
        val flow = PeopleDomain.flowsByPerson(mine, currency)[id]
        val net = flow?.netMinor ?: 0

        PersonUiState(
            isLoading = false,
            name = person.name,
            initial = initialOf(person.name),
            currency = currency,
            sent = Money.format(flow?.sentMinor ?: 0, currency, decimals = false),
            received = Money.format(flow?.receivedMinor ?: 0, currency, decimals = false),
            net = Money.format(net, currency, decimals = false, showPlus = net > 0),
            netTone = if (net > 0) AmountTone.Income else AmountTone.Spending,
            count = mine.size,
            spellings = aliases.map { SpellingRow(it.alias.id, it.alias.descriptor, it.transactions) },
            canSplit = aliases.size > 1,
            transactions = mine.map { it.toItem(zone, today) },
            mergeOptions = (sheet as? PersonSheet.Merge)
                ?.let { PersonDomain.mergeOptions(people, id, it.query) }
                ?.map { PersonOption(it.person.id, it.person.name, it.transactions) }
                .orEmpty(),
            sheet = sheet
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PersonUiState()
    )

    fun onBackClick() = navigator.popBackStack()

    fun onTransactionClick(transactionId: Long) = navigator.navigate(Screen.Transaction(transactionId))

    fun onSheetDismiss() = sheet.update { null }

    fun onRenameClick() = sheet.update { PersonSheet.Rename(uiState.value.name) }

    fun onNameChange(name: String) = sheet.update { PersonSheet.Rename(name) }

    fun onRenameSave() {
        val rename = sheet.value as? PersonSheet.Rename ?: return
        viewModelScope.launch {
            val problem = domain.rename(id, rename.name)
            sheet.update { if (problem == null) null else rename.copy(problem = problem) }
        }
    }

    fun onSpellingClick(spelling: SpellingRow) {
        if (uiState.value.canSplit) sheet.update { PersonSheet.Split(spelling) }
    }

    fun onSplitConfirm() {
        val split = sheet.value as? PersonSheet.Split ?: return
        sheet.update { null }
        viewModelScope.launch { domain.split(split.spelling.id) }
    }

    fun onMergeClick() = sheet.update { PersonSheet.Merge() }

    fun onMergeQueryChange(query: String) = sheet.update { PersonSheet.Merge(query) }

    fun onMergePick(option: PersonOption) = sheet.update { PersonSheet.ConfirmMerge(option) }

    /** This person is gone once merged: their page gives way to the one they joined. */
    fun onMergeConfirm() {
        val into = (sheet.value as? PersonSheet.ConfirmMerge)?.into ?: return
        sheet.update { null }
        viewModelScope.launch {
            domain.merge(id, into.id)
            navigator.popBackStack()
            navigator.navigate(Screen.Person(into.id))
        }
    }
}
