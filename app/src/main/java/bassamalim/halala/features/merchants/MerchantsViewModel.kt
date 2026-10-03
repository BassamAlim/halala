package bassamalim.halala.features.merchants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
class MerchantsViewModel @Inject constructor(
    private val domain: MerchantsDomain,
    private val navigator: Navigator
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<MerchantsUiState> = combine(domain.observeMerchants(), domain.observeSuggestions(), query) { merchants, offers, query ->
        MerchantsUiState(
            isLoading = false,
            query = query,
            hasAny = merchants.isNotEmpty(),
            merchants = MerchantsDomain.filter(merchants, query).map {
                MerchantRow(it.merchant.id, it.merchant.name, it.transactions, it.aliases)
            },
            suggestions = offers.map {
                MerchantMergeRow(it.key, it.keep.merchant.id, it.goes.merchant.id, it.keep.merchant.name, it.goes.merchant.name, it.website)
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MerchantsUiState()
    )

    fun onBackClick() = navigator.popBackStack()

    fun onQueryChange(value: String) = query.update { value }

    fun onMerchantClick(id: Long) = navigator.navigate(Screen.Merchant(id))

    fun onMerge(row: MerchantMergeRow) {
        viewModelScope.launch { domain.merge(fromId = row.goesId, intoId = row.keepId) }
    }

    fun onNotSame(row: MerchantMergeRow) {
        viewModelScope.launch { domain.dismiss(row.key) }
    }
}
