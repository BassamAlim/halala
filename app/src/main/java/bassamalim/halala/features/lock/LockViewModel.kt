package bassamalim.halala.features.lock

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.halala.core.data.repositories.PreferencesRepository
import bassamalim.halala.core.lock.LockManager
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.OpenRequest
import bassamalim.halala.core.nav.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LockViewModel @Inject constructor(
    private val lockManager: LockManager,
    private val navigator: Navigator,
    private val preferences: PreferencesRepository,
    private val openRequest: OpenRequest,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Screen.Lock>()

    fun onUnlocked() {
        lockManager.onUnlocked()

        if (route.resumable) {
            navigator.popBackStack()
            // The widget's "+ Cash" or a notification, tapped while locked.
            openRequest.consume()?.let { navigator.navigate(it) }
            return
        }

        viewModelScope.launch {
            // The first unlock sets up SMS reading; every one after opens the app.
            val onboarded = preferences.observeOnboarded().first()
            val next = if (onboarded) Screen.Main else Screen.Onboarding()
            navigator.navigate(next) {
                popUpTo<Screen.Lock> { inclusive = true }
            }
            val asked = openRequest.consume()
            if (asked != null && onboarded) navigator.navigate(asked)
        }
    }
}
