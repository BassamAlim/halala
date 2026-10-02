package bassamalim.halala.features.editRecurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.enums.CadenceUnit
import bassamalim.halala.core.enums.RecurringKind
import bassamalim.halala.core.ui.components.ChoiceChips
import bassamalim.halala.core.ui.components.ConfirmSheet
import bassamalim.halala.core.ui.components.DateDialog
import bassamalim.halala.core.ui.components.FormField
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.HalalaTextField
import bassamalim.halala.core.ui.components.ListCard
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.components.rememberNotificationAsk
import bassamalim.halala.core.ui.recurringKindLabel
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * Adding or changing a subscription, bill or planned payment: what it is, how much and how
 * often, when it is next due, whether it renews, when a contract ends and when to remind you.
 * No board draws this form: it is built from the system's fields, chips and list rows.
 */
@Composable
fun EditRecurringScreen(viewModel: EditRecurringViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val form = state.form
    val problems = state.problems
    val askToNotify = rememberNotificationAsk()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.section),
        verticalArrangement = Arrangement.spacedBy(Spacing.section)
    ) {
        TopBar(
            title = stringResource(if (state.isNew) R.string.recurring_new else R.string.recurring_edit),
            onBack = viewModel::onBackClick,
            actionLabel = stringResource(R.string.save),
            actionEnabled = !state.isLoading,
            onAction = viewModel::onSaveClick
        )

        if (state.isLoading) return@Column

        FormField(
            label = stringResource(R.string.recurring_name),
            error = stringResource(R.string.merchant_name_missing).takeIf { SeriesProblem.NameMissing in problems }
        ) {
            HalalaTextField(
                value = form.name,
                onValueChange = viewModel::onNameChange,
                isError = SeriesProblem.NameMissing in problems,
                capitalization = KeyboardCapitalization.Words
            )
        }

        FormField(label = stringResource(R.string.recurring_kind)) {
            ChoiceChips(
                options = RecurringKind.entries,
                selected = form.kind,
                label = { recurringKindLabel(it) },
                onSelect = viewModel::onKindClick
            )
        }

        FormField(
            label = stringResource(R.string.transaction_amount),
            error = stringResource(R.string.amount_invalid).takeIf { SeriesProblem.AmountInvalid in problems }
        ) {
            HalalaTextField(
                value = form.amount,
                onValueChange = viewModel::onAmountChange,
                numeric = true,
                isError = SeriesProblem.AmountInvalid in problems
            )
        }

        FormField(
            label = stringResource(R.string.recurring_every),
            error = stringResource(R.string.recurring_every_invalid).takeIf { SeriesProblem.EveryInvalid in problems }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                HalalaTextField(
                    value = form.every,
                    onValueChange = viewModel::onEveryChange,
                    numeric = true,
                    keyboardType = KeyboardType.Number,
                    isError = SeriesProblem.EveryInvalid in problems,
                    imeAction = ImeAction.Done,
                    modifier = Modifier.width(Sizes.fab)
                )
                val count = form.every.toIntOrNull() ?: 1
                ChoiceChips(
                    options = CadenceUnit.entries,
                    selected = form.unit,
                    label = { unitLabel(it, count) },
                    onSelect = viewModel::onUnitClick
                )
            }
        }

        ListCard(Modifier.fillMaxWidth()) {
            ListRow(
                title = stringResource(R.string.recurring_next_due),
                subtitle = state.dueLabel,
                onClick = viewModel::onDueClick
            )
            ListRow(
                title = stringResource(R.string.recurring_ends_on),
                subtitle = state.endsLabel ?: stringResource(R.string.recurring_runs_on),
                divider = true,
                onClick = viewModel::onEndsClick
            )
        }
        if (SeriesProblem.EndsBeforeDue in problems) {
            Text(
                text = stringResource(R.string.recurring_ends_before_due),
                style = HalalaType.Label,
                color = HalalaColors.StateOver
            )
        }
        if (form.endsOn != null) HalalaButton(
            text = stringResource(R.string.recurring_no_end),
            onClick = viewModel::onEndsClear,
            modifier = Modifier.fillMaxWidth()
        )

        if (form.kind == RecurringKind.SUBSCRIPTION) FormField(label = stringResource(R.string.recurring_auto_renew)) {
            ChoiceChips(
                options = listOf(true, false),
                selected = form.autoRenew,
                label = { stringResource(if (it) R.string.yes else R.string.no) },
                onSelect = viewModel::onAutoRenewClick
            )
        }

        FormField(label = stringResource(R.string.recurring_reminder), hint = stringResource(R.string.optional)) {
            ChoiceChips(
                options = EditRecurringDomain.REMINDERS,
                selected = form.reminderDays,
                label = { pluralStringResource(R.plurals.recurring_reminder_option, it, it) },
                onSelect = { days ->
                    if (form.reminderDays != days) askToNotify()
                    viewModel.onReminderClick(days)
                }
            )
        }

        if (!state.isNew) HalalaButton(
            text = stringResource(R.string.delete),
            onClick = viewModel::onDeleteClick,
            destructive = true,
            modifier = Modifier.fillMaxWidth()
        )
    }

    when (val sheet = state.sheet) {
        is EditRecurringSheet.PickDate -> DateDialog(
            date = sheet.date,
            onPicked = viewModel::onDatePicked,
            onDismiss = viewModel::onSheetDismiss
        )
        EditRecurringSheet.ConfirmDelete -> ConfirmSheet(
            title = stringResource(R.string.recurring_delete_title, form.name),
            body = stringResource(R.string.recurring_delete_body),
            confirmLabel = stringResource(R.string.delete),
            dismissLabel = stringResource(R.string.keep),
            onConfirm = viewModel::onDeleteConfirm,
            onDismiss = viewModel::onSheetDismiss
        )
        null -> Unit
    }
}

@Composable
private fun unitLabel(unit: CadenceUnit, count: Int): String = pluralStringResource(
    when (unit) {
        CadenceUnit.DAY -> R.plurals.unit_days
        CadenceUnit.WEEK -> R.plurals.unit_weeks
        CadenceUnit.MONTH -> R.plurals.unit_months
        CadenceUnit.YEAR -> R.plurals.unit_years
    },
    count
)
