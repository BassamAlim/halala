package bassamalim.halala.features.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    domain: MainDomain,
    private val navigator: Navigator
) : ViewModel() {

    init {
        viewModelScope.launch { domain.catchUp() }
    }

    /** The global quick-add: a new transaction, on the cash wallet unless you pick another. */
    fun onQuickAddClick() = navigator.navigate(Screen.EditTransaction())
}
