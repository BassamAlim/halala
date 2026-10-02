package bassamalim.halala.features.editBudget

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.halala.core.enums.BudgetScope
import bassamalim.halala.core.enums.ExpenseType
import bassamalim.halala.core.models.CategoryOption
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
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
class EditBudgetViewModel @Inject constructor(
    private val domain: EditBudgetDomain,
    private val navigator: Navigator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val id = savedStateHandle.toRoute<Screen.EditBudget>().id

    private val form = MutableStateFlow(if (id == 0L) BudgetForm() else null)
    private val problems = MutableStateFlow(emptySet<BudgetProblem>())
    private val query = MutableStateFlow("")
    private val confirmingDelete = MutableStateFlow(false)

    init {
        if (id != 0L) viewModelScope.launch { form.value = domain.load(id) ?: BudgetForm() }
    }

    val uiState: StateFlow<EditBudgetUiState> = combine(
        combine(form, problems, ::Pair),
        domain.observeCategories(),
        domain.observeMerchants(),
        query,
        confirmingDelete
    ) { (form, problems), categories, merchants, query, confirming ->
        val chosen = merchants.firstOrNull { it.merchant.id == form?.merchantId }
        val matching = merchants.filter { it.merchant.name.contains(query.trim(), ignoreCase = true) }.take(MERCHANT_CHOICES)
        EditBudgetUiState(
            isLoading = form == null,
            isNew = id == 0L,
            form = form ?: BudgetForm(),
            categories = categories.map { CategoryOption(it.id, it.name) },
            // The chosen one stays in view whatever the search.
            merchants = (listOfNotNull(chosen) + matching).distinctBy { it.merchant.id }
                .map { CategoryOption(it.merchant.id, it.merchant.name) },
            merchantQuery = query,
            problems = problems,
            isConfirmingDelete = confirming
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditBudgetUiState())

    fun onBackClick() = navigator.popBackStack()

    fun onScopeClick(scope: BudgetScope) = edit { it.copy(scope = scope) }

    fun onCategoryClick(option: CategoryOption) = edit { it.copy(categoryId = option.id) }

    fun onTypeClick(type: ExpenseType) = edit { it.copy(expenseType = type) }

    fun onMerchantClick(option: CategoryOption) = edit { it.copy(merchantId = option.id) }

    fun onMerchantQueryChange(text: String) = query.update { text }

    fun onAmountChange(text: String) = edit { it.copy(amount = text) }

    fun onRolloverClick(on: Boolean) = edit { it.copy(rollover = on) }

    fun onDeleteClick() = confirmingDelete.update { true }

    fun onDeleteDismiss() = confirmingDelete.update { false }

    fun onDeleteConfirm() {
        confirmingDelete.update { false }
        viewModelScope.launch {
            domain.delete(id)
            navigator.popBackStack()
        }
    }

    fun onSaveClick() {
        val current = form.value ?: return
        viewModelScope.launch {
            val found = domain.save(id, current)
            problems.update { found }
            if (found.isEmpty()) navigator.popBackStack()
        }
    }

    private fun edit(change: (BudgetForm) -> BudgetForm) {
        form.update { it?.let(change) }
        problems.update { emptySet() }
    }

    private companion object {
        const val MERCHANT_CHOICES = 12
    }
}
