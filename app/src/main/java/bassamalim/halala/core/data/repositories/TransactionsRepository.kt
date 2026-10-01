package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.AccountsDao
import bassamalim.halala.core.data.dataSources.room.daos.TransactionsDao
import bassamalim.halala.core.data.dataSources.room.entities.InternalTransfer
import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionSource
import bassamalim.halala.core.models.TransactionDraft
import bassamalim.halala.core.models.TransferDraft
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only way into the transactions table. Every amount is checked positive here, and every
 * transaction takes its account's currency here, so neither can be got wrong by a caller.
 */
@Singleton
class TransactionsRepository @Inject constructor(
    private val transactionsDao: TransactionsDao,
    private val accountsDao: AccountsDao,
    private val clock: Clock
) {

    fun observeAll(): Flow<List<TransactionDetail>> = transactionsDao.observeAllDetails()

    fun observe(id: Long): Flow<TransactionDetail?> = transactionsDao.observeDetail(id)

    suspend fun get(id: Long): Transaction? = transactionsDao.get(id)

    suspend fun getAll(): List<Transaction> = transactionsDao.getAll()

    suspend fun getAllTransfers(): List<InternalTransfer> = transactionsDao.getAllTransfers()

    /** The pairing this transaction is a leg of, if it is one. */
    suspend fun getTransferFor(id: Long): InternalTransfer? = transactionsDao.getTransferFor(id)

    suspend fun add(draft: TransactionDraft): Long {
        require(draft.amountMinor > 0) { "Amounts are positive; the direction carries the sign." }

        return transactionsDao.insert(
            Transaction(
                uid = UUID.randomUUID().toString(),
                accountId = draft.accountId,
                direction = draft.direction,
                amountMinor = draft.amountMinor,
                currency = currencyOf(draft.accountId),
                occurredAt = draft.occurredAt,
                kind = draft.kind,
                title = draft.title.trim(),
                note = draft.note.trim(),
                source = draft.source,
                createdAt = clock.instant()
            )
        )
    }

    /** Rewrites one transaction in place; its id, uid, source and creation time are kept. */
    suspend fun update(id: Long, draft: TransactionDraft) {
        require(draft.amountMinor > 0) { "Amounts are positive; the direction carries the sign." }
        val existing = checkNotNull(transactionsDao.get(id)) { "No transaction $id" }

        transactionsDao.update(
            existing.copy(
                accountId = draft.accountId,
                direction = draft.direction,
                amountMinor = draft.amountMinor,
                currency = currencyOf(draft.accountId),
                occurredAt = draft.occurredAt,
                kind = draft.kind,
                title = draft.title.trim(),
                note = draft.note.trim()
            )
        )
    }

    /**
     * A move between two of your accounts: a debit on one, a credit on the other, paired. Both
     * accounts must hold the same currency (converting needs a rate, which comes later).
     * Returns the sending leg's id.
     */
    suspend fun addTransfer(draft: TransferDraft): Long {
        val (outLeg, inLeg) = legsOf(draft)
        return transactionsDao.insertPair(outLeg, inLeg, pairUid = UUID.randomUUID().toString())
    }

    /** Rewrites both legs of the pair [anyLegId] belongs to. */
    suspend fun updateTransfer(anyLegId: Long, draft: TransferDraft) {
        val pair = checkNotNull(transactionsDao.getTransferFor(anyLegId)) { "$anyLegId isn't a move" }
        val oldOut = checkNotNull(transactionsDao.get(pair.outTransactionId))
        val oldIn = checkNotNull(transactionsDao.get(pair.inTransactionId))
        val (outLeg, inLeg) = legsOf(draft)

        transactionsDao.updatePair(
            outLeg.copy(id = oldOut.id, uid = oldOut.uid, createdAt = oldOut.createdAt),
            inLeg.copy(id = oldIn.id, uid = oldIn.uid, createdAt = oldIn.createdAt)
        )
    }

    /**
     * Records what an SMS said. [transaction] is built by the SMS pipeline, which has already
     * matched its currency to the account; that and the sign are checked again here.
     */
    suspend fun addParsed(transaction: Transaction): Long {
        check(transaction)
        return transactionsDao.insert(transaction)
    }

    /** Both legs of a move one SMS described ("between your accounts"). Returns the sending leg's id. */
    suspend fun addParsedPair(outLeg: Transaction, inLeg: Transaction): Long {
        check(outLeg)
        check(inLeg)
        require(outLeg.direction == Direction.DEBIT && inLeg.direction == Direction.CREDIT)
        return transactionsDao.insertPair(outLeg, inLeg, pairUid = UUID.randomUUID().toString())
    }

    /** Pairs two recorded legs as one move between your accounts, so neither counts in totals. */
    suspend fun pair(outId: Long, inId: Long, confidence: Double) {
        transactionsDao.insertTransfer(
            InternalTransfer(
                uid = UUID.randomUUID().toString(),
                outTransactionId = outId,
                inTransactionId = inId,
                matchConfidence = confidence
            )
        )
    }

    private suspend fun check(transaction: Transaction) {
        require(transaction.amountMinor > 0) { "Amounts are positive; the direction carries the sign." }
        require(transaction.currency == currencyOf(transaction.accountId)) { "Not the account's currency." }
    }

    /** Deletes a transaction, and the other leg with it when it is half of a move. */
    suspend fun delete(id: Long) = transactionsDao.deleteWithCounterpart(id)

    private suspend fun legsOf(draft: TransferDraft): Pair<Transaction, Transaction> {
        require(draft.amountMinor > 0) { "Amounts are positive; the direction carries the sign." }
        require(draft.fromAccountId != draft.toAccountId) { "A move needs two different accounts." }

        val currency = currencyOf(draft.fromAccountId)
        require(currency == currencyOf(draft.toAccountId)) { "Both accounts must share a currency." }

        val now = clock.instant()
        val outLeg = Transaction(
            uid = UUID.randomUUID().toString(),
            accountId = draft.fromAccountId,
            direction = Direction.DEBIT,
            amountMinor = draft.amountMinor,
            currency = currency,
            occurredAt = draft.occurredAt,
            kind = draft.kind,
            title = draft.title.trim(),
            note = draft.note.trim(),
            source = TransactionSource.MANUAL,
            createdAt = now
        )
        val inLeg = outLeg.copy(
            uid = UUID.randomUUID().toString(),
            accountId = draft.toAccountId,
            direction = Direction.CREDIT
        )
        return outLeg to inLeg
    }

    private suspend fun currencyOf(accountId: Long): String =
        checkNotNull(accountsDao.get(accountId)) { "No account $accountId" }.currency
}
