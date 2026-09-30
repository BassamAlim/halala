package bassamalim.halala.features.lock

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute
import bassamalim.halala.core.lock.LockManager
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LockViewModel @Inject constructor(
    private val lockManager: LockManager,
    private val navigator: Navigator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Screen.Lock>()

    fun onUnlocked() {
        lockManager.onUnlocked()

        if (route.resumable) {
            navigator.popBackStack()
            return
        }

        navigator.navigate(Screen.Main) {
            popUpTo<Screen.Lock> { inclusive = true }
        }
    }
}
