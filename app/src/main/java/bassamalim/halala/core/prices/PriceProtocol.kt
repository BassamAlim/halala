package bassamalim.halala.core.prices

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A fund as the market lists it: its price (NAV per unit) and the day of that price. */
data class ListedFund(val id: Long, val name: String, val manager: String?, val price: BigDecimal, val date: LocalDate?)

/** Gold today: a gram of 24k in SAR, and the day. */
data class GoldPrice(val perGram: BigDecimal, val date: LocalDate)

/**
 * What Halala asks the market for and reads back, as pure functions. Nothing of yours is sent:
 * gold is one public price, and funds are the whole list (so not even which you hold).
 */
object PriceProtocol {

    /** Gold in US dollars a troy ounce. */
    const val GOLD_URL = "https://api.gold-api.com/price/XAU"

    /** Every Saudi fund with its latest price (Mubasher's list, in English). */
    const val FUNDS_URL = "https://english.mubasher.info/api/1/funds?size=2000"

    /** The riyal is pegged: 3.75 to the dollar. */
    private val SAR_PER_USD = BigDecimal("3.75")
    private val GRAMS_PER_TROY_OUNCE = BigDecimal("31.1034768")
    private const val PRICE_SCALE = 4

    private val json = Json
    private val FUND_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.US)

    // Prices arrive as JSON numbers: read as their exact text, never through a double.
    private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    fun gold(body: String): GoldPrice {
        val quote = json.parseToJsonElement(body).jsonObject
        require(quote.text("currency") == "USD") { "Gold not quoted in dollars" }
        val ounce = quote.text("price")?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: error("No gold price")
        return GoldPrice(
            perGram = ounce.multiply(SAR_PER_USD).divide(GRAMS_PER_TROY_OUNCE, PRICE_SCALE, RoundingMode.HALF_UP),
            date = quote.text("updatedAt")?.let { runCatching { Instant.parse(it).atZone(ZoneOffset.UTC).toLocalDate() }.getOrNull() }
                ?: error("No date")
        )
    }

    fun funds(body: String): List<ListedFund> = json.parseToJsonElement(body).jsonObject["rows"]!!.jsonArray.mapNotNull { element ->
        val row = element.jsonObject
        val price = row.text("price")?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return@mapNotNull null
        ListedFund(
            id = row.text("fundId")?.toLongOrNull() ?: return@mapNotNull null,
            name = row.text("name")?.trim().orEmpty().ifEmpty { return@mapNotNull null },
            manager = row.text("owner")?.trim(),
            price = price.stripTrailingZeros(),
            date = row.text("date")?.let { runCatching { LocalDate.parse(it.trim(), FUND_DATE) }.getOrNull() }
        )
    }

    /** An asset's source: the market gold price, or one listed fund. */
    const val GOLD_SOURCE = "gold"
    private const val FUND_PREFIX = "fund:"

    fun fundSource(id: Long) = "$FUND_PREFIX$id"

    fun fundIdOf(source: String?): Long? = source?.removePrefix(FUND_PREFIX)?.takeIf { source.startsWith(FUND_PREFIX) }?.toLongOrNull()
}
