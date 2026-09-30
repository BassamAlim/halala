package bassamalim.halala.core.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

/**
 * Money is a [Long] count of minor units (halalas for SAR, cents for USD) plus an ISO 4217 code.
 * Nothing in the app holds an amount as a float. Text is turned into minor units, and minor units
 * into text, here and only here, through exact decimals.
 *
 * Display follows the design's voice: thousands separators always, the true minus sign (−) for
 * negatives, two decimals in lists and detail, none in summaries. Digits are always Latin with
 * a comma separator, whatever the device locale, so columns read the same everywhere.
 */
object Money {

    /** U+2212, the true minus sign. A hyphen is never shown in front of an amount. */
    const val MINUS = '−'

    /** How many minor units a currency has: 2 for SAR and USD, 0 for JPY, 3 for KWD. */
    fun fractionDigits(currency: String): Int =
        runCatching { Currency.getInstance(currency.uppercase()).defaultFractionDigits }
            .getOrNull()
            ?.takeIf { it >= 0 }
            ?: 2

    /** Whether [code] is a real ISO 4217 currency code. */
    fun isCurrency(code: String): Boolean =
        code.length == 3 && runCatching { Currency.getInstance(code.uppercase()) }.isSuccess

    /**
     * An unsigned amount typed by a person: "1,234.5", "62", ".75", "٦٢٫٥". Returns null for
     * anything that isn't exactly an amount in [currency], including more decimals than it has
     * ("1.234" SAR) and amounts too large for a [Long].
     */
    fun parse(input: String, currency: String): Long? {
        val text = normalizeDigits(input.trim())
        if (!AMOUNT.matches(text)) return null

        return toMinor(text.replace(",", ""), currency)
    }

    /** Like [parse], but a leading minus (either kind) makes it negative: an opening balance. */
    fun parseSigned(input: String, currency: String): Long? {
        val text = normalizeDigits(input.trim())
        val negative = text.startsWith('-') || text.startsWith(MINUS)
        val magnitude = parse(if (negative) text.substring(1) else text, currency) ?: return null

        return if (negative) -magnitude else magnitude
    }

    /**
     * "6,240.00", "−214.50", "+18,000.00" ([showPlus]), or "6,240" with [decimals] false, which
     * rounds half away from zero.
     */
    fun format(
        minor: Long,
        currency: String,
        decimals: Boolean = true,
        showPlus: Boolean = false
    ): String {
        var value = BigDecimal.valueOf(minor, fractionDigits(currency))
        if (!decimals) value = value.setScale(0, RoundingMode.HALF_UP)

        val sign = when {
            value.signum() < 0 -> MINUS.toString()
            showPlus && value.signum() > 0 -> "+"
            else -> ""
        }

        val plain = value.abs().toPlainString()
        val integer = plain.substringBefore('.')
        val fraction = plain.substringAfter('.', missingDelimiterValue = "")

        return buildString {
            append(sign)
            append(group(integer))
            if (fraction.isNotEmpty()) append('.').append(fraction)
        }
    }

    /**
     * For files other programs read (CSV): ASCII hyphen, no grouping, always every decimal.
     * "-1234.50".
     */
    fun plain(minor: Long, currency: String): String =
        BigDecimal.valueOf(minor, fractionDigits(currency)).toPlainString()

    /** A sum that fails loudly on overflow rather than wrapping round to a wrong balance. */
    fun sum(amounts: Iterable<Long>): Long = amounts.fold(0L, Math::addExact)

    private fun toMinor(text: String, currency: String): Long? {
        val decimal = runCatching { BigDecimal(if (text.startsWith('.')) "0$text" else text) }
            .getOrNull() ?: return null

        val digits = fractionDigits(currency)
        if (decimal.scale() > digits) return null

        return runCatching { decimal.movePointRight(digits).longValueExact() }.getOrNull()
    }

    private fun group(integer: String): String =
        integer.reversed().chunked(3).joinToString(",").reversed()

    /** Arabic-Indic and Persian digits and separators, which an Arabic keyboard types. */
    private fun normalizeDigits(text: String): String = buildString(text.length) {
        for (char in text) {
            append(
                when (char) {
                    in '٠'..'٩' -> '0' + (char - '٠')
                    in '۰'..'۹' -> '0' + (char - '۰')
                    '٫' -> '.'
                    '٬' -> ','
                    else -> char
                }
            )
        }
    }

    /**
     * Digits with optional thousands separators in groups of three, or plain digits, then an
     * optional fraction; or a bare fraction.
     */
    private val AMOUNT = Regex("""^(\d{1,3}(,\d{3})+|\d+)(\.\d+)?$|^\.\d+$""")
}
