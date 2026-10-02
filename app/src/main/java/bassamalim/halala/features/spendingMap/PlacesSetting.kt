package bassamalim.halala.features.spendingMap

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import bassamalim.halala.R
import bassamalim.halala.core.data.repositories.PlacesRepository
import bassamalim.halala.core.data.repositories.PreferencesRepository
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.places.PlaceCapture
import bassamalim.halala.core.ui.components.ButtonKind
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.HalalaSheet
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlacesSettingState(val on: Boolean = false, val open: Boolean = false, val denied: Boolean = false)

/** Settings' "Remember where you spend": the switch, the location permissions it needs, and forgetting. */
@HiltViewModel
class PlacesSettingViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
    private val places: PlacesRepository,
    private val capture: PlaceCapture,
    private val navigator: Navigator
) : ViewModel() {

    private val local = MutableStateFlow(false to false)

    val uiState: StateFlow<PlacesSettingState> = combine(preferences.observePlacesOn(), local) { on, (open, denied) ->
        PlacesSettingState(on = on && capture.hasPermission(), open = open, denied = denied)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlacesSettingState())

    fun onRowClick() = local.update { true to false }
    fun onDismiss() = local.update { false to false }

    /** After the permission dialogs: on only with location in the background too. */
    fun onPermissionsResult() {
        val granted = capture.hasPermission()
        viewModelScope.launch { preferences.setPlacesOn(granted) }
        local.update { !granted to !granted }
    }

    fun onTurnOff(forget: Boolean) {
        local.update { false to false }
        viewModelScope.launch {
            preferences.setPlacesOn(false)
            if (forget) places.forgetAll()
        }
    }

    fun onMapClick() {
        local.update { false to false }
        navigator.navigate(Screen.SpendingMap)
    }
}

@Composable
fun PlacesSettingRow(divider: Boolean, viewModel: PlacesSettingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Android 11 and later ask for "all the time" on its own, after the first.
    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.onPermissionsResult() }
    val foreground = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val any = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (any && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        else viewModel.onPermissionsResult()
    }

    ListRow(
        title = stringResource(R.string.places_title),
        subtitle = stringResource(if (state.on) R.string.places_on else R.string.places_off),
        divider = divider,
        onClick = viewModel::onRowClick
    )

    if (state.open) HalalaSheet(viewModel::onDismiss) {
        Text(text = stringResource(R.string.places_title), style = HalalaType.Title)
        Text(text = stringResource(R.string.places_explain), style = HalalaType.Body, color = HalalaColors.TextMuted)
        if (state.denied) Text(text = stringResource(R.string.places_denied), style = HalalaType.Label, color = HalalaColors.Info)
        if (state.on) {
            HalalaButton(stringResource(R.string.places_see_map), viewModel::onMapClick, Modifier.fillMaxWidth(), kind = ButtonKind.Primary)
            HalalaButton(stringResource(R.string.places_turn_off), { viewModel.onTurnOff(forget = false) }, Modifier.fillMaxWidth())
        } else HalalaButton(
            stringResource(R.string.places_turn_on),
            {
                val asked = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) asked += Manifest.permission.ACCESS_BACKGROUND_LOCATION
                foreground.launch(asked.toTypedArray())
            },
            Modifier.fillMaxWidth(),
            kind = ButtonKind.Primary
        )
        HalalaButton(stringResource(R.string.places_forget), { viewModel.onTurnOff(forget = true) }, Modifier.fillMaxWidth(), destructive = true)
    }
}
