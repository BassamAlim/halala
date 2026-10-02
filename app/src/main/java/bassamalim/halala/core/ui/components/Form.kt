package bassamalim.halala.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
    val bringIntoView = remember { BringIntoViewRequester() }
    // Problems appear on Save; one scrolled out of sight is brought back to you.
    LaunchedEffect(error) { if (error != null) bringIntoView.bringIntoView() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
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
                enabled = enabled,
                onClick = { onSelect(option) }
            )
        }
    }
}
