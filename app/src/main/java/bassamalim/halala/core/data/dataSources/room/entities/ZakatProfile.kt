package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * How you work out zakat: your zakat day in the Hijri year ([hijriMonth] 1–12, [hijriDay]
 * 1–30), today's price of a gram of 24k gold (exact decimal text; a gold asset's price stands
 * in until you give one), which parts of your wealth count (opinions differ), debts due now
 * beyond what you owe people, the Hijri year you last paid for, and whether to remind you two
 * weeks before. One row ([id] 1).
 */
@Entity(tableName = "zakat_profile")
data class ZakatProfile(
    @PrimaryKey val id: Long = 1,
    val hijriMonth: Int? = null,
    val hijriDay: Int? = null,
    val goldPricePerGram: String? = null,
    val includeAccounts: Boolean = true,
    val includeSavings: Boolean = true,
    val includeFunds: Boolean = true,
    val includeGold: Boolean = true,
    val includeOwed: Boolean = true,
    val otherDebtsMinor: Long = 0,
    val paidHijriYear: Int? = null,
    val remind: Boolean = false
)
