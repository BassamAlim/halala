package bassamalim.halala.features.savings

import bassamalim.halala.core.ui.components.Skeleton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.Globals
import bassamalim.halala.core.enums.MaturityChoice
import bassamalim.halala.core.enums.SavingsKind
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
import bassamalim.halala.core.ui.components.ProgressBar
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.components.appendCurrency
import bassamalim.halala.core.ui.components.currencyInlineContent
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Spacing

/**
 * The Savings board: what is in savings, each Awaeed term (locked until, how far, the profit
 * expected, what happens at maturity, the goal it is for; one card per deposit) and Hasad (the
 * month's lowest balance, next month's profit on it). One with no terms yet asks for them.
 */
@Composable
fun SavingsScreen(viewModel: SavingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.section),
        verticalArrangement = Arrangement.spacedBy(Spacing.card)
    ) {
        TopBar(title = stringResource(R.string.wealth_savings), onBack = viewModel::onBackClick)
        if (state.isLoading) {
            Skeleton()
            return@Column
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = stringResource(R.string.savings_in), style = HalalaType.Label, color = HalalaColors.TextMuted)
            Text(
                text = buildAnnotatedString {
                    append(state.total)
                    appendCurrency(Globals.PRIMARY_CURRENCY)
                },
                style = HalalaNumbers.AmountXl,
                inlineContent = currencyInlineContent(HalalaColors.TextMuted)
            )
        }
        if (state.cards.isEmpty()) Text(text = stringResource(R.string.savings_empty), style = HalalaType.Body, color = HalalaColors.TextMuted)

        for ((kind, label) in listOf(SavingsKind.AWAEED to R.string.savings_awaeed, SavingsKind.HASAD to R.string.savings_hasad, null to R.string.savings_other)) {
            val cards = state.cards.filter { it.kind == kind }
            if (cards.isEmpty()) continue
            GroupLabel(stringResource(label))
            cards.forEach { card ->
                HalalaCard(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.onCardClick(card) }, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = card.tenorMonths?.takeIf { kind == SavingsKind.AWAEED }?.let { pluralStringResource(R.plurals.savings_months, it, it) }
                                ?: card.name.ifEmpty { stringResource(R.string.savings_awaeed) },
                            style = HalalaType.BodyStrong
                        )
                        card.rate?.let { Text(text = stringResource(R.string.savings_rate, it), style = HalalaType.Caption, color = HalalaColors.TextMuted) }
                    }
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(text = card.balance, style = HalalaNumbers.AmountLg)
                        Text(
                            text = card.maturity?.let { stringResource(R.string.savings_locked_until, it) } ?: stringResource(R.string.savings_balance),
                            style = HalalaType.Caption,
                            color = HalalaColors.TextMuted
                        )
                    }
                    when (kind) {
                        SavingsKind.AWAEED -> {
                            ProgressBar(progress = card.progress)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                card.started?.let { Text(text = stringResource(R.string.savings_started, it), style = HalalaType.Caption, color = HalalaColors.TextMuted) }
                                card.profit?.let { Text(text = stringResource(R.string.savings_profit_about, it), style = HalalaType.Caption, color = HalalaColors.Income) }
                            }
                            card.choice?.let { Text(text = stringResource(R.string.savings_at_maturity, stringResource(choiceLabel(it))), style = HalalaType.Label, color = HalalaColors.TextMuted) }
                            card.goal?.let { Text(text = stringResource(R.string.deposit_for_goal, it), style = HalalaType.Label, color = HalalaColors.TextMuted) }
                            if (card.depositId != null && (card.rate == null || card.tenorMonths == null))
                                Text(text = stringResource(R.string.savings_set_terms), style = HalalaType.Label, color = HalalaColors.Accent)
                        }
                        SavingsKind.HASAD -> {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = stringResource(R.string.savings_lowest), style = HalalaType.Label, color = HalalaColors.TextMuted)
                                Text(text = card.lowest.orEmpty(), style = HalalaNumbers.Meta)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = stringResource(R.string.savings_expected, card.profitOn.orEmpty()), style = HalalaType.Label, color = HalalaColors.TextMuted)
                                Text(text = card.profit.orEmpty(), style = HalalaNumbers.Meta, color = HalalaColors.Income)
                            }
                            Text(text = stringResource(R.string.savings_hasad_note), style = HalalaType.Caption, color = HalalaColors.Info)
                        }
                        null -> Text(text = stringResource(R.string.savings_set_terms), style = HalalaType.Label, color = HalalaColors.Accent)
                    }
                }
            }
        }
    }
}

fun choiceLabel(choice: MaturityChoice) = when (choice) {
    MaturityChoice.PAY_OUT -> R.string.savings_pay_out
    MaturityChoice.RENEW_PRINCIPAL -> R.string.savings_renew_principal
    MaturityChoice.RENEW_WITH_PROFIT -> R.string.savings_renew_profit
}

/** The terms of one savings account: Awaeed or Hasad, its rate, and a term's start, months and maturity. No board draws it. */
@Composable
fun SavingsTermsScreen(viewModel: SavingsTermsViewModel = hiltViewModel()) {
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
            title = state.name,
            onBack = viewModel::onBackClick,
            actionLabel = stringResource(R.string.save),
            actionEnabled = !state.isLoading,
            onAction = viewModel::onSaveClick
        )
        if (state.isLoading) {
            Skeleton()
            return@Column
        }

        FormField(label = stringResource(R.string.recurring_kind)) {
            ChoiceChips(
                options = SavingsKind.entries,
                selected = form.kind,
                label = { stringResource(if (it == SavingsKind.AWAEED) R.string.savings_awaeed else R.string.savings_hasad) },
                onSelect = viewModel::onKindClick
            )
        }
        FormField(
            label = stringResource(R.string.savings_rate_label),
            hint = stringResource(R.string.savings_rate_hint),
            error = stringResource(R.string.asset_percent_invalid).takeIf { state.rateInvalid }
        ) {
            HalalaTextField(value = form.rate, onValueChange = viewModel::onRateChange, numeric = true, isError = state.rateInvalid)
        }
        if (form.kind == SavingsKind.AWAEED) {
            ListCard(Modifier.fillMaxWidth()) {
                ListRow(title = stringResource(R.string.savings_term_started), subtitle = state.startLabel, onClick = viewModel::onStartClick)
            }
            FormField(label = stringResource(R.string.savings_tenor)) {
                ChoiceChips(
                    options = SavingsTermsViewModel.TENORS,
                    selected = form.tenorMonths,
                    label = { pluralStringResource(R.plurals.savings_months, it, it) },
                    onSelect = viewModel::onTenorClick
                )
            }
            FormField(label = stringResource(R.string.savings_maturity)) {
                ChoiceChips(
                    options = MaturityChoice.entries,
                    selected = form.choice,
                    label = { stringResource(choiceLabel(it)) },
                    onSelect = viewModel::onChoiceClick
                )
            }
        }
        HalalaButton(stringResource(R.string.save), viewModel::onSaveClick, Modifier.fillMaxWidth(), kind = ButtonKind.Primary)
        if (state.hasTerms) HalalaButton(stringResource(R.string.savings_clear), viewModel::onClearClick, Modifier.fillMaxWidth(), destructive = true)
    }

    if (state.picking) DateDialog(date = state.pickFrom, onPicked = viewModel::onStartPicked, onDismiss = viewModel::onStartDismiss)
}

/**
 * One term deposit: what went in and when (its transfer's, so not edited here), the terms the
 * SMS doesn't give, the goal it is for, and paying it out. No board draws it.
 */
@Composable
fun DepositScreen(viewModel: DepositViewModel = hiltViewModel()) {
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
            title = stringResource(R.string.deposit_title),
            onBack = viewModel::onBackClick,
            actionLabel = stringResource(R.string.save),
            actionEnabled = !state.isLoading,
            onAction = viewModel::onSaveClick
        )
        if (state.isLoading) {
            Skeleton()
            return@Column
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = state.amount, style = HalalaNumbers.AmountXl)
            Text(text = stringResource(R.string.savings_started, state.started), style = HalalaType.Label, color = HalalaColors.TextMuted)
        }
        FormField(label = stringResource(R.string.deposit_for), hint = stringResource(R.string.deposit_for_hint)) {
            ChoiceChips(
                options = state.goals,
                selected = state.goals.firstOrNull { it.first == form.goalId },
                label = { it.second.ifEmpty { stringResource(R.string.deposit_no_goal) } },
                onSelect = { viewModel.onGoalClick(it.first) }
            )
        }
        HalalaButton(stringResource(R.string.goal_add), viewModel::onNewGoalClick, Modifier.fillMaxWidth())
        FormField(
            label = stringResource(R.string.savings_rate_label),
            error = stringResource(R.string.asset_percent_invalid).takeIf { state.rateInvalid }
        ) {
            HalalaTextField(value = form.rate, onValueChange = viewModel::onRateChange, numeric = true, isError = state.rateInvalid)
        }
        FormField(label = stringResource(R.string.savings_tenor)) {
            ChoiceChips(
                options = SavingsTermsViewModel.TENORS,
                selected = form.tenorMonths,
                label = { pluralStringResource(R.plurals.savings_months, it, it) },
                onSelect = viewModel::onTenorClick
            )
        }
        FormField(label = stringResource(R.string.savings_maturity)) {
            ChoiceChips(
                options = MaturityChoice.entries,
                selected = form.choice,
                label = { stringResource(choiceLabel(it)) },
                onSelect = viewModel::onChoiceClick
            )
        }
        HalalaButton(stringResource(R.string.save), viewModel::onSaveClick, Modifier.fillMaxWidth(), kind = ButtonKind.Primary)
        HalalaButton(stringResource(R.string.deposit_pay_out), viewModel::onPayOutClick, Modifier.fillMaxWidth())
    }

    if (state.confirmingPayOut) ConfirmSheet(
        title = stringResource(R.string.deposit_pay_out_title),
        body = stringResource(R.string.deposit_pay_out_body),
        confirmLabel = stringResource(R.string.deposit_pay_out),
        dismissLabel = stringResource(R.string.cancel),
        onConfirm = viewModel::onPayOutConfirm,
        onDismiss = viewModel::onPayOutDismiss,
        destructive = false
    )
}
