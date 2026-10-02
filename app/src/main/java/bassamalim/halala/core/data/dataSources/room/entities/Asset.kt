package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import bassamalim.halala.core.enums.AssetType
import java.time.Instant
import java.time.LocalDate

/**
 * Something you own outside your accounts. Quantities and prices are exact decimal text (fund
 * units and a unit price run to four places or more, gold to fractions of a gram); values come
 * out as integer minor units in [currency].
 *
 * - FUND: [quantity] units at [unitPrice] each, priced on [priceDate].
 * - GOLD: [quantity] grams of [karat] gold, [unitPrice] the price of a gram of 24k, less
 *   [spreadPercent] (what a dealer takes on resale).
 * - VEHICLE, PROPERTY, OTHER: [valueMinor] as of [priceDate], less [depreciationPercent] a year.
 *
 * [costMinor] is what you paid, for gain or loss.
 */
@Entity(tableName = "assets", indices = [Index(value = ["uid"], unique = true)])
data class Asset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val type: AssetType,
    val name: String,
    val quantity: String? = null,
    val karat: Int? = null,
    val unitPrice: String? = null,
    val priceDate: LocalDate? = null,
    val valueMinor: Long? = null,
    val costMinor: Long? = null,
    val spreadPercent: String? = null,
    val depreciationPercent: String? = null,
    val currency: String,
    val createdAt: Instant
)
