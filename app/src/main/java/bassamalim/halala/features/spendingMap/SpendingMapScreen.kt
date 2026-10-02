package bassamalim.halala.features.spendingMap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.ui.components.ChipStyle
import bassamalim.halala.core.ui.components.ChoiceSheet
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.HalalaCard
import bassamalim.halala.core.ui.components.HalalaChip
import bassamalim.halala.core.ui.components.HeatMap
import bassamalim.halala.core.ui.components.ListCard
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/** Where you spend: the map with its heat, the period and category, and the top places. No board draws it. */
@Composable
fun SpendingMapScreen(viewModel: SpendingMapViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen)
            .padding(top = Insets.screenTop, bottom = Spacing.section),
        verticalArrangement = Arrangement.spacedBy(Spacing.card)
    ) {
        TopBar(title = stringResource(R.string.map_title), onBack = viewModel::onBackClick)
        if (state.isLoading) return@Column

        if (!state.placesOn) HalalaCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(text = stringResource(R.string.map_off), style = HalalaType.Body, color = HalalaColors.TextMuted)
            HalalaButton(stringResource(R.string.map_turn_on), viewModel::onTurnOnClick, Modifier.fillMaxWidth())
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MapPeriod.entries.forEach { period ->
                HalalaChip(
                    label = stringResource(
                        when (period) {
                            MapPeriod.MONTH -> R.string.map_month
                            MapPeriod.THREE_MONTHS -> R.string.map_three_months
                            MapPeriod.YEAR -> R.string.map_year
                            MapPeriod.ALL -> R.string.map_all
                        }
                    ),
                    style = if (state.period == period) ChipStyle.Accent else ChipStyle.Outline,
                    onClick = { viewModel.onPeriodClick(period) }
                )
            }
        }
        HalalaChip(
            label = state.categoryName ?: stringResource(R.string.map_all_categories),
            style = if (state.categoryId != null) ChipStyle.Accent else ChipStyle.Outline,
            onClick = viewModel::onCategoryClick
        )

        HeatMap(
            points = state.points,
            focus = state.focus,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = Sizes.onboardingBalance)
                .clip(Radius.lg)
        )
        Text(
            text = if (state.count == 0) stringResource(R.string.map_empty)
            else pluralStringResource(R.plurals.map_summary, state.count, state.count, state.total),
            style = HalalaType.Label,
            color = HalalaColors.TextMuted
        )

        if (state.top.isNotEmpty()) ListCard(Modifier.fillMaxWidth()) {
            state.top.take(TOP_SHOWN).forEachIndexed { index, place ->
                ListRow(
                    title = place.name,
                    subtitle = pluralStringResource(R.plurals.map_visits, place.count, place.count),
                    divider = index > 0,
                    trailing = { Text(text = place.spent, style = HalalaNumbers.Amount) },
                    onClick = { viewModel.onPlaceClick(place.key) }
                )
            }
        }
        Text(text = stringResource(R.string.map_privacy), style = HalalaType.Caption, color = HalalaColors.TextMuted)
    }

    if (state.pickingCategory) ChoiceSheet(
        title = stringResource(R.string.category),
        options = listOf<Long?>(null) + state.categories.map { it.first },
        selected = state.categoryId,
        label = { id -> id?.let { state.categories.first { it.first == id }.second } ?: stringResource(R.string.map_all_categories) },
        onPick = viewModel::onCategoryPicked,
        onDismiss = viewModel::onCategoryDismiss
    )
}

/** The map takes what is left; three places fit under it on any phone. */
private const val TOP_SHOWN = 3
