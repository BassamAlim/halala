package bassamalim.halala.features.retirement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.Globals
import bassamalim.halala.core.data.dataSources.room.entities.RetirementPlan
import bassamalim.halala.core.data.repositories.GoalsRepository
import bassamalim.halala.core.data.repositories.PlannerRepository
import bassamalim.halala.core.domain.Assets
import bassamalim.halala.core.domain.GoalState
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.Planner
import bassamalim.halala.core.domain.RetirementInputs
import bassamalim.halala.core.domain.RetirementResult
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.OneAtATime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** The plan's inputs as typed, or a try's (which also gives the pot and the saving a month). */
data class RetirementForm(
    val birthYear: String = "",
    val retireAt: String = "60",
    val wanted: String = "",
    val returnPercent: String = "5",
    val inflationPercent: String = "2.5",
    /** The goal that holds the pot; null makes a new one. */
    val goalId: Long? = null,
    val start: String = "",
    val monthly: String = ""
)

data class RetirementUiState(
    val isLoading: Boolean = true,
    val hasPlan: Boolean = false,
    /** The plan's goal; null without one. */
    val goal: GoalLine? = null,
    /** The plan's result, or the try's while trying; null until every input reads. */
    val result: RetirementView? = null,
    /** While trying: the plan as it stands, to compare. */
    val plan: PlanLine? = null,
    val trying: RetirementForm? = null,
    val editing: RetirementForm? = null,
    /** The goals the pot can be held in. */
    val goals: List<GoalChoice> = emptyList(),
    val canSave: Boolean = false
)

/** The goal holding the pot: what it holds, and what went into it a month over the last three. */
data class GoalLine(val id: Long, val name: String, val saved: String, val averaged: String)

/** The plan beside a try: its age, and how far short it falls a month (null on track). */
data class PlanLine(val retireAt: Int, val short: String?)

data class GoalChoice(val id: Long, val name: String)

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
    /** The pot at each age on the curve. */
    val pots: List<String>,
    val fromAge: Int
)

@HiltViewModel
class RetirementViewModel @Inject constructor(
    private val plannerRepository: PlannerRepository,
    goalsRepository: GoalsRepository,
    private val navigator: Navigator,
    private val clock: Clock
) : ViewModel() {

    private val currency = Globals.PRIMARY_CURRENCY
    private val saving = OneAtATime()
    private val editing = MutableStateFlow<RetirementForm?>(null)
    private val trying = MutableStateFlow<RetirementForm?>(null)

    /** The plan, its goal as it stands, and the goals it could be held in. */
    private data class Standing(val plan: RetirementPlan?, val goal: GoalState?, val goals: List<GoalState>)

    private val standing = combine(plannerRepository.observePlan(), goalsRepository.observeStates()) { plan, goals ->
        Standing(plan, goals.find { it.goal.id == plan?.goalId }, goals.filter { it.goal.currency == currency })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val uiState: StateFlow<RetirementUiState> = combine(standing, editing, trying) { standing, editing, trying ->
        if (standing == null) return@combine RetirementUiState()
        val today = LocalDate.now(clock)
        val plan = standing.plan
        val planned = plan
            ?.let { Planner.inputsOf(it, standing.goal?.savedMinor ?: 0, standing.goal?.averageMonthlyMinor ?: 0, today) }
            ?.let { inputs -> Planner.retirement(inputs)?.let { inputs to it } }
        val tried = trying?.let { inputsOf(it, currency, today) }?.let { inputs -> Planner.retirement(inputs)?.let { inputs to it } }
        RetirementUiState(
            isLoading = false,
            hasPlan = plan != null,
            goal = standing.goal?.let { GoalLine(it.goal.id, it.goal.name, format(it.savedMinor), format(it.averageMonthlyMinor)) },
            result = (if (trying != null) tried else planned)?.let { (inputs, result) -> viewOf(inputs, result) },
            plan = plan?.takeIf { trying != null }?.let { PlanLine(it.retireAt, planned?.second?.shortMonthlyMinor?.takeIf { short -> short > 0 }?.let(::format)) },
            trying = trying,
            editing = editing,
            goals = standing.goals.map { GoalChoice(it.goal.id, it.goal.name) },
            canSave = editing?.let { planOf(it, currency, today) } != null
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RetirementUiState())

    private fun format(minor: Long) = Money.format(minor, currency, decimals = false)

    private fun viewOf(inputs: RetirementInputs, result: RetirementResult) = RetirementView(
        retireAt = inputs.retireAt,
        pot = format(result.potMinor),
        potToday = format(result.potTodayMinor),
        supports = format(result.supportsMonthlyMinor),
        short = result.shortMonthlyMinor.takeIf { it > 0 }?.let(::format),
        extra = format(result.extraMonthlyMinor),
        curve = result.curve.map { it.potMinor },
        contributed = result.curve.map { it.contributedMinor },
        pots = result.curve.map { format(it.potMinor) },
        fromAge = inputs.ageNow
    )

    private fun formOf(plan: RetirementPlan) = RetirementForm(
        birthYear = plan.birthYear.toString(),
        retireAt = plan.retireAt.toString(),
        wanted = Money.input(plan.wantedMinor, plan.currency),
        returnPercent = plan.returnPercent,
        inflationPercent = plan.inflationPercent,
        goalId = plan.goalId
    )

    /** Typing goes to the plan while it is being edited, else to the try. */
    private fun edit(change: (RetirementForm) -> RetirementForm) {
        if (editing.value != null) editing.update { it?.let(change) } else trying.update { it?.let(change) }
    }

    fun onBackClick() = navigator.popBackStack()
    fun onBirthYearChange(text: String) = edit { it.copy(birthYear = text.filter(Char::isDigit).take(4)) }
    fun onRetireChange(text: String) = edit { it.copy(retireAt = text.filter(Char::isDigit).take(3)) }
    fun onWantedChange(text: String) = edit { it.copy(wanted = text) }
    fun onReturnChange(text: String) = edit { it.copy(returnPercent = text) }
    fun onInflationChange(text: String) = edit { it.copy(inflationPercent = text) }
    fun onStartChange(text: String) = edit { it.copy(start = text) }
    fun onMonthlyChange(text: String) = edit { it.copy(monthly = text) }
    fun onGoalSelect(id: Long?) = edit { it.copy(goalId = id) }

    fun onEditClick() {
        editing.value = standing.value?.plan?.let(::formOf) ?: RetirementForm()
    }

    fun onEditDismiss() = editing.update { null }

    /** Saves the plan; a new goal for it is named [newGoalName]. */
    fun onSaveClick(newGoalName: String) {
        val plan = editing.value?.let { planOf(it, currency, LocalDate.now(clock)) } ?: return
        // The screen stays, so the guard lets the next save in once this one is written.
        saving.launch(viewModelScope) {
            if (plannerRepository.savePlan(plan, newGoalName)) editing.update { null }
            false
        }
    }

    /** A try starts from the plan, the pot its goal holds and what has been going in. */
    fun onTryClick() {
        val standing = standing.value ?: return
        val plan = standing.plan ?: return
        trying.value = formOf(plan).copy(
            start = Money.input(standing.goal?.savedMinor ?: 0, currency),
            monthly = Money.input(standing.goal?.averageMonthlyMinor ?: 0, currency)
        )
    }

    fun onTryDone() = trying.update { null }

    fun onGoalClick(id: Long) = navigator.navigate(Screen.EditGoal(id))

    fun onCompoundClick() = navigator.navigate(Screen.Compound)

    companion object {

        /** The plan a form describes, or null while any of it doesn't read: an age of 15 to 90, retiring after it by 100, an income wanted. */
        fun planOf(form: RetirementForm, currency: String, today: LocalDate): RetirementPlan? {
            val born = form.birthYear.trim().toIntOrNull()?.takeIf { today.year - it in 15..90 } ?: return null
            val retire = form.retireAt.trim().toIntOrNull()?.takeIf { it in (today.year - born + 1)..100 } ?: return null
            return RetirementPlan(
                goalId = form.goalId,
                birthYear = born,
                retireAt = retire,
                wantedMinor = Money.parse(form.wanted, currency)?.takeIf { it > 0 } ?: return null,
                returnPercent = Assets.decimal(form.returnPercent)?.toPlainString() ?: return null,
                inflationPercent = Assets.decimal(form.inflationPercent)?.toPlainString() ?: return null,
                currency = currency
            )
        }

        /** A try's inputs: its plan, with the pot and the saving a month it gives. */
        fun inputsOf(form: RetirementForm, currency: String, today: LocalDate): RetirementInputs? {
            val plan = planOf(form, currency, today) ?: return null
            val start = Money.parse(form.start.ifBlank { "0" }, currency) ?: return null
            val monthly = Money.parse(form.monthly.ifBlank { "0" }, currency) ?: return null
            return Planner.inputsOf(plan, start, monthly, today)
        }
    }
}
