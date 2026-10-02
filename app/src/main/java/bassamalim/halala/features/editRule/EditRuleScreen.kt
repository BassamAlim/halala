package bassamalim.halala.features.editRule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.enums.ExpenseType
import bassamalim.halala.core.ui.components.ChoiceChips
import bassamalim.halala.core.ui.components.FormField
import bassamalim.halala.core.ui.components.GroupLabel
import bassamalim.halala.core.ui.components.HalalaTextField
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.expenseTypeLabel
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Spacing

/**
 * Writing a rule by hand, or editing one: when all of these hold, file it under that. No board
 * draws this form, so it is laid out as the app's other forms.
 */
@Composable
fun EditRuleScreen(viewModel: EditRuleViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val form = state.form
    val problems = state.problems

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.section),
        verticalArrangement = Arrangement.spacedBy(Spacing.section)
    ) {
        TopBar(
            title = stringResource(if (state.isNew) R.string.rule_new else R.string.edit_rule),
            onBack = viewModel::onBackClick,
            actionLabel = stringResource(R.string.save),
            actionEnabled = !state.isLoading,
            onAction = viewModel::onSaveClick
        )

        if (state.isLoading) return@Column

        GroupLabel(stringResource(R.string.rule_when))

        FormField(
            label = stringResource(R.string.rule_merchant),
            hint = stringResource(R.string.rule_merchant_hint),
            error = stringResource(R.string.rule_condition_missing).takeIf { RuleProblem.ConditionMissing in problems }
        ) {
            HalalaTextField(
                value = form.merchant,
                onValueChange = viewModel::onMerchantChange,
                isError = RuleProblem.ConditionMissing in problems
            )
        }

        FormField(label = stringResource(R.string.rule_contains), hint = stringResource(R.string.rule_contains_hint)) {
            HalalaTextField(value = form.contains, onValueChange = viewModel::onContainsChange)
        }

        if (state.accounts.isNotEmpty()) {
            FormField(
                label = stringResource(R.string.transaction_account),
                hint = stringResource(R.string.rule_account_hint)
            ) {
                ChoiceChips(
                    options = state.accounts,
                    selected = state.accounts.firstOrNull { it.id == form.accountId },
                    label = { it.label },
                    onSelect = { viewModel.onAccountClick(it.id) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FormField(
                label = stringResource(R.string.rule_amount_from),
                modifier = Modifier.weight(1f),
                hint = stringResource(R.string.optional),
                error = when {
                    RuleProblem.AmountInvalid in problems -> stringResource(R.string.amount_invalid)
                    RuleProblem.RangeInverted in problems -> stringResource(R.string.rule_range_inverted)
                    else -> null
                }
            ) {
                HalalaTextField(
                    value = form.min,
                    onValueChange = viewModel::onMinChange,
                    numeric = true,
                    isError = RuleProblem.AmountInvalid in problems || RuleProblem.RangeInverted in problems
                )
            }
            FormField(
                label = stringResource(R.string.rule_amount_to),
                modifier = Modifier.weight(1f),
                hint = stringResource(R.string.optional)
            ) {
                HalalaTextField(
                    value = form.max,
                    onValueChange = viewModel::onMaxChange,
                    numeric = true,
                    isError = RuleProblem.AmountInvalid in problems || RuleProblem.RangeInverted in problems,
                    imeAction = ImeAction.Done
                )
            }
        }

        GroupLabel(stringResource(R.string.rule_then))

        FormField(
            label = stringResource(R.string.category),
            error = stringResource(R.string.rule_category_missing).takeIf { RuleProblem.CategoryMissing in problems }
        ) {
            ChoiceChips(
                options = state.categories,
                selected = state.categories.firstOrNull { it.id == form.categoryId },
                label = { it.name },
                onSelect = { viewModel.onCategoryClick(it.id) }
            )
        }

        FormField(label = stringResource(R.string.expense_type), hint = stringResource(R.string.optional)) {
            ChoiceChips(
                options = ExpenseType.entries,
                selected = form.expenseType,
                label = { expenseTypeLabel(it) },
                onSelect = viewModel::onTypeClick
            )
        }
    }
}
