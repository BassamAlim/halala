package bassamalim.halala.features.categories

import bassamalim.halala.core.utils.OneAtATime
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.halala.core.enums.BusinessType
import bassamalim.halala.core.enums.ExpenseType
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
class CategoriesViewModel @Inject constructor(
    private val domain: CategoriesDomain,
    private val navigator: Navigator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val saving = OneAtATime()

    /** Opened from a picker to add one: saving it goes back there. */
    private val adding = savedStateHandle.toRoute<Screen.Categories>().add

    private val form = MutableStateFlow(CategoryForm().takeIf { adding })
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
        saving.launch(viewModelScope) {
            val problem = domain.save(written, others)
            form.update { if (problem == null) null else it?.copy(problem = problem) }
            if (problem == null && adding && written.id == null) navigator.popBackStack()
            // The sheet closes, and the next one opened saves afresh.
            false
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
