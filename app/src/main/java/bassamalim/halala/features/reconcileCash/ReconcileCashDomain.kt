package bassamalim.halala.features.reconcileCash

import bassamalim.halala.core.data.dataSources.room.relations.AccountWithBalance
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.domain.CashGap
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import bassamalim.halala.core.models.TransactionDraft
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

class ReconcileCashDomain @Inject constructor(
    private val accountsRepository: AccountsRepository,
    private val transactionsRepository: TransactionsRepository,
    private val clock: Clock
) {

    fun observeAccount(id: Long): Flow<AccountWithBalance?> = accountsRepository.observe(id)

    /** Records the gap, if there is one, as a single transaction timed now. */
    suspend fun reconcile(accountId: Long, recordedMinor: Long, countedMinor: Long) {
        val draft = draftFor(accountId, CashGap.of(recordedMinor, countedMinor), clock.instant())
            ?: return

        transactionsRepository.add(draft)
    }

    companion object {

        /**
         * Less cash than recorded is spending nobody wrote down (it counts in totals, and awaits
         * a category); more is a correction, which counts as neither income nor spending.
         */
        fun draftFor(accountId: Long, gap: CashGap, at: Instant): TransactionDraft? = when (gap) {
            CashGap.None -> null

            is CashGap.Spent -> TransactionDraft(
                accountId = accountId,
                direction = Direction.DEBIT,
                amountMinor = gap.amountMinor,
                occurredAt = at,
                kind = TransactionKind.PURCHASE,
                title = CashGap.SPENT_TITLE,
                source = TransactionSource.RECONCILE
            )

            is CashGap.Found -> TransactionDraft(
                accountId = accountId,
                direction = Direction.CREDIT,
                amountMinor = gap.amountMinor,
                occurredAt = at,
                kind = TransactionKind.ADJUSTMENT,
                title = CashGap.FOUND_TITLE,
                source = TransactionSource.RECONCILE
            )
        }
    }
}
