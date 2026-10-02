package bassamalim.halala.features.assistant

import androidx.lifecycle.ViewModel
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AssistantViewModel @Inject constructor(private val navigator: Navigator) : ViewModel() {
    fun onDigestsClick() = navigator.navigate(Screen.Digests)
}
