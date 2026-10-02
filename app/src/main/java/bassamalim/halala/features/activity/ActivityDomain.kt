package bassamalim.halala.features.activity

import bassamalim.halala.core.data.dataSources.room.relations.AccountWithBalance
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.domain.feedOf
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

class ActivityDomain @Inject constructor(
    private val accountsRepository: AccountsRepository,
    private val transactionsRepository: TransactionsRepository,
    private val clock: Clock
) {

    fun observeAccounts(): Flow<List<AccountWithBalance>> = accountsRepository.observeAll()

    fun observeTransactions(): Flow<List<TransactionDetail>> = transactionsRepository.observeAll()

    fun zone(): ZoneId = clock.zone

    fun today(): LocalDate = LocalDate.now(clock)

    companion object {

        /**
         * The feed for one account (or all of them), narrowed by a search over what you wrote,
         * the merchant it was at (by your name for it or the bank's), and which account it was
         * on. Case-insensitive; blank matches everything.
         */
        fun filter(
            details: List<TransactionDetail>,
            accountId: Long?,
            query: String
        ): List<TransactionDetail> {
            val needle = query.trim()

            return feedOf(details, accountId).filter { detail ->
                needle.isEmpty() || listOfNotNull(
                    detail.transaction.title,
                    detail.merchantName,
                    detail.transaction.note,
                    detail.accountNickname,
                    detail.institutionName,
                    detail.counterpartNickname
                ).any { it.contains(needle, ignoreCase = true) }
            }
        }

        /** This calendar month's transactions (pay cycles arrive with budgets). */
        fun thisMonth(details: List<TransactionDetail>, zone: ZoneId, today: LocalDate) =
            details.filter { YearMonth.from(it.transaction.occurredAt.atZone(zone)) == YearMonth.from(today) }
    }
}
