package bassamalim.halala.features.editTransaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.ui.components.ChoiceChips
import bassamalim.halala.core.ui.components.FormField
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.HalalaTextField
import bassamalim.halala.core.ui.components.SegmentedControl
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.kindLabel
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Quick-add and edit. No board draws this form; it is built from the system's pieces: the
 * segmented control for which way the money went, the amount set large in Plex Mono, choice
 * chips for account and kind, and plain fields for the rest.
 */
@Composable
fun EditTransactionScreen(viewModel: EditTransactionViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    EditTransactionContent(
        state = state,
        onBack = viewModel::onBackClick,
        onSave = viewModel::onSaveClick,
        onModeClick = viewModel::onModeClick,
        onAmountChange = viewModel::onAmountChange,
        onAccountClick = viewModel::onAccountClick,
        onToAccountClick = viewModel::onToAccountClick,
        onKindClick = viewModel::onKindClick,
        onTitleChange = viewModel::onTitleChange,
        onNoteChange = viewModel::onNoteChange,
        onDateClick = viewModel::onDateClick,
        onTimeClick = viewModel::onTimeClick
    )

    if (state.isPickingDate)
        DateDialog(state.form.date, onPicked = viewModel::onDatePicked, onDismiss = viewModel::onPickerDismiss)

    if (state.isPickingTime)
        TimeDialog(state.form.time, onPicked = viewModel::onTimePicked, onDismiss = viewModel::onPickerDismiss)
}

@Composable
private fun EditTransactionContent(
    state: EditTransactionUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onModeClick: (EntryMode) -> Unit,
    onAmountChange: (String) -> Unit,
    onAccountClick: (Long) -> Unit,
    onToAccountClick: (Long) -> Unit,
    onKindClick: (TransactionKind) -> Unit,
    onTitleChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit
) {
    val form = state.form
    val problems = state.problems
    val isMove = form.mode == EntryMode.MOVE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.section),
        verticalArrangement = Arrangement.spacedBy(Spacing.section)
    ) {
        TopBar(
            title = stringResource(if (state.isNew) R.string.transaction_new else R.string.transaction_edit),
            onBack = onBack,
            actionLabel = stringResource(R.string.save),
            actionEnabled = !state.isLoading,
            onAction = onSave
        )

        if (state.isLoading) return@Column

        if (state.modes.size > 1) {
            SegmentedControl(
                options = state.modes.map { stringResource(it.label) },
                selectedIndex = state.modes.indexOf(form.mode),
                onSelect = { onModeClick(state.modes[it]) },
                fill = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        FormField(
            label = stringResource(R.string.transaction_amount),
            error = stringResource(R.string.amount_invalid).takeIf { TransactionProblem.AmountInvalid in problems }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                HalalaTextField(
                    value = form.amount,
                    onValueChange = onAmountChange,
                    placeholder = "0.00",
                    numeric = true,
                    textStyle = HalalaNumbers.AmountXl,
                    isError = TransactionProblem.AmountInvalid in problems,
                    modifier = Modifier.weight(1f)
                )
                Text(text = state.currency, style = HalalaType.Title, color = HalalaColors.TextMuted)
            }
        }

        FormField(
            label = stringResource(if (isMove) R.string.transaction_from else R.string.transaction_account),
            error = stringResource(R.string.transaction_account_missing)
                .takeIf { TransactionProblem.AccountMissing in problems && form.accountId == null }
        ) {
            ChoiceChips(
                options = state.accounts,
                selected = state.accounts.firstOrNull { it.id == form.accountId },
                label = { it.label },
                onSelect = { onAccountClick(it.id) }
            )
        }

        if (isMove) {
            FormField(
                label = stringResource(R.string.transaction_to),
                error = when {
                    TransactionProblem.SameAccount in problems -> stringResource(R.string.transaction_same_account)
                    TransactionProblem.CurrencyMismatch in problems -> stringResource(R.string.transaction_currency_mismatch)
                    TransactionProblem.AccountMissing in problems && form.toAccountId == null ->
                        stringResource(R.string.transaction_account_missing)
                    else -> null
                },
                hint = stringResource(R.string.transaction_move_hint)
            ) {
                ChoiceChips(
                    options = state.accounts,
                    selected = state.accounts.firstOrNull { it.id == form.toAccountId },
                    label = { it.label },
                    onSelect = { onToAccountClick(it.id) }
                )
            }
        }
        else {
            FormField(label = stringResource(R.string.transaction_kind)) {
                ChoiceChips(
                    options = state.kinds,
                    selected = form.kind,
                    label = { kindLabel(it) },
                    onSelect = onKindClick
                )
            }
        }

        FormField(label = stringResource(if (isMove) R.string.transaction_title_move else R.string.transaction_title)) {
            HalalaTextField(
                value = form.title,
                onValueChange = onTitleChange,
                placeholder = stringResource(R.string.optional)
            )
        }

        FormField(label = stringResource(R.string.transaction_when)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                HalalaButton(text = state.dateLabel, onClick = onDateClick, modifier = Modifier.weight(1f))
                HalalaButton(text = state.timeLabel, onClick = onTimeClick)
            }
        }

        FormField(label = stringResource(R.string.transaction_note)) {
            HalalaTextField(
                value = form.note,
                onValueChange = onNoteChange,
                placeholder = stringResource(R.string.optional),
                singleLine = false
            )
        }
    }
}

private val EntryMode.label
    get() = when (this) {
        EntryMode.OUT -> R.string.mode_out
        EntryMode.IN -> R.string.mode_in
        EntryMode.MOVE -> R.string.mode_move
    }

/**
 * Material's date picker speaks UTC midnights; the conversion to and from a [LocalDate] happens
 * here and nowhere else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(date: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = pickerState.selectedDateMillis
                if (millis == null) onDismiss()
                else onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
            }) { Text(stringResource(R.string.done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    ) {
        DatePicker(state = pickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(time: LocalTime, onPicked: (LocalTime) -> Unit, onDismiss: () -> Unit) {
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
