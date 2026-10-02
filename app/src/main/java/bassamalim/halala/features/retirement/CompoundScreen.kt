package bassamalim.halala.features.retirement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import bassamalim.halala.R
import bassamalim.halala.core.Globals
import bassamalim.halala.core.domain.Assets
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.Planner
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.ui.components.ChoiceChips
import bassamalim.halala.core.ui.components.FormField
import bassamalim.halala.core.ui.components.HalalaTextField
import bassamalim.halala.core.ui.components.LineChart
import bassamalim.halala.core.ui.components.SummaryCard
import bassamalim.halala.core.ui.components.TopBar
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class CompoundForm(
    val principal: String = "",
    val monthly: String = "",
    val returnPercent: String = "6",
    /** Compounding periods a year: 12, 4 or 1. */
    val perYear: Int = 12,
    val years: String = "10"
)

data class CompoundUiState(
    val form: CompoundForm = CompoundForm(),
    val final: String? = null,
    val contributed: String = "",
    val returns: String = "",
    val curve: List<Long> = emptyList(),
    val putIn: List<Long> = emptyList()
)

@HiltViewModel
class CompoundViewModel @Inject constructor(private val navigator: Navigator) : ViewModel() {

    private val form = MutableStateFlow(CompoundForm())
    private val currency = Globals.PRIMARY_CURRENCY

    val uiState: StateFlow<CompoundUiState> = form.map { form ->
        val principal = Money.parse(form.principal.ifBlank { "0" }, currency)
        val monthly = Money.parse(form.monthly.ifBlank { "0" }, currency)
        val rate = Assets.decimal(form.returnPercent)
        val years = form.years.toIntOrNull()?.takeIf { it in 1..60 }
        val result = if (principal != null && monthly != null && rate != null && years != null)
            Planner.compound(principal, monthly, rate, form.perYear, years) else null
        CompoundUiState(
            form = form,
            final = result?.let { Money.format(it.finalMinor, currency, decimals = false) },
            contributed = result?.let { Money.format(it.contributedMinor, currency, decimals = false) }.orEmpty(),
            returns = result?.let { Money.format(it.returnsMinor, currency, decimals = false) }.orEmpty(),
            curve = result?.curve?.map { it.potMinor }.orEmpty(),
            putIn = result?.curve?.map { it.contributedMinor }.orEmpty()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompoundUiState())

    fun onBackClick() = navigator.popBackStack()
    fun onPrincipalChange(text: String) = form.update { it.copy(principal = text) }
    fun onMonthlyChange(text: String) = form.update { it.copy(monthly = text) }
    fun onReturnChange(text: String) = form.update { it.copy(returnPercent = text) }
    fun onPerYearClick(value: Int) = form.update { it.copy(perYear = value) }
    fun onYearsChange(text: String) = form.update { it.copy(years = text.filter(Char::isDigit).take(2)) }
}

/** The compound interest calculator (the spec's): what a sum and a monthly saving grow to, and how much of it is returns. No board draws it. */
@Composable
fun CompoundScreen(viewModel: CompoundViewModel = hiltViewModel()) {
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
        TopBar(title = stringResource(R.string.compound), onBack = viewModel::onBackClick)

        FormField(label = stringResource(R.string.compound_principal)) {
            HalalaTextField(value = form.principal, onValueChange = viewModel::onPrincipalChange, numeric = true)
        }
        FormField(label = stringResource(R.string.retirement_monthly)) {
            HalalaTextField(value = form.monthly, onValueChange = viewModel::onMonthlyChange, numeric = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            FormField(label = stringResource(R.string.retirement_return), modifier = Modifier.weight(1f)) {
                HalalaTextField(value = form.returnPercent, onValueChange = viewModel::onReturnChange, numeric = true)
            }
            FormField(label = stringResource(R.string.compound_years), modifier = Modifier.weight(1f)) {
                HalalaTextField(value = form.years, onValueChange = viewModel::onYearsChange, numeric = true, keyboardType = KeyboardType.Number)
            }
        }
        FormField(label = stringResource(R.string.compound_frequency)) {
            ChoiceChips(
                options = listOf(12, 4, 1),
                selected = form.perYear,
                label = {
                    stringResource(
                        when (it) {
                            12 -> R.string.cadence_monthly
                            4 -> R.string.compound_quarterly
                            else -> R.string.cadence_yearly
                        }
                    )
                },
                onSelect = viewModel::onPerYearClick
            )
        }

        state.final?.let { final ->
            SummaryCard(label = stringResource(R.string.compound_final), amount = final, currency = Globals.PRIMARY_CURRENCY)
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Insets.grid)) {
                SummaryCard(stringResource(R.string.compound_put_in), state.contributed, Modifier.weight(1f).fillMaxHeight())
                SummaryCard(stringResource(R.string.compound_returns), state.returns, Modifier.weight(1f).fillMaxHeight(), amountColor = HalalaColors.Income)
            }
            LineChart(
                values = state.curve,
                secondary = state.putIn,
                labels = listOf(stringResource(R.string.compound_year_0), stringResource(R.string.compound_year_n, form.years)),
                description = stringResource(R.string.retirement_chart),
                height = Sizes.fab * 2 + Sizes.chip
            )
            Text(text = stringResource(R.string.retirement_legend), style = HalalaType.Caption, color = HalalaColors.TextMuted)
        }
    }
}
