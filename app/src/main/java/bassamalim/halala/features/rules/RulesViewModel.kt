package bassamalim.halala.features.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.dayLabel
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
class RulesViewModel @Inject constructor(
    private val domain: RulesDomain,
    private val navigator: Navigator
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<RulesUiState> = combine(
        domain.observeRules(),
        query,
        selectedId
    ) { rules, query, selectedId ->
        val zone = domain.zone()
        val today = domain.today()
        val items = rules.map { (stats, words) ->
            RuleItem(
                id = stats.rule.id,
                words = words,
                category = stats.categoryName.orEmpty(),
                expenseType = stats.rule.actions.expenseType,
                source = stats.rule.source,
                enabled = stats.rule.enabled,
                hits = stats.hits,
                lastHit = stats.lastHitAt?.let { dayLabel(it.atZone(zone).toLocalDate(), today) }
            )
        }
        val wanted = query.trim()

        RulesUiState(
            isLoading = false,
            query = query,
            rules = items.filter { rule ->
                listOfNotNull(rule.words.merchant, rule.words.contains, rule.words.account, rule.category)
                    .any { it.contains(wanted, ignoreCase = true) }
            },
            selected = items.firstOrNull { it.id == selectedId }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RulesUiState()
    )

    fun onBackClick() = navigator.popBackStack()

    fun onQueryChange(text: String) = query.update { text }

    fun onRuleClick(id: Long) = selectedId.update { id }

    fun onSheetDismiss() = selectedId.update { null }

    fun onAddClick() = navigator.navigate(Screen.EditRule())

    fun onEditClick() {
        val id = selectedId.value ?: return
        onSheetDismiss()
        navigator.navigate(Screen.EditRule(id))
    }

    fun onToggleClick() {
        val rule = uiState.value.selected ?: return
        onSheetDismiss()
        viewModelScope.launch { domain.setEnabled(rule.id, !rule.enabled) }
    }

    fun onDeleteClick() {
        val id = selectedId.value ?: return
        onSheetDismiss()
        viewModelScope.launch { domain.delete(id) }
    }
}
