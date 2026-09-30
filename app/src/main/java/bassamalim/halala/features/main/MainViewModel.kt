package bassamalim.halala.features.main

import androidx.lifecycle.ViewModel
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val navigator: Navigator
) : ViewModel() {

    /** The global quick-add: a new transaction, on the cash wallet unless you pick another. */
    fun onQuickAddClick() = navigator.navigate(Screen.EditTransaction())
}
