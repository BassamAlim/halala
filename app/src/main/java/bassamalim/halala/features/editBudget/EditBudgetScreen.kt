package bassamalim.halala.features.editBudget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.enums.BudgetScope
import bassamalim.halala.core.enums.ExpenseType
import bassamalim.halala.core.ui.components.ChoiceChips
import bassamalim.halala.core.ui.components.ConfirmSheet
import bassamalim.halala.core.ui.components.FormField
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.HalalaTextField
import bassamalim.halala.core.ui.components.SearchField
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.expenseTypeLabel
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Spacing

/**
 * Adding or changing a budget: what it limits (everything, a category, an expense type or a
 * merchant), how much each pay cycle, and whether what is left rolls over. No board draws it.
 */
@Composable
fun EditBudgetScreen(viewModel: EditBudgetViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val form = state.form
    val choiceMissing = stringResource(R.string.budget_choice_missing).takeIf { BudgetProblem.ChoiceMissing in state.problems }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.section),
        verticalArrangement = Arrangement.spacedBy(Spacing.section)
    ) {
        TopBar(
            title = stringResource(if (state.isNew) R.string.budget_new else R.string.budget),
            onBack = viewModel::onBackClick,
            actionLabel = stringResource(R.string.save),
            actionEnabled = !state.isLoading,
            onAction = viewModel::onSaveClick
        )
        if (state.isLoading) return@Column

        FormField(label = stringResource(R.string.budget_for)) {
            ChoiceChips(
                options = BudgetScope.entries,
                selected = form.scope,
                label = {
                    stringResource(
                        when (it) {
                            BudgetScope.TOTAL -> R.string.budget_everything
                            BudgetScope.CATEGORY -> R.string.category
                            BudgetScope.EXPENSE_TYPE -> R.string.expense_type
                            BudgetScope.MERCHANT -> R.string.merchant
                            BudgetScope.TAG -> R.string.budget_tag
                        }
                    )
                },
                onSelect = viewModel::onScopeClick
            )
        }

        when (form.scope) {
            BudgetScope.TOTAL -> Unit
            BudgetScope.CATEGORY -> FormField(label = stringResource(R.string.category), error = choiceMissing) {
                ChoiceChips(
                    options = state.categories,
                    selected = state.categories.firstOrNull { it.id == form.categoryId },
                    label = { it.name },
                    onSelect = viewModel::onCategoryClick
                )
            }
            BudgetScope.EXPENSE_TYPE -> FormField(label = stringResource(R.string.expense_type), error = choiceMissing) {
                ChoiceChips(
                    options = ExpenseType.entries,
                    selected = form.expenseType,
                    label = { expenseTypeLabel(it) },
                    onSelect = viewModel::onTypeClick
                )
            }
            BudgetScope.MERCHANT -> FormField(label = stringResource(R.string.merchant), error = choiceMissing) {
                SearchField(
                    value = state.merchantQuery,
                    onValueChange = viewModel::onMerchantQueryChange,
                    placeholder = stringResource(R.string.merchants_search),
                    modifier = Modifier.fillMaxWidth()
                )
                ChoiceChips(
                    options = state.merchants,
                    selected = state.merchants.firstOrNull { it.id == form.merchantId },
                    label = { it.name },
                    onSelect = viewModel::onMerchantClick
                )
            }
            BudgetScope.TAG -> FormField(
                label = stringResource(R.string.budget_tag),
                hint = stringResource(R.string.budget_tag_none).takeIf { state.tags.isEmpty() },
                error = choiceMissing
            ) {
                ChoiceChips(
                    options = state.tags,
                    selected = state.tags.firstOrNull { it.id == form.tagId },
                    label = { it.name },
                    onSelect = viewModel::onTagClick
                )
            }
        }

        FormField(
            label = stringResource(R.string.budget_amount),
            hint = stringResource(R.string.budget_amount_hint),
            error = stringResource(R.string.amount_invalid).takeIf { BudgetProblem.AmountInvalid in state.problems }
        ) {
            HalalaTextField(
                value = form.amount,
                onValueChange = viewModel::onAmountChange,
                numeric = true,
                isError = BudgetProblem.AmountInvalid in state.problems,
                imeAction = ImeAction.Done
            )
        }

        FormField(label = stringResource(R.string.budget_rollover), hint = stringResource(R.string.budget_rollover_hint)) {
            ChoiceChips(
                options = listOf(true, false),
                selected = form.rollover,
                label = { stringResource(if (it) R.string.yes else R.string.no) },
                onSelect = viewModel::onRolloverClick
            )
        }

        if (!state.isNew) HalalaButton(
            text = stringResource(R.string.delete),
            onClick = viewModel::onDeleteClick,
            destructive = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text(text = stringResource(R.string.budgets_hint), style = HalalaType.Caption, color = HalalaColors.TextMuted)
    }

    if (state.isConfirmingDelete) ConfirmSheet(
        title = stringResource(R.string.budget_delete_title),
        body = stringResource(R.string.budget_delete_body),
        confirmLabel = stringResource(R.string.delete),
        dismissLabel = stringResource(R.string.keep),
        onConfirm = viewModel::onDeleteConfirm,
        onDismiss = viewModel::onDeleteDismiss
    )
}
