package bassamalim.halala.features.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.models.CategoryOption
import bassamalim.halala.core.nav.Navigator
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
    private val pickingCategory = MutableStateFlow(false)

    val uiState: StateFlow<RulesUiState> = combine(
        domain.observeRules(),
        domain.observeCategories(),
        query,
        selectedId,
        pickingCategory
    ) { rules, categories, query, selectedId, picking ->
        val zone = domain.zone()
        val today = domain.today()
        val items = rules.map { (rule, categoryName, hits, lastHitAt) ->
            RuleItem(
                id = rule.id,
                merchant = rule.conditions.merchant.orEmpty(),
                categoryId = rule.actions.categoryId,
                category = categoryName.orEmpty(),
                expenseType = rule.actions.expenseType,
                source = rule.source,
                enabled = rule.enabled,
                hits = hits,
                lastHit = lastHitAt?.let { dayLabel(it.atZone(zone).toLocalDate(), today) }
            )
        }

        RulesUiState(
            isLoading = false,
            query = query,
            rules = items.filter {
                it.merchant.contains(query.trim(), ignoreCase = true) ||
                        it.category.contains(query.trim(), ignoreCase = true)
            },
            categories = categories.map { CategoryOption(it.id, it.name) },
            selected = items.firstOrNull { it.id == selectedId },
            isPickingCategory = picking
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RulesUiState()
    )

    fun onBackClick() = navigator.popBackStack()

    fun onQueryChange(text: String) = query.update { text }

    fun onRuleClick(id: Long) = selectedId.update { id }

    fun onSheetDismiss() {
        selectedId.update { null }
        pickingCategory.update { false }
    }

    fun onChangeCategoryClick() = pickingCategory.update { true }

    fun onCategoryPick(category: CategoryOption) {
        val id = selectedId.value ?: return
        onSheetDismiss()
        viewModelScope.launch { domain.retarget(id, category.id) }
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
