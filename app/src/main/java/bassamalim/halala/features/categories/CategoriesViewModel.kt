package bassamalim.halala.features.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.enums.BusinessType
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

    private val form = MutableStateFlow<CategoryForm?>(null)
    private val deletingId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<CategoriesUiState> = combine(
        domain.observeCategories(),
        form,
        deletingId
    ) { categories, form, deletingId ->
        val items = categories.map { (category, uses) ->
            CategoryItem(category.id, category.name, category.expenseType, category.businessTypes, uses)
        }

        CategoriesUiState(
            isLoading = false,
            categories = items,
            form = form,
            deleting = items.firstOrNull { it.id == deletingId }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CategoriesUiState()
    )

    fun onBackClick() = navigator.popBackStack()

    fun onAddClick() = form.update { CategoryForm() }

    fun onCategoryClick(category: CategoryItem) = form.update {
        CategoryForm(category.id, category.name, category.expenseType, category.businessTypes)
    }

    fun onFormDismiss() = form.update { null }

    fun onNameChange(name: String) = form.update { it?.copy(name = name, problem = null) }

    /** Tapping the chosen type again clears it: a category needn't have one. */
    fun onTypeClick(type: ExpenseType) =
        form.update { it?.copy(expenseType = type.takeIf { _ -> it.expenseType != type }) }

    fun onBusinessTypeClick(type: BusinessType) = form.update {
        it?.copy(businessTypes = if (type in it.businessTypes) it.businessTypes - type else it.businessTypes + type)
    }

    fun onSaveClick() {
        val written = form.value ?: return
        val others = uiState.value.categories.filter { it.id != written.id }.map { it.name }
        viewModelScope.launch {
            val problem = domain.save(written, others)
            form.update { if (problem == null) null else it?.copy(problem = problem) }
        }
    }

    fun onDeleteClick() {
        val id = form.value?.id ?: return
        form.update { null }
        deletingId.update { id }
    }

    fun onDeleteDismiss() = deletingId.update { null }

    fun onDeleteConfirm() {
        val id = deletingId.value ?: return
        deletingId.update { null }
        viewModelScope.launch { domain.delete(id) }
    }
}
