package bassamalim.halala.features.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.Globals
import bassamalim.halala.core.domain.HeadsUp
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.Recurring
import bassamalim.halala.core.domain.SeriesState
import bassamalim.halala.core.enums.SeriesStatus
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.initialOf
import bassamalim.halala.core.utils.monthYearLabel
import bassamalim.halala.core.utils.shortDateLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class RecurringViewModel @Inject constructor(
    private val domain: RecurringDomain,
    private val navigator: Navigator
) : ViewModel() {

    init {
        // Whatever repeats since the app opened is proposed as you look.
        viewModelScope.launch { domain.detect() }
    }

    val uiState: StateFlow<RecurringUiState> = combine(domain.observeStates(), domain.observeDismissed()) { states, dismissed ->
        val today = domain.today()
        val currency = Globals.PRIMARY_CURRENCY
        val (monthly, yearly) = RecurringDomain.totals(states, currency)
        val upcoming = RecurringDomain.upcoming(states)
        val horizon = today.plusDays(RecurringDomain.SOON_DAYS)

        RecurringUiState(
            isLoading = false,
            currency = currency,
            monthly = Money.format(monthly, currency, decimals = false),
            yearly = Money.format(yearly, currency, decimals = false),
            alerts = alertsOf(states, dismissed, today),
            soon = upcoming.filter { !it.nextDue!!.isAfter(horizon) }.map { rowOf(it, today) },
            later = upcoming.filter { it.nextDue!!.isAfter(horizon) }.map { rowOf(it, today) }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecurringUiState()
    )

    private fun alertsOf(states: List<SeriesState>, dismissed: Set<String>, today: LocalDate): List<RecurringAlert> {
        val active = states.filter { it.series.status == SeriesStatus.ACTIVE }
        val raised = active.filter { it.raisedTo != null && !it.series.cancelReminder }.map {
            RecurringAlert.PriceUp(
                seriesId = it.series.id,
                name = it.series.name,
                from = Money.format(it.series.amountMinor, it.series.currency, decimals = false),
                to = Money.format(it.raisedTo!!, it.series.currency, decimals = false),
                toMinor = it.raisedTo,
                every = it.series.every,
                unit = it.series.unit
            )
        }
        val missed = active.filter { it.missed }.map {
            RecurringAlert.Missed(it.series.id, it.series.name, shortDateLabel(it.nextDue!!, today))
        }
        val proposed = states.filter { it.series.status == SeriesStatus.PROPOSED }.map {
            RecurringAlert.Proposed(
                seriesId = it.series.id,
                name = it.series.name,
                kind = it.series.kind,
                amount = Money.format(it.series.amountMinor, it.series.currency),
                currency = it.series.currency,
                every = it.series.every,
                unit = it.series.unit
            )
        }
        val upcoming = active.mapNotNull { state ->
            val heads = Recurring.headsUp(state, today) ?: return@mapNotNull null
            val key = Recurring.headsUpKey(state)
            if (key in dismissed) return@mapNotNull null
            RecurringAlert.Upcoming(
                seriesId = state.series.id,
                key = key,
                name = state.series.name,
                firstCharge = heads == HeadsUp.FIRST_CHARGE,
                due = shortDateLabel(state.nextDue!!, today),
                amount = Money.format(state.series.amountMinor, state.series.currency),
                currency = state.series.currency
            )
        }
        return raised + missed + upcoming + proposed
    }

    private fun rowOf(state: SeriesState, today: LocalDate): SeriesRow {
        val series = state.series
        return SeriesRow(
            id = series.id,
            merchantId = series.merchantId,
            name = series.name,
            initial = initialOf(series.name),
            kind = series.kind,
            every = series.every,
            unit = series.unit,
            autoRenew = series.autoRenew,
            priceUp = state.raisedTo != null,
            due = shortDateLabel(state.nextDue!!, today),
            endsOn = series.endsOn?.let(::monthYearLabel),
            reminderDays = series.reminderDays,
            amount = Money.format(-(state.raisedTo ?: series.amountMinor), series.currency),
            currency = series.currency
        )
    }

    fun onBackClick() = navigator.popBackStack()

    fun onAddClick() = navigator.navigate(Screen.EditRecurring())

    fun onSeriesClick(id: Long) = navigator.navigate(Screen.EditRecurring(id))

    fun onConfirm(id: Long) {
        viewModelScope.launch { domain.confirm(id) }
    }

    fun onDismiss(id: Long) {
        viewModelScope.launch { domain.dismiss(id) }
    }

    fun onKeepPrice(alert: RecurringAlert.PriceUp) {
        viewModelScope.launch { domain.keepPrice(alert.seriesId, alert.toMinor) }
    }

    fun onRemindToCancel(alert: RecurringAlert.PriceUp) {
        viewModelScope.launch { domain.remindToCancel(alert.seriesId, alert.toMinor) }
    }

    fun onKeep(alert: RecurringAlert.Upcoming) {
        viewModelScope.launch { domain.keep(alert.key) }
    }

    /** Before a renewal or a trial's end: remind me to cancel, at the price it has. */
    fun onRemindToCancel(alert: RecurringAlert.Upcoming) {
        viewModelScope.launch { domain.remindToCancel(alert.seriesId, null) }
    }
}
