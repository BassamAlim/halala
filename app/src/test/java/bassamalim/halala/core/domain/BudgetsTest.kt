package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.Budget
import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.enums.BudgetScope
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class BudgetsTest {

    private val zone = ZoneOffset.UTC
    private val cycle = PayCycle(LocalDate.parse("2026-09-27"), LocalDate.parse("2026-10-27"), fromSalary = true, salaryMinor = null)
    private val previous = PayCycle(LocalDate.parse("2026-08-26"), LocalDate.parse("2026-09-27"), fromSalary = true, salaryMinor = null)

    private fun spend(day: String, minor: Long, category: Long? = 1, kind: TransactionKind = TransactionKind.PURCHASE, shared: Long = 0) =
        TransactionDetail(
            transaction = Transaction(
                uid = day + minor, accountId = 1, direction = Direction.DEBIT, amountMinor = minor, currency = "SAR",
                occurredAt = Instant.parse("${day}T10:00:00Z"), kind = kind, source = TransactionSource.SMS,
                createdAt = Instant.EPOCH, categoryId = category
            ),
            accountNickname = "", institutionName = null, counterpartId = null, counterpartAccountId = null,
            counterpartNickname = null, counterpartInstitutionName = null, isTransferInLeg = false, sharedMinor = shared
        )

    private fun budget(scope: BudgetScope = BudgetScope.CATEGORY, amount: Long = 200_000, rollover: Boolean = false) =
        Budget(uid = "b", scope = scope, categoryId = 1, amountMinor = amount, currency = "SAR", rollover = rollover, createdAt = Instant.EPOCH)

    private val details = listOf(
        spend("2026-09-28", 100_000),
        spend("2026-10-01", 50_000, shared = 20_000),
        spend("2026-10-02", 30_000, category = 2),
        spend("2026-10-03", 40_000, kind = TransactionKind.LOAN_GIVEN),
        spend("2026-09-01", 150_000)
    )

    @Test
    fun `a budget counts its scope's spending in the cycle, your share of a split`() {
        val status = Budgets.statusOf(budget(), details, cycle, previous, LocalDate.parse("2026-10-14"), zone)
        assertEquals(130_000, status.spentMinor)
        assertEquals(BudgetState.OK, status.state)
        assertEquals(160_000, Budgets.statusOf(budget(BudgetScope.TOTAL), details, cycle, previous, LocalDate.parse("2026-10-14"), zone).spentMinor)
    }

    @Test
    fun `rollover adds what was left last cycle`() {
        val status = Budgets.statusOf(budget(rollover = true), details, cycle, previous, LocalDate.parse("2026-10-14"), zone)
        assertEquals(250_000, status.limitMinor)
    }

    @Test
    fun `spending further through the budget than the cycle is fast`() {
        assertTrue(Budgets.statusOf(budget(), details, cycle, previous, LocalDate.parse("2026-09-30"), zone).paceAhead)
        assertFalse(Budgets.statusOf(budget(), details, cycle, previous, LocalDate.parse("2026-10-20"), zone).paceAhead)
    }
}
