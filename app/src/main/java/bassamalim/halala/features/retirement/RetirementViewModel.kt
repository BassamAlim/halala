package bassamalim.halala.features.retirement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.Globals
import bassamalim.halala.core.data.dataSources.room.entities.RetirementScenario
import bassamalim.halala.core.data.repositories.PlannerRepository
import bassamalim.halala.core.domain.Assets
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.Planner
import bassamalim.halala.core.domain.RetirementInputs
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** The planner's inputs as typed. */
data class RetirementForm(
    val ageNow: String = "",
    val retireAt: String = "60",
    val start: String = "",
    val monthly: String = "",
    val returnPercent: String = "5",
    val inflationPercent: String = "2.5",
    val wanted: String = ""
)

data class RetirementUiState(
    val isLoading: Boolean = true,
    val form: RetirementForm = RetirementForm(),
    /** Null until every input reads. */
    val result: RetirementView? = null,
    val scenarios: List<RetirementScenario> = emptyList(),
    val sheet: RetirementSheet? = null
)

/** The result in words' figures: the pot, its worth today, the income it supports, short or over, the extra to save. */
data class RetirementView(
    val retireAt: Int,
    val pot: String,
    val potToday: String,
    val supports: String,
    val short: String?,
    val extra: String,
    val curve: List<Long>,
    val contributed: List<Long>,
    val fromAge: Int
)

sealed interface RetirementSheet {
    data class Save(val name: String = "") : RetirementSheet
    data object Scenarios : RetirementSheet
}

@HiltViewModel
class RetirementViewModel @Inject constructor(
    private val plannerRepository: PlannerRepository,
    private val navigator: Navigator
) : ViewModel() {

    private val currency = Globals.PRIMARY_CURRENCY
    private val form = MutableStateFlow<RetirementForm?>(null)
    private val sheet = MutableStateFlow<RetirementSheet?>(null)

    init {
        // Starts from what you have invested and what you have been saving.
        viewModelScope.launch {
            form.value = RetirementForm(
                start = Money.input(plannerRepository.investedNow(currency), currency),
                monthly = Money.input(plannerRepository.averageSaving(currency), currency)
            )
        }
    }

    val uiState: StateFlow<RetirementUiState> = combine(form, plannerRepository.observeScenarios(), sheet) { form, scenarios, sheet ->
        RetirementUiState(
            isLoading = form == null,
            form = form ?: RetirementForm(),
            result = form?.let(::inputsOf)?.let { inputs ->
                Planner.retirement(inputs)?.let {
                    RetirementView(
                        retireAt = inputs.retireAt,
                        pot = Money.format(it.potMinor, currency, decimals = false),
                        potToday = Money.format(it.potTodayMinor, currency, decimals = false),
                        supports = Money.format(it.supportsMonthlyMinor, currency, decimals = false),
                        short = it.shortMonthlyMinor.takeIf { short -> short > 0 }?.let { short -> Money.format(short, currency, decimals = false) },
                        extra = Money.format(it.extraMonthlyMinor, currency, decimals = false),
                        curve = it.curve.map { point -> point.potMinor },
                        contributed = it.curve.map { point -> point.contributedMinor },
                        fromAge = inputs.ageNow
                    )
                }
            },
            scenarios = scenarios,
            sheet = sheet
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RetirementUiState())

    private fun inputsOf(form: RetirementForm): RetirementInputs? {
        val age = form.ageNow.trim().toIntOrNull()?.takeIf { it in 15..90 } ?: return null
        val retire = form.retireAt.trim().toIntOrNull()?.takeIf { it in (age + 1)..100 } ?: return null
        return RetirementInputs(
            ageNow = age,
            retireAt = retire,
            startMinor = Money.parse(form.start.ifBlank { "0" }, currency) ?: return null,
            monthlyMinor = Money.parse(form.monthly.ifBlank { "0" }, currency) ?: return null,
            returnPercent = Assets.decimal(form.returnPercent) ?: return null,
            inflationPercent = Assets.decimal(form.inflationPercent) ?: return null,
            wantedMonthlyMinor = Money.parse(form.wanted.ifBlank { "0" }, currency) ?: return null
        )
    }

    private fun edit(change: (RetirementForm) -> RetirementForm) = form.update { it?.let(change) }

    fun onBackClick() = navigator.popBackStack()
    fun onAgeChange(text: String) = edit { it.copy(ageNow = text.filter(Char::isDigit).take(3)) }
    fun onRetireChange(text: String) = edit { it.copy(retireAt = text.filter(Char::isDigit).take(3)) }
    fun onStartChange(text: String) = edit { it.copy(start = text) }
    fun onMonthlyChange(text: String) = edit { it.copy(monthly = text) }
    fun onReturnChange(text: String) = edit { it.copy(returnPercent = text) }
    fun onInflationChange(text: String) = edit { it.copy(inflationPercent = text) }
    fun onWantedChange(text: String) = edit { it.copy(wanted = text) }

    fun onCompoundClick() = navigator.navigate(Screen.Compound)

    fun onScenariosClick() = sheet.update { RetirementSheet.Scenarios }
    fun onSaveClick() = sheet.update { RetirementSheet.Save() }
    fun onNameChange(text: String) = sheet.update { RetirementSheet.Save(text) }
    fun onSheetDismiss() = sheet.update { null }

    fun onSaveConfirm() {
        val name = (sheet.value as? RetirementSheet.Save)?.name?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val inputs = form.value?.let(::inputsOf) ?: return
        sheet.update { null }
        viewModelScope.launch {
            plannerRepository.save(
                RetirementScenario(
                    uid = "", name = name, ageNow = inputs.ageNow, retireAt = inputs.retireAt, startMinor = inputs.startMinor,
                    monthlyMinor = inputs.monthlyMinor, returnPercent = inputs.returnPercent.toPlainString(),
                    inflationPercent = inputs.inflationPercent.toPlainString(), wantedMinor = inputs.wantedMonthlyMinor,
                    currency = currency, createdAt = Instant.EPOCH
                )
            )
        }
    }

    fun onScenarioLoad(scenario: RetirementScenario) {
        sheet.update { null }
        form.value = RetirementForm(
            ageNow = scenario.ageNow.toString(),
            retireAt = scenario.retireAt.toString(),
            start = Money.input(scenario.startMinor, scenario.currency),
            monthly = Money.input(scenario.monthlyMinor, scenario.currency),
            returnPercent = scenario.returnPercent,
            inflationPercent = scenario.inflationPercent,
            wanted = Money.input(scenario.wantedMinor, scenario.currency)
        )
    }

    fun onScenarioDelete(id: Long) {
        viewModelScope.launch { plannerRepository.delete(id) }
    }
}
