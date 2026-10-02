package bassamalim.halala.features.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.ui.components.ButtonKind
import bassamalim.halala.core.ui.components.ChipStyle
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.HalalaChip
import bassamalim.halala.core.ui.components.HalalaSheet
import bassamalim.halala.core.ui.components.HalalaTextField
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Spacing

/** What goes in Transaction detail's Tags row: its tags as chips, or "Add". [row] lays it out as the card's other rows. */
@Composable
fun TransactionTags(row: @Composable (value: @Composable () -> Unit) -> Unit, viewModel: TransactionTagsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val names = state.all.filter { it.first in state.on }.map { it.second }

    row {
        HalalaChip(
            label = names.joinToString(", ").ifEmpty { stringResource(R.string.add) },
            style = if (names.isEmpty()) ChipStyle.Outline else ChipStyle.Plain,
            onClick = viewModel::onEditClick
        )
    }

    if (state.editing) TagsSheet(state, viewModel)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsSheet(state: TransactionTagsState, viewModel: TransactionTagsViewModel) {
    HalalaSheet(viewModel::onDismiss) {
        Text(text = stringResource(R.string.tags), style = HalalaType.Title)
        if (state.all.isEmpty()) Text(text = stringResource(R.string.tags_none_yet), style = HalalaType.Label, color = HalalaColors.TextMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            state.all.forEach { (id, name) ->
                HalalaChip(label = name, style = if (id in state.on) ChipStyle.On else ChipStyle.Outline, onClick = { viewModel.onToggle(id) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            HalalaTextField(
                value = state.draft,
                onValueChange = viewModel::onDraftChange,
                placeholder = stringResource(R.string.tag_new_name),
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
                onImeAction = viewModel::onAddClick,
                modifier = Modifier.weight(1f)
            )
            HalalaButton(stringResource(R.string.add), viewModel::onAddClick)
        }
        HalalaButton(stringResource(R.string.done), viewModel::onDismiss, Modifier.fillMaxWidth(), kind = ButtonKind.Primary)
    }
}
