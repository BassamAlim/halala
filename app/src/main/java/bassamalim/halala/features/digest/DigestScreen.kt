package bassamalim.halala.features.digest

import bassamalim.halala.core.ui.components.Skeleton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.domain.DigestKind
import bassamalim.halala.core.ui.components.GroupLabel
import bassamalim.halala.core.ui.components.HalalaCard
import bassamalim.halala.core.ui.components.ListCard
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.ui.components.ProgressBar
import bassamalim.halala.core.ui.components.SummaryCard
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing
import kotlin.math.abs

/**
 * The Digest board: what you spent against the period before, what was left, what is owed to
 * you, where it went, and what is worth a look. Net worth's change joins it with Phase 5.
 */
@Composable
fun DigestScreen(viewModel: DigestViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.section),
        verticalArrangement = Arrangement.spacedBy(Spacing.card)
    ) {
        TopBar(
            title = stringResource(
                when (state.kind) {
                    DigestKind.WEEK -> R.string.digest_week_title
                    DigestKind.MONTH -> R.string.digest_month_title
                    DigestKind.YEAR -> R.string.digest_year_title
                },
                state.title
            ),
            onBack = viewModel::onBackClick
        )
        if (state.isLoading) {
            Skeleton()
            return@Column
        }

        val change = state.changePercent
        val tail = when {
            change == null -> stringResource(R.string.digest_spent_tail_none)
            change == 0 -> stringResource(R.string.digest_spent_tail_same, state.previousTitle)
            change < 0 -> stringResource(R.string.digest_spent_tail_less, abs(change), state.previousTitle)
            else -> stringResource(R.string.digest_spent_tail_more, change, state.previousTitle)
        }
        Text(
            text = buildAnnotatedString {
                append(stringResource(R.string.digest_spent_head))
                append(' ')
                withStyle(SpanStyle(fontFamily = HalalaNumbers.Amount.fontFamily)) { append(state.spent) }
                append(tail)
            },
            style = HalalaType.ScreenTitle
        )

        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SummaryCard(
                label = stringResource(R.string.digest_saved),
                amount = state.saved,
                amountColor = if (state.savedNegative) HalalaColors.Text else HalalaColors.Income,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            SummaryCard(
                label = stringResource(R.string.people_owed_to_you),
                amount = state.owedToYou,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        if (state.categories.isNotEmpty()) HalalaCard(label = stringResource(R.string.digest_where), modifier = Modifier.fillMaxWidth()) {
            state.categories.forEach { bar ->
                Column(Modifier.padding(vertical = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = bar.name ?: stringResource(R.string.digest_unfiled), style = HalalaType.Label)
                        Text(text = bar.amount, style = HalalaNumbers.Meta)
                    }
                    ProgressBar(progress = bar.fraction)
                }
            }
        }

        if (state.observations.isNotEmpty()) HalalaCard(label = stringResource(R.string.digest_worth_a_look), modifier = Modifier.fillMaxWidth()) {
            state.observations.forEach { line ->
                Row(Modifier.padding(vertical = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bolt),
                        contentDescription = null,
                        tint = HalalaColors.Accent,
                        modifier = Modifier.size(Sizes.iconSmall)
                    )
                    Text(text = observation(line), style = HalalaType.Label)
                }
            }
        }
    }
}

@Composable
private fun observation(line: ObservationLine): String = when (line) {
    is ObservationLine.Moved -> if (line.percent > 0) stringResource(R.string.digest_up, line.category, line.percent)
    else stringResource(R.string.digest_down, line.category, abs(line.percent))
    is ObservationLine.PriceRose -> stringResource(R.string.digest_price, line.name, line.from, line.to, line.monthly)
    is ObservationLine.LoanDue -> stringResource(if (line.lent) R.string.digest_loan_lent else R.string.digest_loan_borrowed, line.person, line.due)
}

/** Every past digest with spending: months, weeks and years. Reached from Settings. */
@Composable
fun DigestsScreen(viewModel: DigestsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.section),
        verticalArrangement = Arrangement.spacedBy(Spacing.card)
    ) {
        TopBar(title = stringResource(R.string.digests), onBack = viewModel::onBackClick)
        if (state.isLoading) {
            Skeleton()
            return@Column
        }
        if (state.months.isEmpty() && state.weeks.isEmpty() && state.years.isEmpty())
            Text(text = stringResource(R.string.digests_empty), style = HalalaType.Body, color = HalalaColors.TextMuted)

        for ((label, rows) in listOf(R.string.digests_months to state.months, R.string.digests_weeks to state.weeks, R.string.digests_years to state.years)) {
            if (rows.isEmpty()) continue
            GroupLabel(stringResource(label))
            ListCard(Modifier.fillMaxWidth()) {
                rows.forEachIndexed { index, row ->
                    ListRow(
                        title = if (row.kind == DigestKind.WEEK) stringResource(R.string.digest_week_of, row.title) else row.title,
                        subtitle = stringResource(R.string.digest_spent_row, row.spent),
                        divider = index > 0,
                        onClick = { viewModel.onDigestClick(row) }
                    )
                }
            }
        }
        Text(text = stringResource(R.string.digests_hint), style = HalalaType.Caption, color = HalalaColors.TextMuted)
    }
}
