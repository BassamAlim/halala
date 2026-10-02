package bassamalim.halala.features.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.enums.ExpenseType
import bassamalim.halala.core.nav.Navigator
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
class CategoriesViewModel @Inject constructor(
    private val domain: CategoriesDomain,
    private val navigator: Navigator
) : ViewModel() {

    private val adding = MutableStateFlow<NewCategory?>(null)
    private val deletingId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<CategoriesUiState> = combine(
        domain.observeCategories(),
        adding,
        deletingId
    ) { categories, adding, deletingId ->
        val items = categories.map { (category, uses) ->
            CategoryItem(category.id, category.name, category.expenseType, uses)
        }

        CategoriesUiState(
            isLoading = false,
            categories = items,
            adding = adding,
            deleting = items.firstOrNull { it.id == deletingId }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CategoriesUiState()
    )

    fun onBackClick() = navigator.popBackStack()

    fun onAddClick() = adding.update { NewCategory() }

    fun onAddDismiss() = adding.update { null }

    fun onNameChange(name: String) = adding.update { it?.copy(name = name, problem = null) }

    /** Tapping the chosen type again clears it: a category needn't have one. */
    fun onTypeClick(type: ExpenseType) =
        adding.update { it?.copy(expenseType = type.takeIf { _ -> it.expenseType != type }) }

    fun onSaveClick() {
        val new = adding.value ?: return
        val names = uiState.value.categories.map { it.name }
        viewModelScope.launch {
            val problem = domain.add(new.name, new.expenseType, names)
            adding.update { if (problem == null) null else it?.copy(problem = problem) }
        }
    }

    fun onDeleteClick(id: Long) = deletingId.update { id }

    fun onDeleteDismiss() = deletingId.update { null }

    fun onDeleteConfirm() {
        val id = deletingId.value ?: return
        deletingId.update { null }
        viewModelScope.launch { domain.delete(id) }
    }
}
