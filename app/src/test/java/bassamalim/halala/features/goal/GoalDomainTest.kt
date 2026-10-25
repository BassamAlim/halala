package bassamalim.halala.features.goal

import bassamalim.halala.core.data.dataSources.room.entities.Deposit
import bassamalim.halala.core.data.dataSources.room.entities.GoalContribution
import bassamalim.halala.core.data.dataSources.room.entities.SavingsGoal
import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class GoalDomainTest {

    private fun leg(id: Long, account: Long, day: Int, pair: Pair<Long, Long>? = null, inLeg: Boolean = false) = TransactionDetail(
        transaction = Transaction(
            id = id, uid = "t$id", accountId = account, direction = if (inLeg) Direction.CREDIT else Direction.DEBIT,
            amountMinor = 100_000, currency = "SAR", occurredAt = Instant.parse("2026-10-%02dT10:00:00Z".format(day)),
            kind = TransactionKind.INTERNAL_TRANSFER, source = TransactionSource.SMS, createdAt = Instant.EPOCH
        ),
        accountNickname = "", institutionName = null, counterpartId = pair?.first, counterpartAccountId = pair?.second,
        counterpartNickname = null, counterpartInstitutionName = null, isTransferInLeg = inLeg
    )

    @Test
    fun `what went into a goal is its accounts, its deposits' transfers and its marks, each once, newest first`() {
        // 1 current, 2 the goal's savings account, 3 the deposits' holding account, 4 a broker's.
        val goal = SavingsGoal(id = 1, uid = "g", name = "House", targetMinor = 1, currency = "SAR", targetDate = null, accountIds = listOf(2), createdAt = Instant.EPOCH)
        val details = listOf(
            leg(10, 1, day = 1, pair = 11L to 2), leg(11, 2, day = 1, pair = 10L to 1, inLeg = true), // into the savings account
            leg(20, 1, day = 2, pair = 21L to 3), leg(21, 3, day = 2, pair = 20L to 1, inLeg = true), // an Awaeed for it
            leg(30, 1, day = 3, pair = 31L to 3), leg(31, 3, day = 3, pair = 30L to 1, inLeg = true), // an Awaeed for another
            leg(40, 1, day = 4), // to the broker, marked
            leg(50, 1, day = 5) // spending
        )
        val deposits = listOf(Deposit(uid = "a", transactionId = 21, goalId = 1), Deposit(uid = "b", transactionId = 31, goalId = 2))
        val marks = listOf(GoalContribution(uid = "c", goalId = 1, transactionId = 40))

        assertEquals(listOf(40L, 20L, 11L), GoalDomain.transactionsOf(goal, details, deposits, marks).map { it.transaction.id })
    }
}
