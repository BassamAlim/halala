package bassamalim.halala.features.zakat

import bassamalim.halala.core.ui.components.Skeleton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.buildAnnotatedString
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.Globals
import bassamalim.halala.core.domain.WealthClass
import bassamalim.halala.core.ui.components.ButtonKind
import bassamalim.halala.core.ui.components.ChoiceChips
import bassamalim.halala.core.ui.components.FormField
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.HalalaCard
import bassamalim.halala.core.ui.components.HalalaSheet
import bassamalim.halala.core.ui.components.HalalaTextField
import bassamalim.halala.core.ui.components.ListCard
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.ui.components.ProgressBar
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.components.appendCurrency
import bassamalim.halala.core.ui.components.currencyInlineContent
import bassamalim.halala.core.ui.components.rememberNotificationAsk
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * The Zakat board: what is due and on which Hijri day, the hawl and today's nisab, what counts
 * (each part a choice, since opinions differ), debts due now, the method in plain words, and
 * the reminder and "Mark as paid".
 */
@Composable
fun ZakatScreen(viewModel: ZakatViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val months = stringArrayResource(R.array.hijri_months)
    val askToNotify = rememberNotificationAsk()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.section),
        verticalArrangement = Arrangement.spacedBy(Spacing.card)
    ) {
        TopBar(title = stringResource(R.string.zakat), onBack = viewModel::onBackClick)
        if (state.isLoading) {
            Skeleton()
            return@Column
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = stringResource(R.string.zakat_due), style = HalalaType.Label, color = HalalaColors.TextMuted)
            Text(
                text = buildAnnotatedString {
                    append(state.due ?: "—")
                    appendCurrency(Globals.PRIMARY_CURRENCY)
                },
                style = HalalaNumbers.AmountXl,
                inlineContent = currencyInlineContent(HalalaColors.TextMuted)
            )
            val method = when {
                state.due == null -> stringResource(R.string.zakat_needs_price)
                state.belowNisab -> stringResource(R.string.zakat_below_nisab, state.base)
                else -> stringResource(R.string.zakat_method, state.base)
            }
            val day = if (state.hijriMonth != null && state.gregorian != null)
                " " + stringResource(R.string.zakat_due_on, state.hijriDay!!, months[state.hijriMonth!! - 1], state.hijriYear!!, state.gregorian!!)
            else ""
            Text(text = method + day, style = HalalaType.Label, color = HalalaColors.TextMuted)
            if (state.paid) Text(text = stringResource(R.string.zakat_paid), style = HalalaType.Label, color = HalalaColors.Income)
        }

        HalalaCard(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (state.hawlDays > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = stringResource(R.string.zakat_hawl), style = HalalaType.Label, color = HalalaColors.TextMuted)
                    Text(text = stringResource(R.string.zakat_hawl_days, state.hawlElapsed, state.hawlDays), style = HalalaType.Label)
                }
                ProgressBar(progress = state.hawlElapsed.toFloat() / state.hawlDays)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = stringResource(R.string.zakat_nisab), style = HalalaType.Label, color = HalalaColors.TextMuted)
                Text(text = state.nisab ?: "—", style = HalalaNumbers.Meta)
            }
        }

        HalalaCard(modifier = Modifier.fillMaxWidth()) {
            state.parts.forEachIndexed { index, part ->
                if (index > 0) HorizontalDivider(thickness = Sizes.border, color = HalalaColors.Line)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Sizes.touchTarget)
                        .clickable(role = Role.Checkbox) { viewModel.onToggle(part.kind) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Checkbox(
                        checked = part.included,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = HalalaColors.Accent, checkmarkColor = HalalaColors.OnAccent, uncheckedColor = HalalaColors.TextMuted)
                    )
                    Text(text = stringResource(partLabel(part.kind)), style = HalalaType.Body, modifier = Modifier.weight(1f))
                    Text(text = part.amount, style = HalalaNumbers.Amount)
                }
            }
            HorizontalDivider(thickness = Sizes.border, color = HalalaColors.Line)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.touchTarget)
                    .clickable(role = Role.Button, onClick = viewModel::onDebtsClick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = stringResource(R.string.zakat_debts), style = HalalaType.Body)
                Text(text = state.debts, style = HalalaNumbers.Amount)
            }
        }

        ListCard(Modifier.fillMaxWidth()) {
            ListRow(
                title = stringResource(R.string.zakat_day),
                subtitle = state.hijriMonth?.let { stringResource(R.string.zakat_day_value, state.hijriDay!!, months[it - 1]) }
                    ?: stringResource(R.string.zakat_day_unset),
                onClick = viewModel::onDayClick
            )
            ListRow(
                title = stringResource(R.string.zakat_gold_price),
                subtitle = state.goldPrice ?: stringResource(R.string.zakat_gold_price_unset),
                divider = true,
                onClick = viewModel::onGoldPriceClick
            )
        }

        Text(text = stringResource(R.string.zakat_note), style = HalalaType.Caption, color = HalalaColors.TextMuted)

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            HalalaButton(
                text = stringResource(if (state.remind) R.string.zakat_reminding else R.string.zakat_remind),
                onClick = {
                    if (!state.remind) askToNotify()
                    viewModel.onRemindClick()
                },
                enabled = state.hijriMonth != null,
                modifier = Modifier.weight(1f)
            )
            HalalaButton(
                text = stringResource(if (state.paid) R.string.zakat_unmark_paid else R.string.zakat_mark_paid),
                onClick = viewModel::onPaidClick,
                kind = if (state.paid) ButtonKind.Secondary else ButtonKind.Primary,
                enabled = state.hijriMonth != null
            )
        }
    }

    when (val sheet = state.sheet) {
        is ZakatSheet.Day -> HalalaSheet(onDismiss = viewModel::onSheetDismiss) {
            Text(text = stringResource(R.string.zakat_day), style = HalalaType.Title)
            FormField(label = stringResource(R.string.zakat_month)) {
                ChoiceChips(options = (1..12).toList(), selected = sheet.month, label = { months[it - 1] }, onSelect = viewModel::onDayMonthPick)
            }
            FormField(label = stringResource(R.string.zakat_day_of_month)) {
                ChoiceChips(options = (1..30).toList(), selected = sheet.day, label = { it.toString() }, onSelect = viewModel::onDayDayPick)
            }
            HalalaButton(stringResource(R.string.save), viewModel::onDaySave, Modifier.fillMaxWidth(), kind = ButtonKind.Primary)
        }
        is ZakatSheet.GoldPrice -> HalalaSheet(onDismiss = viewModel::onSheetDismiss) {
            Text(text = stringResource(R.string.zakat_gold_price), style = HalalaType.Title)
            FormField(
                label = stringResource(R.string.asset_gold_price),
                hint = stringResource(R.string.zakat_gold_price_hint),
                error = stringResource(R.string.asset_number_invalid).takeIf { sheet.invalid }
            ) {
                HalalaTextField(value = sheet.text, onValueChange = viewModel::onGoldPriceChange, numeric = true, isError = sheet.invalid)
            }
            HalalaButton(stringResource(R.string.save), viewModel::onGoldPriceSave, Modifier.fillMaxWidth(), kind = ButtonKind.Primary)
        }
        is ZakatSheet.Debts -> HalalaSheet(onDismiss = viewModel::onSheetDismiss) {
            Text(text = stringResource(R.string.zakat_debts), style = HalalaType.Title)
            FormField(
                label = stringResource(R.string.zakat_other_debts),
                hint = stringResource(R.string.zakat_other_debts_hint),
                error = stringResource(R.string.amount_invalid).takeIf { sheet.invalid }
            ) {
                HalalaTextField(value = sheet.text, onValueChange = viewModel::onDebtsChange, numeric = true, isError = sheet.invalid)
            }
            HalalaButton(stringResource(R.string.save), viewModel::onDebtsSave, Modifier.fillMaxWidth(), kind = ButtonKind.Primary)
        }
        null -> Unit
    }
}

private fun partLabel(kind: WealthClass) = when (kind) {
    WealthClass.ACCOUNTS -> R.string.wealth_accounts
    WealthClass.SAVINGS -> R.string.wealth_savings
    WealthClass.FUNDS -> R.string.zakat_funds
    WealthClass.GOLD -> R.string.wealth_gold
    else -> R.string.zakat_owed
}
