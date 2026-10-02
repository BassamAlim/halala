package bassamalim.halala.core.data.dataSources.room.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import bassamalim.halala.core.data.dataSources.room.entities.InternalTransfer
import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.enums.ExpenseType
import kotlinx.coroutines.flow.Flow

/** Every transaction column plus the names and pairing needed to show it. */
private const val DETAIL_SELECT = """
        SELECT t.*, a.nickname AS accountNickname, i.name AS institutionName,
            o.id AS counterpartId, o.accountId AS counterpartAccountId,
            oa.nickname AS counterpartNickname, oi.name AS counterpartInstitutionName,
            (x.inTransactionId IS NOT NULL AND x.inTransactionId = t.id) AS isTransferInLeg,
            c.name AS categoryName, m.id AS merchantId, m.name AS merchantName,
            m.businessType AS merchantType, m.identifiedBy AS merchantIdentifiedBy,
            m.confidence AS merchantConfidence, p.id AS personId, p.name AS personName,
            (SELECT COALESCE(SUM(e.amountMinor), 0) FROM loans l JOIN loan_events e ON e.loanId = l.id
                WHERE l.splitOf = t.id AND e.type = 'DISBURSEMENT') AS sharedMinor
        FROM transactions t
        JOIN accounts a ON a.id = t.accountId
        LEFT JOIN institutions i ON i.id = a.institutionId
        LEFT JOIN internal_transfers x ON x.outTransactionId = t.id OR x.inTransactionId = t.id
        LEFT JOIN transactions o ON o.id =
            CASE WHEN x.outTransactionId = t.id THEN x.inTransactionId ELSE x.outTransactionId END
        LEFT JOIN accounts oa ON oa.id = o.accountId
        LEFT JOIN institutions oi ON oi.id = oa.institutionId
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN merchant_aliases ma ON ma.aliasKey = t.merchantKey AND t.merchantKey != ''
        LEFT JOIN merchants m ON m.id = ma.merchantId
        LEFT JOIN person_aliases pa ON pa.aliasKey = t.merchantKey AND t.merchantKey != ''
            AND x.id IS NULL AND t.kind IN ($PERSON_KINDS)
        LEFT JOIN people p ON p.id = pa.personId
    """

@Dao
interface TransactionsDao {

    @Query("$DETAIL_SELECT ORDER BY t.occurredAt DESC, t.id DESC")
    fun observeAllDetails(): Flow<List<TransactionDetail>>

    @Query("$DETAIL_SELECT WHERE t.id = :id")
    fun observeDetail(id: Long): Flow<TransactionDetail?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): Transaction?

    @Query("SELECT * FROM transactions ORDER BY occurredAt, id")
    suspend fun getAll(): List<Transaction>

    @Query("SELECT * FROM internal_transfers ORDER BY id")
    suspend fun getAllTransfers(): List<InternalTransfer>

    @Query(
        "SELECT * FROM internal_transfers WHERE outTransactionId = :transactionId " +
                "OR inTransactionId = :transactionId"
    )
    suspend fun getTransferFor(transactionId: Long): InternalTransfer?

    /**
     * What a rule may file: named money out that isn't half of a move, and that you haven't
     * filed yourself (nothing chosen yet, or chosen by a rule).
     */
    @Query(
        """
        SELECT * FROM transactions t
        WHERE t.title != '' AND t.direction = 'DEBIT' AND (t.categoryId IS NULL OR t.ruleId IS NOT NULL)
            AND NOT EXISTS (
                SELECT 1 FROM internal_transfers x WHERE x.outTransactionId = t.id OR x.inTransactionId = t.id
            )
        """
    )
    suspend fun getRuleCandidates(): List<Transaction>

    @Query("UPDATE transactions SET categoryId = :categoryId, expenseType = :expenseType, ruleId = :ruleId WHERE id IN (:ids)")
    suspend fun setCategory(ids: List<Long>, categoryId: Long?, expenseType: ExpenseType?, ruleId: Long?)

    /** With its category gone, a transaction's type goes too, so it comes back to review whole. */
    @Query("UPDATE transactions SET expenseType = NULL WHERE categoryId = :categoryId")
    suspend fun clearTypeOf(categoryId: Long)

    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Insert
    suspend fun insertTransfer(transfer: InternalTransfer): Long

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Both legs and their pairing land together or not at all. Returns the sending leg's id. */
    @androidx.room.Transaction
    suspend fun insertPair(outLeg: Transaction, inLeg: Transaction, pairUid: String): Long {
        val outId = insert(outLeg)
        val inId = insert(inLeg)
        insertTransfer(
            InternalTransfer(uid = pairUid, outTransactionId = outId, inTransactionId = inId)
        )
        return outId
    }

    @androidx.room.Transaction
    suspend fun updatePair(outLeg: Transaction, inLeg: Transaction) {
        update(outLeg)
        update(inLeg)
    }

    /** A move is one thing to you, so deleting either leg deletes both (the pair cascades). */
    @androidx.room.Transaction
    suspend fun deleteWithCounterpart(id: Long) {
        val pair = getTransferFor(id)
        deleteById(id)
        if (pair != null)
            deleteById(if (pair.outTransactionId == id) pair.inTransactionId else pair.outTransactionId)
    }
}
