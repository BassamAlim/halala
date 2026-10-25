package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.Deposit
import bassamalim.halala.core.data.dataSources.room.entities.GoalContribution
import bassamalim.halala.core.data.dataSources.room.entities.SavingsGoal
import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class GoalsTest {

    private val today = LocalDate.parse("2026-10-14")

    @Test
    fun `what is left over the months to the target month, rounded up`() {
        val goal = SavingsGoal(uid = "g", name = "Fund", targetMinor = 6_000_000, currency = "SAR", targetDate = LocalDate.parse("2027-03-01"), accountIds = listOf(1), createdAt = Instant.EPOCH)
        val state = Goals.stateOf(goal, 4_200_000, 1_020_000, today)
        // Oct to Mar is six months.
        assertEquals(300_000L, state.neededMonthlyMinor)
        assertEquals(340_000L, state.averageMonthlyMinor)
        assertEquals(1, Goals.perMonth(1, 6))
        assertEquals(0, Goals.perMonth(-5, 6))
        assertNull(Goals.stateOf(goal.copy(targetDate = null), 0, 0, today).neededMonthlyMinor)
    }

    /** A move of [minor] from account 1 to [to] (leg [id], its pair [pairId]), as its sending leg. */
    private fun sent(id: Long, pairId: Long, to: Long, minor: Long = 500_000) = TransactionDetail(
        transaction = Transaction(
            id = id, uid = "t$id", accountId = 1, direction = Direction.DEBIT, amountMinor = minor, currency = "SAR",
            occurredAt = Instant.parse("2026-10-01T10:00:00Z"), kind = TransactionKind.INTERNAL_TRANSFER,
            source = TransactionSource.SMS, createdAt = Instant.EPOCH
        ),
        accountNickname = "", institutionName = null, counterpartId = pairId, counterpartAccountId = to,
        counterpartNickname = null, counterpartInstitutionName = null, isTransferInLeg = false
    )

    @Test
    fun `a deposit's transfer goes toward the deposit's goal, never a second time as a mark`() {
        val goal = SavingsGoal(id = 1, uid = "g", name = "Retirement", targetMinor = 10_000_000, currency = "SAR", targetDate = null, accountIds = emptyList(), createdAt = Instant.EPOCH)
        val types = mapOf(1L to AccountType.CURRENT, 2L to AccountType.DEPOSIT, 3L to AccountType.SAVINGS)
        val awaeed = sent(10, pairId = 11, to = 2)
        val broker = sent(20, pairId = 21, to = 3, minor = 200_000)
        val deposit = Deposit(id = 1, uid = "d", transactionId = 11, goalId = 1)

        // Found by either leg; another move isn't one.
        assertEquals(deposit, Goals.depositOf(awaeed, listOf(deposit)))
        assertEquals(deposit, Goals.depositOf(awaeed.copy(transaction = awaeed.transaction.copy(id = 11), counterpartId = 10), listOf(deposit)))
        assertNull(Goals.depositOf(broker, listOf(deposit)))
        assertTrue(Goals.touchesDeposits(awaeed, types))
        assertFalse(Goals.touchesDeposits(broker, types))

        val marks = listOf(
            GoalContribution(uid = "a", goalId = 1, transactionId = 10) to awaeed,
            GoalContribution(uid = "b", goalId = 1, transactionId = 20) to broker
        )
        assertEquals(200_000L, Goals.contributed(goal, marks, holding = setOf(2)))
        assertEquals(700_000L, Goals.contributed(goal, marks))
    }
}
