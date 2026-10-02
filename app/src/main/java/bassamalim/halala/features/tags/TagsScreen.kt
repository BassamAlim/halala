package bassamalim.halala.features.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.ui.components.ButtonKind
import bassamalim.halala.core.ui.components.ChoiceChips
import bassamalim.halala.core.ui.components.ConfirmSheet
import bassamalim.halala.core.ui.components.DateDialog
import bassamalim.halala.core.ui.components.FormField
import bassamalim.halala.core.ui.components.GroupLabel
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.HalalaCard
import bassamalim.halala.core.ui.components.HalalaTextField
import bassamalim.halala.core.ui.components.ListCard
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.components.TransactionItemRow
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Spacing

/** Tags (no board): what the spending suggests, then every tag with its days, count and spending. */
@Composable
fun TagsScreen(viewModel: TagsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.section),
        verticalArrangement = Arrangement.spacedBy(Spacing.card)
    ) {
        TopBar(
            title = stringResource(R.string.tags),
            onBack = viewModel::onBackClick,
            actionLabel = stringResource(R.string.tag_new),
            onAction = viewModel::onNewClick
        )
        if (state.isLoading) return@Column

        state.suggestions.forEach { s ->
            val name = stringResource(R.string.tag_trip_to, s.place)
            HalalaCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(text = stringResource(R.string.tag_suggest_title, name), style = HalalaType.BodyStrong)
                Text(
                    text = pluralStringResource(R.plurals.tag_suggest_body, s.count, s.count, s.place, daysText(s.days)),
                    style = HalalaType.Label,
                    color = HalalaColors.TextMuted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    HalalaButton(stringResource(R.string.tag_suggest_yes), { viewModel.onAcceptClick(s.key, name) }, Modifier.weight(1f), kind = ButtonKind.Primary)
                    HalalaButton(stringResource(R.string.tag_suggest_no), { viewModel.onDismissClick(s.key) }, Modifier.weight(1f))
                }
            }
        }

        if (state.tags.isEmpty()) Text(text = stringResource(R.string.tags_empty), style = HalalaType.Body, color = HalalaColors.TextMuted)
        else ListCard(Modifier.fillMaxWidth()) {
            state.tags.forEachIndexed { index, tag ->
                ListRow(
                    title = tag.name,
                    subtitle = listOfNotNull(
                        tag.days?.let { daysText(it) },
                        pluralStringResource(R.plurals.tag_count, tag.count, tag.count)
                    ).joinToString(" · "),
                    divider = index > 0,
                    trailing = { Text(text = tag.spent, style = HalalaNumbers.Amount) },
                    onClick = { viewModel.onTagClick(tag.id) }
                )
            }
        }
        Text(text = stringResource(R.string.tags_explain), style = HalalaType.Caption, color = HalalaColors.TextMuted)
    }
}

@Composable
fun daysText(days: Days) =
    if (days.to == null) stringResource(R.string.tag_from, days.from) else stringResource(R.string.tag_between, days.from, days.to)

@Composable
fun EditTagScreen(viewModel: EditTagViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val form = state.form

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.section),
        verticalArrangement = Arrangement.spacedBy(Spacing.section)
    ) {
        TopBar(
            title = stringResource(if (state.isNew) R.string.tag_new else R.string.tag_edit),
            onBack = viewModel::onBackClick,
            actionLabel = stringResource(R.string.save),
            actionEnabled = !state.isLoading,
            onAction = viewModel::onSaveClick
        )
        if (state.isLoading) return@Column

        FormField(label = stringResource(R.string.tag_name), error = stringResource(R.string.merchant_name_missing).takeIf { state.nameMissing }) {
            HalalaTextField(value = form.name, onValueChange = viewModel::onNameChange, isError = state.nameMissing, capitalization = KeyboardCapitalization.Words)
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ListCard(Modifier.fillMaxWidth()) {
                ListRow(
                    title = stringResource(R.string.tag_starts),
                    subtitle = state.startLabel ?: stringResource(R.string.tag_no_days),
                    onClick = { viewModel.onDateClick(TagDate.START) }
                )
                ListRow(
                    title = stringResource(R.string.tag_ends),
                    subtitle = state.endLabel ?: stringResource(R.string.tag_running),
                    divider = true,
                    onClick = { viewModel.onDateClick(TagDate.END) }
                )
            }
            if (state.endsBeforeStart) Text(text = stringResource(R.string.tag_ends_before), style = HalalaType.Label, color = HalalaColors.StateOver)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (form.startsOn != null) HalalaButton(stringResource(R.string.tag_clear_days), viewModel::onStartClear, Modifier.weight(1f))
                if (form.endsOn != null) HalalaButton(stringResource(R.string.tag_no_end), viewModel::onEndClear, Modifier.weight(1f))
            }
        }
        if (form.startsOn != null) FormField(label = stringResource(R.string.tag_auto), hint = stringResource(R.string.tag_auto_hint)) {
            ChoiceChips(
                options = listOf(true, false),
                selected = form.auto,
                label = { stringResource(if (it) R.string.yes else R.string.no) },
                onSelect = viewModel::onAutoClick
            )
        }

        if (!state.isNew) {
            Column {
                GroupLabel(stringResource(R.string.tag_carrying, state.spent))
                state.items.forEachIndexed { index, item ->
                    TransactionItemRow(item = item, divider = index > 0, onClick = { viewModel.onTransactionClick(item.id) })
                }
            }
            HalalaButton(stringResource(R.string.delete), viewModel::onDeleteClick, Modifier.fillMaxWidth(), destructive = true)
        }
    }

    state.picking?.let { DateDialog(date = state.pickFrom, onPicked = viewModel::onDatePicked, onDismiss = viewModel::onDateDismiss) }
    if (state.confirmingDelete) ConfirmSheet(
        title = stringResource(R.string.tag_delete_title, form.name),
        body = stringResource(R.string.tag_delete_body),
        confirmLabel = stringResource(R.string.delete),
        dismissLabel = stringResource(R.string.keep),
        onConfirm = viewModel::onDeleteConfirm,
        onDismiss = viewModel::onDeleteDismiss
    )
}
