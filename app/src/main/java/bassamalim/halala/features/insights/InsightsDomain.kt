package bassamalim.halala.features.insights

import bassamalim.halala.core.data.dataSources.room.entities.TransactionPlace
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.data.repositories.PlacesRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.domain.Budgets
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.PayCycle
import bassamalim.halala.core.domain.Places
import bassamalim.halala.core.domain.Spot
import bassamalim.halala.core.places.LocationAccess
import bassamalim.halala.core.places.PlaceCapture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.ZoneId
import javax.inject.Inject

/** One share of a pay cycle's spending: a category (or a merchant) and what went to it. */
data class Share(val id: Long?, val name: String?, val minor: Long)

/** The ledger the charts read: every transaction, and the places kept for purchases. */
data class InsightsLedger(val details: List<TransactionDetail>, val places: List<TransactionPlace>)

/**
 * The charts on the Insights tab: a pay cycle's spending by cycle, category and merchant, how it
 * built up through the cycle, and where it was spent. Spending is money out that counts in
 * totals, your share of it, in one currency, as budgets and digests count it.
 */
class InsightsDomain @Inject constructor(
    private val transactionsRepository: TransactionsRepository,
    private val placesRepository: PlacesRepository,
    private val capture: PlaceCapture,
    private val clock: Clock
) {

    fun observeLedger(): Flow<InsightsLedger> =
        combine(transactionsRepository.observeAll(), placesRepository.observeAll(), ::InsightsLedger)

    /** What location Halala has, for whether the map can show. */
    fun locationAccess(): LocationAccess = capture.access()

    fun zone(): ZoneId = clock.zone

    fun today(): LocalDate = LocalDate.now(clock)

    companion object {

        /** Slices the category chart draws before the rest becomes "Other". */
        const val SLICES = 5

        fun spending(details: List<TransactionDetail>, currency: String) =
            details.filter { Budgets.isSpending(it) && it.transaction.currency == currency }

        private fun TransactionDetail.day(zone: ZoneId) = transaction.occurredAt.atZone(zone).toLocalDate()

        private fun List<TransactionDetail>.within(cycle: PayCycle, zone: ZoneId) = filter { cycle.contains(it.day(zone)) }

        /** The first day anything was spent, for how far back the bars go; null with none. */
        fun firstDay(spending: List<TransactionDetail>, zone: ZoneId): LocalDate? = spending.minOfOrNull { it.day(zone) }

        /** What was spent in each of [cycles], in the same order. */
        fun byCycle(spending: List<TransactionDetail>, cycles: List<PayCycle>, zone: ZoneId): List<Pair<PayCycle, Long>> =
            cycles.map { cycle -> cycle to Money.sum(spending.within(cycle, zone).map { it.yourMinor }) }

        /** [cycle]'s spending grouped by [key], biggest first; a group with no name is ungrouped (unfiled). */
        private fun shares(
            spending: List<TransactionDetail>,
            cycle: PayCycle,
            zone: ZoneId,
            key: (TransactionDetail) -> Pair<Long?, String?>
        ): List<Share> = spending
            .within(cycle, zone)
            .groupBy(key)
            .map { (k, list) -> Share(k.first, k.second, Money.sum(list.map { it.yourMinor })) }
            .filter { it.minor > 0 }
            .sortedByDescending { it.minor }

        /** By category, every one, biggest first; unfiled has no id and no name. */
        fun categories(spending: List<TransactionDetail>, cycle: PayCycle, zone: ZoneId): List<Share> =
            shares(spending, cycle, zone) { it.transaction.categoryId to it.categoryName }

        /** By category, the biggest [SLICES] and then everything else as one share with no id and name "" (Other). */
        fun byCategory(spending: List<TransactionDetail>, cycle: PayCycle, zone: ZoneId): List<Share> {
            val all = categories(spending, cycle, zone)
            if (all.size <= SLICES + 1) return all
            return all.take(SLICES) + Share(null, "", Money.sum(all.drop(SLICES).map { it.minor }))
        }

        /** The [count] merchants most was spent at, by your name for each. */
        fun topMerchants(spending: List<TransactionDetail>, cycle: PayCycle, zone: ZoneId, count: Int): List<Share> =
            shares(spending.filter { it.merchantId != null }, cycle, zone) { it.merchantId to it.merchantName }.take(count)

        /** [cycle]'s purchases that have a place, for the map. */
        fun placed(details: List<TransactionDetail>, places: List<TransactionPlace>, currency: String, cycle: PayCycle, zone: ZoneId): List<Spot> =
            Places.spots(details, places, currency) { cycle.contains(it.day(zone)) }

        /**
         * Spending to the end of each day of [cycle] (to [today] in the one under way), and
         * [before] to the same day of its own, for comparing the pace.
         */
        fun cumulative(
            spending: List<TransactionDetail>,
            cycle: PayCycle,
            before: PayCycle,
            today: LocalDate,
            zone: ZoneId
        ): Pair<List<Long>, List<Long>> {
            val days = if (cycle.contains(today)) ChronoUnit.DAYS.between(cycle.start, today).toInt() + 1 else cycle.days.toInt()
            fun running(c: PayCycle): List<Long> {
                val perDay = spending.within(c, zone)
                    .groupBy { ChronoUnit.DAYS.between(c.start, it.day(zone)).toInt() }
                    .mapValues { (_, list) -> Money.sum(list.map { it.yourMinor }) }
                return (0 until c.days.toInt()).runningFold(0L) { total, day -> Math.addExact(total, perDay[day] ?: 0L) }.drop(1)
            }
            val now = running(cycle).take(days)
            val previous = running(before).let { prev -> List(days) { prev[minOf(it, prev.size - 1)] } }
            return now to previous
        }
    }
}
