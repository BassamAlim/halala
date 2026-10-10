package bassamalim.halala.features.insights

import bassamalim.halala.core.data.dataSources.room.entities.TransactionPlace
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.data.repositories.PlacesRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.domain.Budgets
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.Places
import bassamalim.halala.core.domain.Spot
import bassamalim.halala.core.places.LocationAccess
import bassamalim.halala.core.places.PlaceCapture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** One share of a month's spending: a category (or a merchant) and what went to it. */
data class Share(val id: Long?, val name: String?, val minor: Long)

/** The ledger the charts read: every transaction, and the places kept for purchases. */
data class InsightsLedger(val details: List<TransactionDetail>, val places: List<TransactionPlace>)

/**
 * The charts on Activity's Insights: a month's spending by month, category and merchant, how it
 * built up through the month, and where it was spent. Spending is money out that counts in
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

        private fun TransactionDetail.month(zone: ZoneId) = YearMonth.from(transaction.occurredAt.atZone(zone))

        /** The first month anything was spent, for how far back the bars go; null with none. */
        fun firstMonth(spending: List<TransactionDetail>, zone: ZoneId): YearMonth? = spending.minOfOrNull { it.month(zone) }

        /** What was spent in each of the [count] months up to [last], oldest first. */
        fun byMonth(spending: List<TransactionDetail>, last: YearMonth, count: Int, zone: ZoneId): List<Pair<YearMonth, Long>> {
            val sums = spending.groupBy { it.month(zone) }.mapValues { (_, list) -> Money.sum(list.map { it.yourMinor }) }
            return (count - 1 downTo 0).map { last.minusMonths(it.toLong()) }.map { it to (sums[it] ?: 0L) }
        }

        /** [month]'s spending grouped by [key], biggest first; a group with no name is ungrouped (unfiled). */
        private fun shares(
            spending: List<TransactionDetail>,
            month: YearMonth,
            zone: ZoneId,
            key: (TransactionDetail) -> Pair<Long?, String?>
        ): List<Share> = spending
            .filter { it.month(zone) == month }
            .groupBy(key)
            .map { (k, list) -> Share(k.first, k.second, Money.sum(list.map { it.yourMinor })) }
            .filter { it.minor > 0 }
            .sortedByDescending { it.minor }

        /** By category, every one, biggest first; unfiled has no id and no name. */
        fun categories(spending: List<TransactionDetail>, month: YearMonth, zone: ZoneId): List<Share> =
            shares(spending, month, zone) { it.transaction.categoryId to it.categoryName }

        /** By category, the biggest [SLICES] and then everything else as one share with no id and name "" (Other). */
        fun byCategory(spending: List<TransactionDetail>, month: YearMonth, zone: ZoneId): List<Share> {
            val all = categories(spending, month, zone)
            if (all.size <= SLICES + 1) return all
            return all.take(SLICES) + Share(null, "", Money.sum(all.drop(SLICES).map { it.minor }))
        }

        /** The [count] merchants most was spent at, by your name for each. */
        fun topMerchants(spending: List<TransactionDetail>, month: YearMonth, zone: ZoneId, count: Int): List<Share> =
            shares(spending.filter { it.merchantId != null }, month, zone) { it.merchantId to it.merchantName }.take(count)

        /** [month]'s purchases that have a place, for the map. */
        fun placed(details: List<TransactionDetail>, places: List<TransactionPlace>, currency: String, month: YearMonth, zone: ZoneId): List<Spot> =
            Places.spots(details, places, currency) { it.month(zone) == month }

        /**
         * Spending to the end of each day of [month] (to [today] in the month under way), and the
         * month before to the same day of its own, for comparing the pace.
         */
        fun cumulative(spending: List<TransactionDetail>, month: YearMonth, today: LocalDate, zone: ZoneId): Pair<List<Long>, List<Long>> {
            val days = if (month == YearMonth.from(today)) today.dayOfMonth else month.lengthOfMonth()
            fun running(m: YearMonth): List<Long> {
                val perDay = spending.filter { it.month(zone) == m }
                    .groupBy { it.transaction.occurredAt.atZone(zone).dayOfMonth }
                    .mapValues { (_, list) -> Money.sum(list.map { it.yourMinor }) }
                return (1..m.lengthOfMonth()).runningFold(0L) { total, day -> Math.addExact(total, perDay[day] ?: 0L) }.drop(1)
            }
            val now = running(month).take(days)
            val before = running(month.minusMonths(1)).let { prev -> List(days) { prev[minOf(it, prev.size - 1)] } }
            return now to before
        }
    }
}
