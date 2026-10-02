package bassamalim.halala.features.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import bassamalim.halala.core.data.repositories.PreferencesRepository
import bassamalim.halala.core.places.LocationAccess
import bassamalim.halala.core.places.PlaceCapture
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    domain: MainDomain,
    private val navigator: Navigator,
    private val preferences: PreferencesRepository,
    private val capture: PlaceCapture
) : ViewModel() {

    /** Once, the first time in after onboarding: ask for location, unless it is already allowed. */
    val askLocation: StateFlow<Boolean> = preferences.observeLocationAsked()
        .map { asked -> !asked && capture.access() != LocationAccess.GRANTED }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onLocationAnswered() {
        viewModelScope.launch { preferences.setLocationAsked() }
    }

    init {
        viewModelScope.launch { domain.catchUp() }
    }

    /** The global quick-add: a new transaction, on the cash wallet unless you pick another. */
    fun onQuickAddClick() = navigator.navigate(Screen.EditTransaction())
}
