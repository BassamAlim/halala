package bassamalim.halala.features.insights

import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.domain.PayCycle
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import bassamalim.halala.features.categorySpending.CategorySpendingDomain
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class InsightsDomainTest {

    private val zone = ZoneOffset.UTC

    /** A calendar month as a cycle, as when no salary has come. */
    private fun month(year: Int, month: Int) = YearMonth.of(year, month).atDay(1).let { PayCycle(it, it.plusMonths(1), false, null) }

    private fun tx(day: String, minor: Long, category: Long? = 1, merchant: Long? = null, direction: Direction = Direction.DEBIT) =
        TransactionDetail(
            transaction = Transaction(
                uid = day + minor, accountId = 1, direction = direction, amountMinor = minor, currency = "SAR",
                occurredAt = Instant.parse("${day}T10:00:00Z"), kind = TransactionKind.PURCHASE, source = TransactionSource.SMS,
                createdAt = Instant.EPOCH, categoryId = category
            ),
            accountNickname = "", institutionName = null, counterpartId = null, counterpartAccountId = null,
            counterpartNickname = null, counterpartInstitutionName = null, isTransferInLeg = false,
            categoryName = category?.let { "C$it" }, merchantId = merchant, merchantName = merchant?.let { "M$it" }
        )

    @Test
    fun `cycles count spending only`() {
        val spending = InsightsDomain.spending(
            listOf(tx("2026-08-03", 500), tx("2026-10-01", 200), tx("2026-10-02", 900, direction = Direction.CREDIT)),
            "SAR"
        )
        val cycles = listOf(month(2026, 8), month(2026, 9), month(2026, 10))
        assertEquals(cycles.zip(listOf(500L, 0L, 200L)), InsightsDomain.byCycle(spending, cycles, zone))
        // Salary on the 27th: the 3 Aug charge is in the cycle from 27 Jul.
        val paid = PayCycle(LocalDate.parse("2026-07-27"), LocalDate.parse("2026-08-27"), true, 1)
        assertEquals(listOf(paid to 500L), InsightsDomain.byCycle(spending, listOf(paid), zone))
    }

    @Test
    fun `categories past the fifth become Other`() {
        val month = month(2026, 10)
        val details = (1L..7L).map { tx("2026-10-0$it", it * 100, it) } + tx("2026-10-08", 50, null)
        val shares = InsightsDomain.byCategory(details, month, zone)
        assertEquals(listOf("C7", "C6", "C5", "C4", "C3", ""), shares.map { it.name })
        // C2, C1 and the unfiled 50.
        assertEquals(350L, shares.last().minor)
    }

    @Test
    fun `a category's month is its spending in that month only, unfiled as no category`() {
        val details = listOf(
            tx("2026-10-01", 300, 1), tx("2026-10-02", 400, 2), tx("2026-09-30", 500, 1),
            tx("2026-10-03", 600, null), tx("2026-10-04", 700, 1, direction = Direction.CREDIT)
        )
        val october = month(2026, 10)
        fun amounts(category: Long?) =
            CategorySpendingDomain.of(details, category, october, "SAR", zone).map { it.transaction.amountMinor }
        assertEquals(listOf(300L), amounts(1))
        assertEquals(listOf(600L), amounts(null))
        assertEquals(listOf(null, "C2", "C1"), InsightsDomain.categories(InsightsDomain.spending(details, "SAR"), october, zone).map { it.name })
    }

    @Test
    fun `top merchants leave out what has none`() {
        val details = listOf(tx("2026-10-01", 300, merchant = 1), tx("2026-10-02", 400, merchant = 2), tx("2026-10-03", 900))
        assertEquals(listOf(2L, 1L), InsightsDomain.topMerchants(details, month(2026, 10), zone, 5).map { it.id })
    }

    @Test
    fun `the cycle under way runs to today, against the cycle before to the same day`() {
        val details = listOf(tx("2026-09-01", 100), tx("2026-09-30", 50), tx("2026-10-02", 70))
        val (now, before) = InsightsDomain.cumulative(details, month(2026, 10), month(2026, 9), LocalDate.parse("2026-10-03"), zone)
        assertEquals(listOf(0L, 70L, 70L), now)
        assertEquals(listOf(100L, 100L, 100L), before)
        // March's 31st compares with February's last day.
        val (_, feb) = InsightsDomain.cumulative(listOf(tx("2026-02-28", 10)), month(2026, 3), month(2026, 2), LocalDate.parse("2026-10-03"), zone)
        assertEquals(10L, feb.last())
        // From the 27th: the first days are the 27th on.
        val cycle = PayCycle(LocalDate.parse("2026-09-27"), LocalDate.parse("2026-10-27"), true, 1)
        val (fromSalary, _) = InsightsDomain.cumulative(details, cycle, month(2026, 9), LocalDate.parse("2026-10-03"), zone)
        assertEquals(listOf(0L, 0L, 0L, 50L, 50L, 120L, 120L), fromSalary)
    }
}
