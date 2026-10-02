package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.Tag
import bassamalim.halala.core.data.dataSources.room.entities.TransactionTag
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.enums.Direction
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale

/**
 * A tag the spending suggests: a run of purchases in another currency is probably a trip.
 * [country] is where that currency is spent, when it is only one country's; [key] names the
 * suggestion so a "no" is remembered.
 */
data class TagSuggestion(
    val key: String,
    val currency: String,
    val country: String?,
    val from: LocalDate,
    val to: LocalDate,
    val count: Int,
    /** Still going: its last purchase was in the last few days. */
    val ongoing: Boolean,
    val ids: List<Long>
)

/** Tags: which transactions carry one, what an automatic tag covers, and the suggestions. */
object Tags {

    /** A gap longer than this between two purchases abroad ends a trip. */
    const val TRIP_GAP_DAYS = 7L
    /** A run needs this many purchases to read as a trip. */
    const val TRIP_MIN = 3
    /** How far back suggestions look. */
    const val LOOK_BACK_DAYS = 120L
    /** A trip with a purchase this recently is still going. */
    const val ONGOING_DAYS = 3L

    /** Whether [tag] takes the transaction on [day] by itself. */
    fun covers(tag: Tag, day: LocalDate, today: LocalDate): Boolean {
        if (!tag.auto || tag.startsOn == null) return false
        return !day.isBefore(tag.startsOn) && !day.isAfter(tag.endsOn ?: today)
    }

    /** The tags each transaction carries (rows you removed aside), by transaction id. */
    fun byTransaction(rows: List<TransactionTag>): Map<Long, Set<Long>> =
        rows.filter { !it.removed }.groupBy { it.transactionId }.mapValues { (_, list) -> list.map { it.tagId }.toSet() }

    /** The one country [currency] is spent in, in English, or null when it is several or none. */
    fun countryOf(currency: String): String? = Locale.getISOCountries()
        .filter { runCatching { Currency.getInstance(Locale.Builder().setRegion(it).build()).currencyCode }.getOrNull() == currency }
        .singleOrNull()
        ?.let { Locale.Builder().setRegion(it).build().getDisplayCountry(Locale.US) }

    fun suggest(
        details: List<TransactionDetail>,
        tags: List<Tag>,
        tagged: Map<Long, Set<Long>>,
        dismissed: Set<String>,
        today: LocalDate,
        zone: ZoneId
    ): List<TagSuggestion> {
        fun day(d: TransactionDetail) = d.transaction.occurredAt.atZone(zone).toLocalDate()
        val abroad = details.filter {
            val original = it.transaction.originalCurrency
            it.transaction.direction == Direction.DEBIT && original != null && original != it.transaction.currency &&
                !day(it).isBefore(today.minusDays(LOOK_BACK_DAYS))
        }
        return abroad.groupBy { it.transaction.originalCurrency!! }.flatMap { (currency, list) ->
            val runs = mutableListOf<MutableList<TransactionDetail>>()
            for (d in list.sortedBy { it.transaction.occurredAt }) {
                val last = runs.lastOrNull()?.last()
                if (last == null || ChronoUnit.DAYS.between(day(last), day(d)) > TRIP_GAP_DAYS) runs += mutableListOf(d) else runs.last() += d
            }
            runs.filter { it.size >= TRIP_MIN }.map { run ->
                val from = day(run.first())
                TagSuggestion(
                    key = "trip:$currency:$from",
                    currency = currency,
                    country = countryOf(currency),
                    from = from,
                    to = day(run.last()),
                    count = run.size,
                    ongoing = !day(run.last()).isBefore(today.minusDays(ONGOING_DAYS)),
                    ids = run.map { it.transaction.id }
                )
            }
        }
            .filter { it.key !in dismissed }
            // Already tagged: most of it carries a tag, or a tag's days cover it.
            .filter { s -> s.ids.count { tagged[it].orEmpty().isNotEmpty() } * 2 < s.ids.size }
            .filter { s -> tags.none { t -> t.startsOn != null && !t.startsOn.isAfter(s.to) && !(t.endsOn ?: today).isBefore(s.from) } }
            .sortedByDescending { it.from }
    }
}
