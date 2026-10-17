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
import bassamalim.halala.core.ui.components.ChoiceChips
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
 * The Retirement board, as your plan: what you would have at the age you retire, what it is worth
 * today and the income it supports, the curve (with what you put in, dashed), and the gap and the
 * extra saving to close it. The pot is a savings goal (the Awaeed deposits you file under it) and
 * the saving a month is what went into it lately. Edit changes the plan; a try changes nothing.
 */
@Composable
fun RetirementScreen(viewModel: RetirementViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val newGoalName = stringResource(R.string.retirement)

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
            actionLabel = stringResource(R.string.edit).takeIf { state.hasPlan },
            onAction = viewModel::onEditClick
        )
        if (state.isLoading) {
            Skeleton()
            return@Column
        }
        if (!state.hasPlan) {
            Text(text = stringResource(R.string.retirement_empty), style = HalalaType.Body, color = HalalaColors.TextMuted)
            HalalaButton(stringResource(R.string.retirement_set), viewModel::onEditClick, Modifier.fillMaxWidth(), kind = ButtonKind.Primary)
            HalalaButton(stringResource(R.string.compound), viewModel::onCompoundClick, Modifier.fillMaxWidth())
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
                tips = result.pots.mapIndexed { year, pot -> stringResource(R.string.retirement_age, result.fromAge + year) to pot },
                description = stringResource(R.string.retirement_chart),
                height = Sizes.fab * 2 + Sizes.chip
            )
            Text(text = stringResource(R.string.retirement_legend), style = HalalaType.Caption, color = HalalaColors.TextMuted)
            HalalaCard(modifier = Modifier.fillMaxWidth()) {
                if (result.short != null) {
                    Text(text = stringResource(R.string.retirement_short, result.short), style = HalalaType.BodyStrong)
                    Text(text = stringResource(R.string.retirement_extra, result.extra), style = HalalaType.Label, color = HalalaColors.TextMuted)
                } else Text(text = stringResource(R.string.retirement_on_track), style = HalalaType.BodyStrong)
            }
        } ?: Text(
            text = stringResource(if (state.trying != null) R.string.retirement_fill else R.string.retirement_past),
            style = HalalaType.Body,
            color = HalalaColors.TextMuted
        )

        val trying = state.trying
        if (trying != null) {
            state.plan?.let { plan ->
                Text(
                    text = plan.short?.let { stringResource(R.string.retirement_plan_short, plan.retireAt, it) }
                        ?: stringResource(R.string.retirement_plan_on_track, plan.retireAt),
                    style = HalalaType.Label,
                    color = HalalaColors.TextMuted
                )
            }
            FieldRow {
                Field(stringResource(R.string.retirement_retire_at), trying.retireAt, viewModel::onRetireChange, KeyboardType.Number)
                Field(stringResource(R.string.retirement_wanted), trying.wanted, viewModel::onWantedChange)
            }
            FieldRow {
                Field(stringResource(R.string.retirement_start), trying.start, viewModel::onStartChange)
                Field(stringResource(R.string.retirement_monthly), trying.monthly, viewModel::onMonthlyChange)
            }
            FieldRow {
                Field(stringResource(R.string.retirement_return), trying.returnPercent, viewModel::onReturnChange)
                Field(stringResource(R.string.retirement_inflation), trying.inflationPercent, viewModel::onInflationChange)
            }
            Text(text = stringResource(R.string.retirement_try_hint), style = HalalaType.Caption, color = HalalaColors.TextMuted)
            HalalaButton(stringResource(R.string.retirement_try_done), viewModel::onTryDone, Modifier.fillMaxWidth())
            return@Column
        }

        ListCard(Modifier.fillMaxWidth()) {
            state.goal?.let { goal ->
                ListRow(
                    title = goal.name,
                    subtitle = stringResource(R.string.retirement_goal_line, goal.saved, goal.averaged),
                    onClick = { viewModel.onGoalClick(goal.id) }
                )
            } ?: ListRow(
                title = stringResource(R.string.retirement_no_goal),
                subtitle = stringResource(R.string.retirement_no_goal_hint),
                onClick = viewModel::onEditClick
            )
        }
        HalalaButton(stringResource(R.string.retirement_try), viewModel::onTryClick, Modifier.fillMaxWidth())
        HalalaButton(stringResource(R.string.compound), viewModel::onCompoundClick, Modifier.fillMaxWidth())
    }

    state.editing?.let { form ->
        HalalaSheet(onDismiss = viewModel::onEditDismiss) {
            Text(text = stringResource(R.string.retirement_plan), style = HalalaType.Title)
            FieldRow {
                Field(stringResource(R.string.retirement_born), form.birthYear, viewModel::onBirthYearChange, KeyboardType.Number)
                Field(stringResource(R.string.retirement_retire_at), form.retireAt, viewModel::onRetireChange, KeyboardType.Number)
            }
            FormField(label = stringResource(R.string.retirement_wanted), hint = stringResource(R.string.retirement_wanted_hint)) {
                HalalaTextField(value = form.wanted, onValueChange = viewModel::onWantedChange, numeric = true)
            }
            FieldRow {
                Field(stringResource(R.string.retirement_return), form.returnPercent, viewModel::onReturnChange)
                Field(stringResource(R.string.retirement_inflation), form.inflationPercent, viewModel::onInflationChange)
            }
            FormField(label = stringResource(R.string.retirement_goal), hint = stringResource(R.string.retirement_goal_hint)) {
                ChoiceChips(
                    options = state.goals.map { it.id } + null,
                    selected = form.goalId,
                    label = { id -> state.goals.find { it.id == id }?.name ?: stringResource(R.string.retirement_goal_new) },
                    onSelect = viewModel::onGoalSelect
                )
            }
            HalalaButton(
                stringResource(R.string.save),
                { viewModel.onSaveClick(newGoalName) },
                Modifier.fillMaxWidth(),
                kind = ButtonKind.Primary,
                enabled = state.canSave
            )
        }
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
