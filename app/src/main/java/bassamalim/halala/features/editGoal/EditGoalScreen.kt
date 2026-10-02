package bassamalim.halala.features.editGoal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.ui.components.ChipStyle
import bassamalim.halala.core.ui.components.ConfirmSheet
import bassamalim.halala.core.ui.components.DateDialog
import bassamalim.halala.core.ui.components.FormField
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.HalalaChip
import bassamalim.halala.core.ui.components.HalalaTextField
import bassamalim.halala.core.ui.components.ListCard
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Spacing

/** Adding or changing a savings goal: its name, target, date and the accounts it is saved in. No board draws it. */
@Composable
fun EditGoalScreen(viewModel: EditGoalViewModel = hiltViewModel()) {
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
            title = stringResource(if (state.isNew) R.string.goal_new else R.string.goal),
            onBack = viewModel::onBackClick,
            actionLabel = stringResource(R.string.save),
            actionEnabled = !state.isLoading,
            onAction = viewModel::onSaveClick
        )
        if (state.isLoading) return@Column

        FormField(
            label = stringResource(R.string.recurring_name),
            error = stringResource(R.string.merchant_name_missing).takeIf { GoalProblem.NameMissing in state.problems }
        ) {
            HalalaTextField(
                value = form.name,
                onValueChange = viewModel::onNameChange,
                isError = GoalProblem.NameMissing in state.problems,
                capitalization = KeyboardCapitalization.Words
            )
        }
        FormField(
            label = stringResource(R.string.goal_target),
            error = stringResource(R.string.amount_invalid).takeIf { GoalProblem.TargetInvalid in state.problems }
        ) {
            HalalaTextField(
                value = form.target,
                onValueChange = viewModel::onTargetChange,
                numeric = true,
                isError = GoalProblem.TargetInvalid in state.problems
            )
        }
        ListCard(Modifier.fillMaxWidth()) {
            ListRow(
                title = stringResource(R.string.goal_by),
                subtitle = state.dateLabel ?: stringResource(R.string.goal_no_date),
                onClick = viewModel::onDateClick
            )
        }
        if (form.targetDate != null) HalalaButton(
            text = stringResource(R.string.goal_clear_date),
            onClick = viewModel::onDateClear,
            modifier = Modifier.fillMaxWidth()
        )
        FormField(
            label = stringResource(R.string.goal_accounts),
            hint = stringResource(R.string.goal_accounts_hint),
            error = stringResource(R.string.goal_accounts_missing).takeIf { GoalProblem.AccountsMissing in state.problems }
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                state.accounts.forEach { account ->
                    HalalaChip(
                        label = account.label,
                        style = if (account.id in form.accountIds) ChipStyle.On else ChipStyle.Outline,
                        onClick = { viewModel.onAccountClick(account.id) }
                    )
                }
            }
        }
        if (!state.isNew) HalalaButton(
            text = stringResource(R.string.delete),
            onClick = viewModel::onDeleteClick,
            destructive = true,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (state.pickingDate) DateDialog(date = state.pickFrom, onPicked = viewModel::onDatePicked, onDismiss = viewModel::onDateDismiss)
    if (state.isConfirmingDelete) ConfirmSheet(
        title = stringResource(R.string.goal_delete_title, form.name),
        body = stringResource(R.string.goal_delete_body),
        confirmLabel = stringResource(R.string.delete),
        dismissLabel = stringResource(R.string.keep),
        onConfirm = viewModel::onDeleteConfirm,
        onDismiss = viewModel::onDeleteDismiss
    )
}
