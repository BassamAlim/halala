package bassamalim.halala.features.goal

import bassamalim.halala.core.data.dataSources.room.entities.Deposit
import bassamalim.halala.core.data.dataSources.room.entities.GoalContribution
import bassamalim.halala.core.data.dataSources.room.entities.SavingsGoal
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.data.repositories.GoalsRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.domain.GoalState
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** One savings goal and the transactions that went into or out of it. */
class GoalDomain @Inject constructor(
    private val goalsRepository: GoalsRepository,
    private val transactionsRepository: TransactionsRepository,
    private val clock: Clock
) {

    fun observeStates(): Flow<List<GoalState>> = goalsRepository.observeStates()

    fun observeTransactions(): Flow<List<TransactionDetail>> = transactionsRepository.observeAll()

    fun observeDeposits(): Flow<List<Deposit>> = goalsRepository.observeDeposits()

    fun observeContributions(): Flow<List<GoalContribution>> = goalsRepository.observeContributions()

    fun zone(): ZoneId = clock.zone

    fun today(): LocalDate = LocalDate.now(clock)

    companion object {

        /**
         * What went into or out of [goal], newest first: every transaction on the accounts it is
         * saved in (a move between two of them once, by its sending leg), the transfer that opened
         * each deposit filed under it, and what was marked toward it. Each transaction once.
         */
        fun transactionsOf(
            goal: SavingsGoal,
            details: List<TransactionDetail>,
            deposits: List<Deposit>,
            contributions: List<GoalContribution>
        ): List<TransactionDetail> {
            val ids = goal.accountIds.toSet()
            val byId = details.associateBy { it.transaction.id }
            val inAccounts = details.filter { detail ->
                detail.transaction.accountId in ids && !(detail.isTransferInLeg && detail.counterpartAccountId in ids)
            }
            // A deposit is the leg that arrived in the bank's holding account; the move shows by its sending leg.
            val opened = deposits.filter { it.goalId == goal.id }.mapNotNull { byId[it.transactionId] }
                .map { leg -> leg.counterpartId?.takeIf { leg.isTransferInLeg }?.let(byId::get) ?: leg }
            val marked = contributions.filter { it.goalId == goal.id }.mapNotNull { byId[it.transactionId] }
            return (inAccounts + opened + marked)
                .distinctBy { it.transaction.id }
                .sortedWith(compareByDescending<TransactionDetail> { it.transaction.occurredAt }.thenByDescending { it.transaction.id })
        }
    }
}
