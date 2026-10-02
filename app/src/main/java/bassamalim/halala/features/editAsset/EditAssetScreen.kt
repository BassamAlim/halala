package bassamalim.halala.features.editAsset

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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.halala.R
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.components.SearchField
import bassamalim.halala.core.ui.components.HalalaSheet
import androidx.compose.foundation.layout.heightIn
import bassamalim.halala.core.domain.Assets
import bassamalim.halala.core.enums.AssetType
import bassamalim.halala.core.ui.components.ChoiceChips
import bassamalim.halala.core.ui.components.ConfirmSheet
import bassamalim.halala.core.ui.components.DateDialog
import bassamalim.halala.core.ui.components.FormField
import bassamalim.halala.core.ui.components.HalalaButton
import bassamalim.halala.core.ui.components.HalalaTextField
import bassamalim.halala.core.ui.components.ListCard
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Spacing
import bassamalim.halala.features.assets.assetTypeLabel

/**
 * Adding or changing an asset: a fund's units and unit price, gold's grams, karat, the day's
 * price and a dealer's spread, or another thing's value and yearly depreciation; and what you
 * paid. No board draws it.
 */
@Composable
fun EditAssetScreen(viewModel: EditAssetViewModel = hiltViewModel()) {
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
            title = stringResource(if (state.isNew) R.string.asset_new else R.string.asset),
            onBack = viewModel::onBackClick,
            actionLabel = stringResource(R.string.save),
            actionEnabled = !state.isLoading,
            onAction = viewModel::onSaveClick
        )
        if (state.isLoading) return@Column

        FormField(label = stringResource(R.string.recurring_kind)) {
            ChoiceChips(
                options = AssetType.entries,
                selected = form.type,
                label = { stringResource(assetTypeLabel(it)) },
                onSelect = viewModel::onTypeClick
            )
        }
        Field(stringResource(R.string.recurring_name), form.name, viewModel::onNameChange, AssetProblem.NameMissing in problems, R.string.merchant_name_missing, numeric = false)

        when (form.type) {
            AssetType.FUND -> {
                Field(stringResource(R.string.asset_units), form.quantity, viewModel::onQuantityChange, AssetProblem.QuantityInvalid in problems, R.string.asset_number_invalid)
                ListCard(Modifier.fillMaxWidth()) {
                    ListRow(
                        title = stringResource(R.string.asset_market_price),
                        subtitle = stringResource(if (form.priceSource != null) R.string.asset_fund_linked else R.string.asset_fund_not_linked),
                        onClick = viewModel::onLinkFundClick
                    )
                }
                if (form.priceSource != null) HalalaButton(stringResource(R.string.asset_unlink), viewModel::onUnlinkClick, Modifier.fillMaxWidth())
                Field(stringResource(R.string.asset_unit_price), form.unitPrice, viewModel::onPriceChange, AssetProblem.PriceInvalid in problems, R.string.asset_number_invalid)
            }
            AssetType.GOLD -> {
                Field(stringResource(R.string.asset_grams), form.quantity, viewModel::onQuantityChange, AssetProblem.QuantityInvalid in problems, R.string.asset_number_invalid)
                FormField(label = stringResource(R.string.asset_karat)) {
                    ChoiceChips(
                        options = Assets.KARATS,
                        selected = form.karat,
                        label = { stringResource(R.string.asset_karat_value, it) },
                        onSelect = viewModel::onKaratClick
                    )
                }
                FormField(
                    label = stringResource(R.string.asset_gold_source),
                    error = stringResource(R.string.asset_gold_failed).takeIf { state.goldFailed }
                ) {
                    ChoiceChips(
                        options = listOf(true, false),
                        selected = form.priceSource != null,
                        label = { stringResource(if (it) R.string.asset_gold_market else R.string.asset_gold_mine) },
                        onSelect = viewModel::onGoldMarketClick
                    )
                }
                Field(stringResource(R.string.asset_gold_price), form.unitPrice, viewModel::onPriceChange, AssetProblem.PriceInvalid in problems, R.string.asset_number_invalid, hint = stringResource(R.string.asset_gold_price_hint))
                Field(stringResource(R.string.asset_spread), form.spread, viewModel::onSpreadChange, AssetProblem.PercentInvalid in problems, R.string.asset_percent_invalid, hint = stringResource(R.string.asset_spread_hint))
            }
            else -> {
                Field(stringResource(R.string.asset_value), form.value, viewModel::onValueChange, AssetProblem.ValueInvalid in problems, R.string.amount_invalid)
                Field(stringResource(R.string.asset_depreciation), form.depreciation, viewModel::onDepreciationChange, AssetProblem.PercentInvalid in problems, R.string.asset_percent_invalid, hint = stringResource(R.string.asset_depreciation_hint))
            }
        }

        ListCard(Modifier.fillMaxWidth()) {
            ListRow(
                title = stringResource(if (form.type == AssetType.FUND || form.type == AssetType.GOLD) R.string.asset_priced_on else R.string.asset_valued_as_of),
                subtitle = state.dateLabel,
                onClick = viewModel::onDateClick
            )
        }
        Field(stringResource(R.string.asset_cost), form.cost, viewModel::onCostChange, AssetProblem.CostInvalid in problems, R.string.amount_invalid, hint = stringResource(R.string.optional))
        Text(text = stringResource(R.string.asset_manual_hint), style = HalalaType.Caption, color = HalalaColors.TextMuted)

        if (!state.isNew) HalalaButton(
            text = stringResource(R.string.delete),
            onClick = viewModel::onDeleteClick,
            destructive = true,
            modifier = Modifier.fillMaxWidth()
        )
    }

    state.fundSearch?.let { search ->
        HalalaSheet(viewModel::onFundSearchDismiss) {
            Text(text = stringResource(R.string.asset_pick_fund), style = HalalaType.Title)
            SearchField(value = search.query, onValueChange = viewModel::onFundQueryChange, placeholder = stringResource(R.string.asset_fund_search))
            when {
                search.results == null -> Text(text = stringResource(R.string.asset_funds_loading), style = HalalaType.Label, color = HalalaColors.TextMuted)
                search.failed -> Text(text = stringResource(R.string.asset_funds_failed), style = HalalaType.Label, color = HalalaColors.TextMuted)
                search.results.isEmpty() -> Text(text = stringResource(R.string.asset_funds_none), style = HalalaType.Label, color = HalalaColors.TextMuted)
                else -> Column(Modifier.heightIn(max = Sizes.sankeyMax).verticalScroll(rememberScrollState())) {
                    search.results.forEachIndexed { index, fund ->
                        ListRow(
                            title = fund.name,
                            subtitle = listOfNotNull(fund.manager, fund.date).joinToString(" · "),
                            divider = index > 0,
                            trailing = { Text(text = fund.price, style = HalalaNumbers.Meta) },
                            onClick = { viewModel.onFundPicked(fund.id) }
                        )
                    }
                }
            }
            Text(text = stringResource(R.string.asset_funds_source), style = HalalaType.Caption, color = HalalaColors.TextMuted)
        }
    }

    if (state.pickingDate) DateDialog(date = state.pickFrom, onPicked = viewModel::onDatePicked, onDismiss = viewModel::onDateDismiss)
    if (state.isConfirmingDelete) ConfirmSheet(
        title = stringResource(R.string.goal_delete_title, form.name),
        body = stringResource(R.string.asset_delete_body),
        confirmLabel = stringResource(R.string.delete),
        dismissLabel = stringResource(R.string.keep),
        onConfirm = viewModel::onDeleteConfirm,
        onDismiss = viewModel::onDeleteDismiss
    )
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, isError: Boolean, error: Int, numeric: Boolean = true, hint: String? = null) {
    FormField(label = label, hint = hint, error = stringResource(error).takeIf { isError }) {
        HalalaTextField(
            value = value,
            onValueChange = onChange,
            numeric = numeric,
            isError = isError,
            capitalization = if (numeric) KeyboardCapitalization.None else KeyboardCapitalization.Words
        )
    }
}
