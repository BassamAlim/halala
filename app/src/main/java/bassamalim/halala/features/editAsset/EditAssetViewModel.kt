package bassamalim.halala.features.editAsset

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.halala.core.enums.AssetType
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.shortDateLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class EditAssetUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val form: AssetForm = AssetForm(),
    /** "28 Sep": when its price or value is from; today until you say. */
    val dateLabel: String = "",
    val problems: Set<AssetProblem> = emptySet(),
    val pickingDate: Boolean = false,
    val pickFrom: LocalDate = LocalDate.MIN,
    val isConfirmingDelete: Boolean = false
)

@HiltViewModel
class EditAssetViewModel @Inject constructor(
    private val domain: EditAssetDomain,
    private val navigator: Navigator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val id = savedStateHandle.toRoute<Screen.EditAsset>().id

    private val form = MutableStateFlow(if (id == 0L) AssetForm() else null)
    private val problems = MutableStateFlow(emptySet<AssetProblem>())
    private val picking = MutableStateFlow(false)
    private val confirmingDelete = MutableStateFlow(false)

    init {
        if (id != 0L) viewModelScope.launch { form.value = domain.load(id) ?: AssetForm() }
    }

    val uiState: StateFlow<EditAssetUiState> = combine(form, problems, picking, confirmingDelete) { form, problems, picking, confirming ->
        val today = domain.today()
        EditAssetUiState(
            isLoading = form == null,
            isNew = id == 0L,
            form = form ?: AssetForm(),
            dateLabel = shortDateLabel(form?.priceDate ?: today, today),
            problems = problems,
            pickingDate = picking,
            pickFrom = form?.priceDate ?: today,
            isConfirmingDelete = confirming
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditAssetUiState())

    fun onBackClick() = navigator.popBackStack()
    fun onTypeClick(type: AssetType) = edit { it.copy(type = type) }
    fun onNameChange(text: String) = edit { it.copy(name = text) }
    fun onQuantityChange(text: String) = edit { it.copy(quantity = text) }
    fun onKaratClick(karat: Int) = edit { it.copy(karat = karat) }
    fun onPriceChange(text: String) = edit { it.copy(unitPrice = text) }
    fun onValueChange(text: String) = edit { it.copy(value = text) }
    fun onCostChange(text: String) = edit { it.copy(cost = text) }
    fun onSpreadChange(text: String) = edit { it.copy(spread = text) }
    fun onDepreciationChange(text: String) = edit { it.copy(depreciation = text) }
    fun onDateClick() = picking.update { true }
    fun onDateDismiss() = picking.update { false }
    fun onDatePicked(date: LocalDate) {
        picking.update { false }
        edit { it.copy(priceDate = date) }
    }

    fun onDeleteClick() = confirmingDelete.update { true }
    fun onDeleteDismiss() = confirmingDelete.update { false }
    fun onDeleteConfirm() {
        confirmingDelete.update { false }
        val currency = form.value?.currency ?: return
        viewModelScope.launch {
            domain.delete(id, currency)
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

    private fun edit(change: (AssetForm) -> AssetForm) {
        form.update { it?.let(change) }
        problems.update { emptySet() }
    }
}
