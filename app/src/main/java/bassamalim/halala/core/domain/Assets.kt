package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.Asset
import bassamalim.halala.core.enums.AssetType
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * What assets are worth, in exact decimals rounded once, half up, to the currency's minor units.
 * Quantities and prices are decimal text; a value that can't be read is worth nothing rather
 * than a guess.
 */
object Assets {

    /** Karats gold is sold in; 24 is pure. */
    val KARATS = listOf(24, 22, 21, 18)

    /** A decimal as typed: digits and one point, Arabic-Indic digits accepted, grouping commas dropped. */
    fun decimal(text: String?): BigDecimal? {
        if (text.isNullOrBlank()) return null
        val latin = text.trim().map { c ->
            when (c) {
                in '٠'..'٩' -> '0' + (c - '٠')
                '٫' -> '.'
                else -> c
            }
        }.joinToString("").replace(",", "").replace("٬", "")
        return latin.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }
    }

    /** What [asset] is worth on [today], in minor units of its currency. */
    fun valueOf(asset: Asset, today: LocalDate): Long {
        val scale = Money.fractionDigits(asset.currency)
        val major: BigDecimal = when (asset.type) {
            AssetType.FUND -> {
                val units = decimal(asset.quantity) ?: return 0
                val price = decimal(asset.unitPrice) ?: return 0
                units * price
            }
            AssetType.GOLD -> {
                val grams = decimal(asset.quantity) ?: return 0
                val price = decimal(asset.unitPrice) ?: return 0
                val purity = BigDecimal(asset.karat ?: 24).divide(BigDecimal(24), MathContext.DECIMAL64)
                val keep = BigDecimal.ONE - (decimal(asset.spreadPercent) ?: BigDecimal.ZERO).movePointLeft(2)
                grams * purity * price * keep.max(BigDecimal.ZERO)
            }
            else -> {
                val value = BigDecimal.valueOf(asset.valueMinor ?: return 0).movePointLeft(scale)
                val rate = decimal(asset.depreciationPercent)?.movePointLeft(2)
                val since = asset.priceDate
                if (rate == null || since == null || rate.signum() == 0) value
                else {
                    val years = ChronoUnit.DAYS.between(since, today).coerceAtLeast(0).toDouble() / 365.0
                    value * BigDecimal.valueOf(Math.pow((BigDecimal.ONE - rate).max(BigDecimal.ZERO).toDouble(), years))
                }
            }
        }
        return major.setScale(scale, RoundingMode.HALF_UP).movePointRight(scale).longValueExact()
    }

    /** How much gold [assets] hold, as grams of 24k (for zakat's nisab and its weight). */
    fun pureGoldGrams(assets: List<Asset>): BigDecimal = assets
        .filter { it.type == AssetType.GOLD }
        .mapNotNull { asset -> decimal(asset.quantity)?.let { it * BigDecimal(asset.karat ?: 24).divide(BigDecimal(24), MathContext.DECIMAL64) } }
        .fold(BigDecimal.ZERO, BigDecimal::add)

    /** Gain or loss against what was paid; null when you didn't say what you paid. */
    fun gainOf(asset: Asset, today: LocalDate): Long? = asset.costMinor?.let { valueOf(asset, today) - it }
}
