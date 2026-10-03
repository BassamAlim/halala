package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.Asset
import bassamalim.halala.core.data.dataSources.room.entities.GoalContribution
import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.enums.AssetType
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

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

    private fun marked(day: String, minor: Long, direction: Direction, withdrawn: Boolean, moveTo: Long? = null) =
        GoalContribution(uid = day, goalId = 1, transactionId = 0, withdrawn = withdrawn) to TransactionDetail(
            transaction = Transaction(
                uid = day, accountId = 1, direction = direction, amountMinor = minor, currency = "SAR",
                occurredAt = Instant.parse("${day}T10:00:00Z"), kind = TransactionKind.SAVINGS_DEPOSIT,
                source = TransactionSource.SMS, createdAt = Instant.EPOCH
            ),
            accountNickname = "", institutionName = null, counterpartId = moveTo, counterpartAccountId = moveTo,
            counterpartNickname = null, counterpartInstitutionName = null, isTransferInLeg = false
        )

    @Test
    fun `money put toward a goal outside your accounts is savings until it comes back`() {
        val held = NetWorth.heldElsewhere(
            listOf(
                marked("2026-09-01", 500_000, Direction.DEBIT, withdrawn = false),
                marked("2026-09-02", 300_000, Direction.DEBIT, withdrawn = false, moveTo = 2), // a move: already in balances
                marked("2026-09-03", 100_000, Direction.CREDIT, withdrawn = false), // arrived in your account: already there
                marked("2026-10-01", 700_000, Direction.CREDIT, withdrawn = true) // 200,000 of it profit
            ),
            "SAR", ZoneOffset.UTC
        )
        assertEquals(mapOf(LocalDate.parse("2026-09-01") to 500_000L, LocalDate.parse("2026-10-01") to -500_000L), held)
        assertEquals(500_000L, NetWorth.now(emptyList(), emptyList(), emptyList(), "SAR", today, 500_000).parts[WealthClass.SAVINGS])
    }
}
