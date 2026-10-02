package bassamalim.halala.core.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import bassamalim.halala.R
import bassamalim.halala.core.ui.theme.HalalaColors
import java.time.LocalTime

/** Material's time picker in a dialog, 24-hour, on the app's surface. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(time: LocalTime, onPicked: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val pickerState = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HalalaColors.Surface,
        confirmButton = {
            TextButton(onClick = { onPicked(LocalTime.of(pickerState.hour, pickerState.minute)) }) {
                Text(stringResource(R.string.done))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = { TimePicker(state = pickerState) }
    )
}
