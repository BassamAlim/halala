package bassamalim.halala.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Spacing

/**
 * A labelled part of a form: the muted label, the control, then a caption under it that is
 * either the problem (in state-over) or a hint.
 */
@Composable
fun FormField(
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    hint: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        CardLabel(label)
        content()
        when {
            error != null -> Text(text = error, style = HalalaType.Caption, color = HalalaColors.StateOver)
            hint != null -> Text(text = hint, style = HalalaType.Caption, color = HalalaColors.TextMuted)
        }
    }
}

/** One choice out of several, as chips that wrap: the chosen one filled, the rest outlined. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        options.forEach { option ->
            HalalaChip(
                label = label(option),
                style = if (option == selected) ChipStyle.On else ChipStyle.Outline,
                onClick = if (enabled) ({ onSelect(option) }) else null
            )
        }
    }
}
