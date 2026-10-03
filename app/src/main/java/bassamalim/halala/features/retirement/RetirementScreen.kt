package bassamalim.halala.features.retirement

import bassamalim.halala.core.ui.components.Skeleton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.Globals
import bassamalim.halala.core.ui.components.ButtonKind
import bassamalim.halala.core.ui.components.FormField
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.HalalaCard
import bassamalim.halala.core.ui.components.HalalaSheet
import bassamalim.halala.core.ui.components.HalalaTextField
import bassamalim.halala.core.ui.components.LineChart
import bassamalim.halala.core.ui.components.ListCard
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.components.appendCurrency
import bassamalim.halala.core.ui.components.currencyInlineContent
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * The Retirement board: what you would have at the age you retire, what it is worth today and the
 * income it supports, the curve (with what you put in, dashed), the inputs (the pot and the
 * monthly saving start from what you have and have been saving), the gap and the extra saving to
 * close it. Scenarios are kept to compare.
 */
@Composable
fun RetirementScreen(viewModel: RetirementViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val form = state.form

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.section),
        verticalArrangement = Arrangement.spacedBy(Spacing.card)
    ) {
        TopBar(
            title = stringResource(R.string.retirement),
            onBack = viewModel::onBackClick,
            actionLabel = stringResource(R.string.retirement_scenarios),
            onAction = viewModel::onScenariosClick
        )
        if (state.isLoading) {
            Skeleton()
            return@Column
        }

        state.result?.let { result ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(text = stringResource(R.string.retirement_at, result.retireAt), style = HalalaType.Label, color = HalalaColors.TextMuted)
                Text(
                    text = buildAnnotatedString {
                        append("≈ ")
                        append(result.pot)
                        appendCurrency(Globals.PRIMARY_CURRENCY)
                    },
                    style = HalalaNumbers.AmountXl,
                    inlineContent = currencyInlineContent(HalalaColors.TextMuted)
                )
                Text(text = stringResource(R.string.retirement_today, result.potToday, result.supports), style = HalalaType.Label, color = HalalaColors.TextMuted)
            }
            LineChart(
                values = result.curve,
                secondary = result.contributed,
                labels = listOf(stringResource(R.string.retirement_age, result.fromAge), result.retireAt.toString()),
                description = stringResource(R.string.retirement_chart),
                height = Sizes.fab * 2 + Sizes.chip
            )
            Text(text = stringResource(R.string.retirement_legend), style = HalalaType.Caption, color = HalalaColors.TextMuted)
        } ?: Text(text = stringResource(R.string.retirement_fill), style = HalalaType.Body, color = HalalaColors.TextMuted)

        FieldRow {
            Field(stringResource(R.string.retirement_age_now), form.ageNow, viewModel::onAgeChange, KeyboardType.Number)
            Field(stringResource(R.string.retirement_retire_at), form.retireAt, viewModel::onRetireChange, KeyboardType.Number)
        }
        FieldRow {
            Field(stringResource(R.string.retirement_monthly), form.monthly, viewModel::onMonthlyChange)
            Field(stringResource(R.string.retirement_return), form.returnPercent, viewModel::onReturnChange)
        }
        FieldRow {
            Field(stringResource(R.string.retirement_inflation), form.inflationPercent, viewModel::onInflationChange)
            Field(stringResource(R.string.retirement_wanted), form.wanted, viewModel::onWantedChange)
        }
        FormField(label = stringResource(R.string.retirement_start), hint = stringResource(R.string.retirement_start_hint)) {
            HalalaTextField(value = form.start, onValueChange = viewModel::onStartChange, numeric = true)
        }

        state.result?.let { result ->
            HalalaCard(modifier = Modifier.fillMaxWidth()) {
                if (result.short != null) {
                    Text(text = stringResource(R.string.retirement_short, result.short), style = HalalaType.BodyStrong)
                    Text(text = stringResource(R.string.retirement_extra, result.extra), style = HalalaType.Label, color = HalalaColors.TextMuted)
                } else Text(text = stringResource(R.string.retirement_on_track), style = HalalaType.BodyStrong)
            }
            HalalaButton(stringResource(R.string.retirement_save), viewModel::onSaveClick, Modifier.fillMaxWidth())
        }
        HalalaButton(stringResource(R.string.compound), viewModel::onCompoundClick, Modifier.fillMaxWidth())
    }

    when (val sheet = state.sheet) {
        is RetirementSheet.Save -> HalalaSheet(onDismiss = viewModel::onSheetDismiss) {
            Text(text = stringResource(R.string.retirement_save), style = HalalaType.Title)
            FormField(label = stringResource(R.string.recurring_name)) {
                HalalaTextField(value = sheet.name, onValueChange = viewModel::onNameChange)
            }
            HalalaButton(stringResource(R.string.save), viewModel::onSaveConfirm, Modifier.fillMaxWidth(), kind = ButtonKind.Primary, enabled = sheet.name.isNotBlank())
        }
        RetirementSheet.Scenarios -> HalalaSheet(onDismiss = viewModel::onSheetDismiss) {
            Text(text = stringResource(R.string.retirement_scenarios), style = HalalaType.Title)
            if (state.scenarios.isEmpty()) Text(text = stringResource(R.string.retirement_no_scenarios), style = HalalaType.Label, color = HalalaColors.TextMuted)
            else ListCard(Modifier.fillMaxWidth()) {
                state.scenarios.forEachIndexed { index, scenario ->
                    ListRow(
                        title = scenario.name,
                        subtitle = stringResource(R.string.retirement_scenario_detail, scenario.ageNow, scenario.retireAt, scenario.returnPercent),
                        divider = index > 0,
                        trailing = {
                            HalalaButton(stringResource(R.string.delete), { viewModel.onScenarioDelete(scenario.id) }, destructive = true)
                        },
                        onClick = { viewModel.onScenarioLoad(scenario) }
                    )
                }
            }
        }
        null -> Unit
    }
}

@Composable
private fun FieldRow(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), content = content)
}

@Composable
private fun RowScope.Field(label: String, value: String, onChange: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Decimal) {
    FormField(label = label, modifier = Modifier.weight(1f)) {
        HalalaTextField(value = value, onValueChange = onChange, numeric = true, keyboardType = keyboard)
    }
}
