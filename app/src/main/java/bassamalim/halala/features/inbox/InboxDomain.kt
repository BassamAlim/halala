package bassamalim.halala.features.inbox

import bassamalim.halala.core.data.repositories.AlertsRepository
import bassamalim.halala.core.data.repositories.PreferencesRepository
import bassamalim.halala.core.data.repositories.RecurringRepository
import bassamalim.halala.core.data.repositories.TagsRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.domain.Recurring
import bassamalim.halala.core.domain.Rules
import bassamalim.halala.core.domain.SeriesState
import bassamalim.halala.core.domain.Tags
import bassamalim.halala.core.enums.SeriesStatus
import bassamalim.halala.features.people.PeopleDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** What waits for your say, each kind where it is answered. */
enum class InboxKind { MERCHANTS, ALERTS, RECURRING, PEOPLE, TRIPS }

/**
 * The inbox: how much of each kind is waiting. Nothing is stored or decided here; each count is
 * what its own screen would show, and that screen is where it is answered.
 */
class InboxDomain @Inject constructor(
    private val transactionsRepository: TransactionsRepository,
    private val alertsRepository: AlertsRepository,
    private val recurringRepository: RecurringRepository,
    private val tagsRepository: TagsRepository,
    private val preferencesRepository: PreferencesRepository,
    private val people: PeopleDomain,
    private val clock: Clock
) {

    fun observeCounts(): Flow<Map<InboxKind, Int>> = combine(
        transactionsRepository.observeAll(),
        alertsRepository.observeAlerts(),
        combine(recurringRepository.observeStates(), alertsRepository.observeDismissed(), ::Pair),
        people.observeSuggestions(),
        combine(tagsRepository.observeAll(), tagsRepository.observeRows(), preferencesRepository.observeDismissedTagSuggestions(), ::Triple)
    ) { details, alerts, (series, kept), merges, (tags, rows, dismissed) ->
        val today = LocalDate.now(clock)
        mapOf(
            InboxKind.MERCHANTS to Rules.clusters(details).size,
            InboxKind.ALERTS to alerts.size,
            InboxKind.RECURRING to recurring(series, kept, today),
            InboxKind.PEOPLE to merges.size,
            InboxKind.TRIPS to Tags.suggest(details, tags, Tags.byTransaction(rows), dismissed, today, clock.zone).size
        ).filterValues { it > 0 }
    }

    companion object {
        /**
         * The cards Subscriptions and bills opens with: a price rise, a missed charge, a renewal or
         * a trial's end you haven't [kept], one that was found.
         */
        fun recurring(states: List<SeriesState>, kept: Set<String>, today: LocalDate): Int = states.sumOf { state ->
            when (state.series.status) {
                SeriesStatus.PROPOSED -> 1
                SeriesStatus.ACTIVE -> (if (state.raisedTo != null && !state.series.cancelReminder) 1 else 0) +
                        (if (state.missed) 1 else 0) +
                        (if (Recurring.headsUp(state, today) != null && Recurring.headsUpKey(state) !in kept) 1 else 0)
                else -> 0
            }
        }
    }
}
