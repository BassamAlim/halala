package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.data.dataSources.room.entities.TransactionPlace
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import java.time.Duration
import java.time.Instant
import kotlin.math.floor

/** One purchase on the map: where, and how much (minor units, for the heat only). */
data class Spot(val transactionId: Long, val latitude: Double, val longitude: Double, val amountMinor: Long, val merchant: String)

/** A place you spend at: purchases within about 200 m of each other, named by their usual merchant. */
data class TopPlace(val key: String, val name: String, val count: Int, val spentMinor: Long, val latitude: Double, val longitude: Double)

/**
 * Where you spend. A purchase's place is where the phone was when its SMS arrived, so only
 * purchases that just happened take one (not a back-import, not a card charged later).
 */
object Places {

    /** An SMS this long after the purchase is too late to say where it was. */
    val FRESH: Duration = Duration.ofMinutes(30)
    /** A fix less sure than this is no use. */
    const val MAX_ACCURACY_METERS = 500f
    /** About 200 m of latitude: the grid places are gathered on. */
    private const val CELL_DEGREES = 0.002

    /** The purchases recorded since [since] that just happened and have no place yet. */
    fun needingPlace(transactions: List<Transaction>, placed: Set<Long>, since: Instant, now: Instant): List<Transaction> =
        transactions.filter {
            it.kind == TransactionKind.PURCHASE && it.direction == Direction.DEBIT && it.id !in placed &&
                !it.createdAt.isBefore(since) && !it.occurredAt.isBefore(now - FRESH)
        }

    fun spots(details: List<TransactionDetail>, places: List<TransactionPlace>, currency: String, include: (TransactionDetail) -> Boolean): List<Spot> {
        val byId = places.associateBy { it.transactionId }
        return details.filter { Budgets.isSpending(it) && it.transaction.currency == currency && include(it) }
            .mapNotNull { d ->
                byId[d.transaction.id]?.let {
                    Spot(d.transaction.id, it.latitude, it.longitude, d.yourMinor, d.merchantName ?: d.transaction.title)
                }
            }
    }

    fun top(spots: List<Spot>, count: Int = 5): List<TopPlace> = spots
        .groupBy { "${floor(it.latitude / CELL_DEGREES).toLong()}:${floor(it.longitude / CELL_DEGREES).toLong()}" }
        .map { (key, here) ->
            TopPlace(
                key = key,
                name = here.groupingBy { it.merchant }.eachCount().maxByOrNull { it.value }!!.key,
                count = here.size,
                spentMinor = Money.sum(here.map { it.amountMinor }),
                latitude = here.map { it.latitude }.average(),
                longitude = here.map { it.longitude }.average()
            )
        }
        .sortedByDescending { it.spentMinor }
        .take(count)
}
