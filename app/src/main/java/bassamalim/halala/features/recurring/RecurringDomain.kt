package bassamalim.halala.features.recurring

import bassamalim.halala.core.data.repositories.AlertsRepository
import bassamalim.halala.core.data.repositories.RecurringRepository
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.SeriesState
import bassamalim.halala.core.enums.SeriesStatus
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

class RecurringDomain @Inject constructor(
    private val recurringRepository: RecurringRepository,
    private val alertsRepository: AlertsRepository,
    private val clock: Clock
) {

    fun observeStates(): Flow<List<SeriesState>> = recurringRepository.observeStates()

    /** The heads-ups you said "keep it" to. */
    fun observeDismissed(): Flow<Set<String>> = alertsRepository.observeDismissed()

    suspend fun keep(key: String) = alertsRepository.dismiss(key)

    suspend fun detect() = recurringRepository.detect()

    suspend fun confirm(id: Long) = recurringRepository.setStatus(id, SeriesStatus.ACTIVE)

    suspend fun dismiss(id: Long) = recurringRepository.setStatus(id, SeriesStatus.DISMISSED)

    suspend fun keepPrice(id: Long, amountMinor: Long) = recurringRepository.acceptPrice(id, amountMinor)

    suspend fun remindToCancel(id: Long, amountMinor: Long?) = recurringRepository.remindToCancel(id, amountMinor)

    fun today(): LocalDate = LocalDate.now(clock)

    companion object {

        /** The board's "Next 30 days": due within this many days of today. */
        const val SOON_DAYS = 30L

        /** What the active, running series cost, in [currency]: (a month, a year). */
        fun totals(states: List<SeriesState>, currency: String): Pair<Long, Long> {
            val running = states.filter { it.series.status == SeriesStatus.ACTIVE && it.series.currency == currency }
            val yearly = Money.sum(running.map { it.yearlyMinor })
            return Money.sum(running.map { it.monthlyMinor }) to yearly
        }

        /** The active ones still running, the soonest due first. */
        fun upcoming(states: List<SeriesState>): List<SeriesState> = states
            .filter { it.series.status == SeriesStatus.ACTIVE && it.nextDue != null }
            .sortedWith(compareBy({ it.nextDue }, { it.series.name.lowercase() }))
    }
}
