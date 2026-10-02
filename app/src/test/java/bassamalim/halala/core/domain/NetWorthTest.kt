package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.Asset
import bassamalim.halala.core.enums.AssetType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class NetWorthTest {

    private val today = LocalDate.parse("2026-10-14")

    private fun asset(type: AssetType, quantity: String? = null, karat: Int? = null, price: String? = null, value: Long? = null, spread: String? = null, depreciation: String? = null, since: LocalDate? = null) =
        Asset(uid = "a", type = type, name = "x", quantity = quantity, karat = karat, unitPrice = price, valueMinor = value,
            spreadPercent = spread, depreciationPercent = depreciation, priceDate = since, currency = "SAR", createdAt = Instant.EPOCH)

    @Test
    fun `funds are units at the unit price, rounded once`() {
        // 1,234.5678 × 12.3456 = 15,241.48023168 → 15,241.48
        assertEquals(1_524_148, Assets.valueOf(asset(AssetType.FUND, "1,234.5678", price = "12.3456"), today))
    }

    @Test
    fun `gold is grams by purity at the 24k price, less the spread`() {
        // 95 g × 21/24 × 400 × 0.97 = 32,252.50
        assertEquals(3_225_250, Assets.valueOf(asset(AssetType.GOLD, "95", 21, "400", spread = "3"), today))
        assertEquals(BigDecimal("83.125"), Assets.pureGoldGrams(listOf(asset(AssetType.GOLD, "95", 21))).setScale(3))
    }

    @Test
    fun `a car loses its yearly depreciation, compounded`() {
        val car = asset(AssetType.VEHICLE, value = 10_000_000, depreciation = "10", since = today.minusDays(365))
        assertEquals(9_000_000, Assets.valueOf(car, today))
        assertEquals(10_000_000, Assets.valueOf(car.copy(depreciationPercent = null), today))
    }

    @Test
    fun `an unreadable quantity is worth nothing, not a guess`() {
        assertEquals(0, Assets.valueOf(asset(AssetType.FUND, "abc", price = "1"), today))
        assertEquals(null, Assets.decimal("-5"))
        assertEquals(BigDecimal("62.5"), Assets.decimal("٦٢٫٥"))
    }
}
