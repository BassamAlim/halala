package bassamalim.halala.features.assets

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import bassamalim.halala.R
import bassamalim.halala.core.data.repositories.AssetsRepository
import bassamalim.halala.core.domain.Assets
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.enums.AssetType
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.ui.components.GroupLabel
import bassamalim.halala.core.ui.components.ListCard
import bassamalim.halala.core.ui.components.ListRow
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Spacing
import bassamalim.halala.core.utils.shortDateLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * One asset as the list shows it: [detail] is what it is made of ("95 g · 21k", "1,234.5678
 * units at 12.3456 · 28 Sep"), already words but for the units' label, which the screen adds.
 */
data class AssetRow(
    val id: Long,
    val type: AssetType,
    val name: String,
    val quantity: String?,
    val karat: Int?,
    val unitPrice: String?,
    val priced: String?,
    val value: String,
    val gain: String?
)

data class AssetsUiState(val isLoading: Boolean = true, val assets: List<AssetRow> = emptyList())

@HiltViewModel
class AssetsViewModel @Inject constructor(
    assetsRepository: AssetsRepository,
    private val navigator: Navigator,
    private val clock: Clock
) : ViewModel() {

    val uiState: StateFlow<AssetsUiState> = assetsRepository.observeAll().map { assets ->
        val today = LocalDate.now(clock)
        AssetsUiState(
            isLoading = false,
            assets = assets.map { asset ->
                AssetRow(
                    id = asset.id,
                    type = asset.type,
                    name = asset.name,
                    quantity = asset.quantity,
                    karat = asset.karat,
                    unitPrice = asset.unitPrice,
                    priced = asset.priceDate?.let { shortDateLabel(it, today) },
                    value = Money.format(Assets.valueOf(asset, today), asset.currency, decimals = false),
                    gain = Assets.gainOf(asset, today)?.let { Money.format(it, asset.currency, decimals = false, showPlus = true) }
                )
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssetsUiState())

    fun onBackClick() = navigator.popBackStack()

    fun onAddClick() = navigator.navigate(Screen.EditAsset())

    fun onAssetClick(id: Long) = navigator.navigate(Screen.EditAsset(id))
}

/** Funds, gold and other things you own, with what each is worth. No board draws it; it is the system's list card. */
@Composable
fun AssetsScreen(viewModel: AssetsViewModel = hiltViewModel()) {
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
            title = stringResource(R.string.assets),
            onBack = viewModel::onBackClick,
            actionLabel = stringResource(R.string.recurring_add),
            onAction = viewModel::onAddClick
        )
        if (state.isLoading) return@Column
        if (state.assets.isEmpty()) Text(text = stringResource(R.string.assets_empty), style = HalalaType.Body, color = HalalaColors.TextMuted)

        state.assets.groupBy { it.type }.forEach { (type, rows) ->
            GroupLabel(stringResource(assetTypeLabel(type)))
            ListCard(Modifier.fillMaxWidth()) {
                rows.forEachIndexed { index, row ->
                    ListRow(
                        title = row.name,
                        subtitle = detailOf(row),
                        divider = index > 0,
                        trailing = {
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.align(Alignment.CenterVertically)) {
                                Text(text = row.value, style = HalalaNumbers.Amount)
                                row.gain?.let { Text(text = it, style = HalalaNumbers.Meta, color = HalalaColors.TextMuted) }
                            }
                        },
                        onClick = { viewModel.onAssetClick(row.id) }
                    )
                }
            }
        }
        Text(text = stringResource(R.string.assets_hint), style = HalalaType.Caption, color = HalalaColors.TextMuted)
    }
}

fun assetTypeLabel(type: AssetType) = when (type) {
    AssetType.FUND -> R.string.asset_fund
    AssetType.GOLD -> R.string.asset_gold
    AssetType.VEHICLE -> R.string.asset_vehicle
    AssetType.PROPERTY -> R.string.asset_property
    AssetType.OTHER -> R.string.asset_other
}

@Composable
private fun detailOf(row: AssetRow): String? = when (row.type) {
    AssetType.FUND -> row.quantity?.let { units ->
        listOfNotNull(stringResource(R.string.asset_fund_detail, units, row.unitPrice.orEmpty()), row.priced).joinToString(" · ")
    }
    AssetType.GOLD -> row.quantity?.let { stringResource(R.string.asset_gold_detail, it, row.karat ?: 24) }
    else -> row.priced?.let { stringResource(R.string.asset_valued_on, it) }
}
