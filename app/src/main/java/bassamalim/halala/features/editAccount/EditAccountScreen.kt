package bassamalim.halala.features.editAccount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.ui.accountTypeLabel
import bassamalim.halala.core.ui.components.ChoiceChips
import bassamalim.halala.core.ui.components.FormField
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.HalalaTextField
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Spacing

/**
 * Adding or editing an account. No board draws this form, so it follows the onboarding board's
 * naming rows (bank, ••digits, a name you'll recognise) laid out as the app's other forms.
 */
@Composable
fun EditAccountScreen(viewModel: EditAccountViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    EditAccountContent(
        state = state,
        onBack = viewModel::onBackClick,
        onSave = viewModel::onSaveClick,
        onBankClick = viewModel::onBankClick,
        onTypeClick = viewModel::onTypeClick,
        onNameChange = viewModel::onNameChange,
        onLast4Change = viewModel::onLast4Change,
        onCurrencyClick = viewModel::onCurrencyClick,
        onCurrencyChange = viewModel::onCurrencyChange,
        onOpeningBalanceChange = viewModel::onOpeningBalanceChange,
        onArchiveClick = viewModel::onArchiveClick,
        onCountCashClick = viewModel::onCountCashClick
    )
}

@Composable
private fun EditAccountContent(
    state: EditAccountUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onBankClick: (Long) -> Unit,
    onTypeClick: (AccountType) -> Unit,
    onNameChange: (String) -> Unit,
    onLast4Change: (String) -> Unit,
    onCurrencyClick: (String?) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onOpeningBalanceChange: (String) -> Unit,
    onArchiveClick: () -> Unit,
    onCountCashClick: () -> Unit
) {
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
            title = stringResource(if (state.isNew) R.string.account_new else R.string.account),
            onBack = onBack,
            actionLabel = stringResource(R.string.save),
            actionEnabled = !state.isLoading,
            onAction = onSave
        )

        if (state.isLoading) return@Column

        if (!state.isCash) {
            FormField(
                label = stringResource(R.string.account_bank),
                error = stringResource(R.string.account_bank_missing).takeIf { AccountProblem.BankMissing in problems }
            ) {
                ChoiceChips(
                    options = state.banks,
                    selected = state.banks.firstOrNull { it.id == form.institutionId },
                    label = { it.name },
                    onSelect = { onBankClick(it.id) }
                )
            }

            FormField(label = stringResource(R.string.account_type)) {
                ChoiceChips(
                    options = state.types,
                    selected = form.type,
                    label = { accountTypeLabel(it) },
                    onSelect = onTypeClick
                )
            }
        }

        FormField(
            label = stringResource(R.string.account_name),
            hint = stringResource(R.string.account_name_hint),
            error = stringResource(R.string.account_name_missing).takeIf { AccountProblem.NameMissing in problems }
        ) {
            HalalaTextField(
                value = form.name,
                onValueChange = onNameChange,
                placeholder = stringResource(R.string.account_name_placeholder),
                isError = AccountProblem.NameMissing in problems
            )
        }

        if (!state.isCash) {
            val taken = problems.filterIsInstance<AccountProblem.Last4Taken>().firstOrNull()

            FormField(
                label = stringResource(R.string.account_last4),
                hint = stringResource(R.string.account_last4_hint),
                error = when {
                    taken != null -> stringResource(R.string.account_last4_taken, taken.name)
                    AccountProblem.Last4Invalid in problems -> stringResource(R.string.account_last4_invalid)
                    else -> null
                }
            ) {
                HalalaTextField(
                    value = form.last4,
                    onValueChange = onLast4Change,
                    placeholder = "0000",
                    numeric = true,
                    keyboardType = KeyboardType.NumberPassword,
                    isError = taken != null || AccountProblem.Last4Invalid in problems
                )
            }
        }

        FormField(
            label = stringResource(R.string.account_currency),
            hint = stringResource(R.string.account_currency_locked).takeIf { state.currencyLocked },
            error = stringResource(R.string.account_currency_invalid).takeIf { AccountProblem.CurrencyInvalid in problems }
        ) {
            ChoiceChips(
                options = EditAccountUiState.CURRENCY_CHOICES + listOf<String?>(null),
                selected = state.currencyChoice,
                label = { it ?: stringResource(R.string.account_currency_other) },
                onSelect = onCurrencyClick,
                enabled = !state.currencyLocked
            )

            if (state.currencyChoice == null && !state.currencyLocked) {
                HalalaTextField(
                    value = form.currency,
                    onValueChange = onCurrencyChange,
                    placeholder = stringResource(R.string.account_currency_placeholder),
                    isError = AccountProblem.CurrencyInvalid in problems
                )
            }
        }

        FormField(
            label = stringResource(R.string.account_opening_balance),
            hint = stringResource(R.string.account_opening_balance_hint),
            error = stringResource(R.string.amount_invalid).takeIf { AccountProblem.OpeningBalanceInvalid in problems }
        ) {
            HalalaTextField(
                value = form.openingBalance,
                onValueChange = onOpeningBalanceChange,
                placeholder = "0.00",
                numeric = true,
                isError = AccountProblem.OpeningBalanceInvalid in problems
            )
        }

        if (!state.isNew) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (state.isCash) {
                    HalalaButton(
                        text = stringResource(R.string.reconcile_title),
                        onClick = onCountCashClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HalalaButton(
                    text = stringResource(if (state.isArchived) R.string.account_unarchive else R.string.account_archive),
                    onClick = onArchiveClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

