package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant

/** A foreign amount in the account's currency, and the rate it was taken at (account units per foreign unit). */
data class Conversion(val amountMinor: Long, val rate: BigDecimal)

/**
 * What a foreign charge cost in the account's currency when the bank's SMS gave only the foreign
 * amount. The surest rate is your own: the one a bank used for a charge whose SMS gave both
 * amounts, the nearest in time (it carries the card's fee too). Without one, a currency pegged
 * to the dollar converts at its peg plus a typical card fee. Anything else can't be estimated.
 */
object ForeignRates {

    /**
     * Saudi cards add about 2–2.75% on a foreign charge; the middle of that, until your own SMS
     * show the real one.
     */
    // ponytail: one fee for every card; learn it per card from quoted charges if estimates drift.
    val CARD_FEE: BigDecimal = BigDecimal("0.025")

    /** Riyals per unit: SAR is pegged at 3.75 to the dollar, as these are. */
    private val SAR_PER_UNIT: Map<String, BigDecimal> = mapOf(
        "SAR" to BigDecimal.ONE,
        "USD" to BigDecimal("3.75"),
        "AED" to perDollar("3.6725"),
        "QAR" to perDollar("3.64"),
        "BHD" to perDollar("0.376"),
        "OMR" to perDollar("0.3845"),
        "JOD" to perDollar("0.709")
    )

    private fun perDollar(units: String) = BigDecimal("3.75").divide(BigDecimal(units), MathContext.DECIMAL128)

    /**
     * [originalMinor] of [original] in [currency], charged at [at]: by the nearest of [quoted]
     * (charges whose SMS gave both, from [original] into [currency]), else by the pegs; null when
     * neither can say.
     */
    fun estimate(originalMinor: Long, original: String, currency: String, at: Instant, quoted: List<Transaction>): Conversion? {
        val nearest = quoted
            .filter { it.originalCurrency == original && it.currency == currency && (it.originalAmountMinor ?: 0) > 0 && !it.estimated }
            .minByOrNull { Duration.between(it.occurredAt, at).abs() }
        val rate = nearest?.let { rateOf(it.originalAmountMinor!!, original, it.amountMinor, currency) }
            ?: pegged(original, currency)
            ?: return null
        val amount = BigDecimal.valueOf(originalMinor, Money.fractionDigits(original))
            .multiply(rate)
            .setScale(Money.fractionDigits(currency), RoundingMode.HALF_UP)
            .unscaledValue().longValueExact()
        return Conversion(amount, rate)
    }

    /** The rate a charge was converted at: [amountMinor] of [currency] per unit of [original]. */
    fun rateOf(originalMinor: Long, original: String, amountMinor: Long, currency: String): BigDecimal =
        BigDecimal.valueOf(amountMinor, Money.fractionDigits(currency))
            .divide(BigDecimal.valueOf(originalMinor, Money.fractionDigits(original)), MathContext.DECIMAL64)

    private fun pegged(original: String, currency: String): BigDecimal? {
        val from = SAR_PER_UNIT[original] ?: return null
        val to = SAR_PER_UNIT[currency] ?: return null
        return from.divide(to, MathContext.DECIMAL128).multiply(BigDecimal.ONE + CARD_FEE)
    }

    /** "3.8304": a rate as Transaction detail shows it, four places. */
    fun label(rate: BigDecimal): String = rate.setScale(4, RoundingMode.HALF_UP).toPlainString()
}
