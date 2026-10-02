package bassamalim.halala.core.data.dataSources.room.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import bassamalim.halala.core.data.dataSources.room.entities.Loan
import bassamalim.halala.core.data.dataSources.room.entities.LoanEvent
import bassamalim.halala.core.data.dataSources.room.relations.LoanEventRow
import bassamalim.halala.core.enums.LoanEventType
import bassamalim.halala.core.enums.TransactionKind
import kotlinx.coroutines.flow.Flow

/** Loans and what happened to each. */
@Dao
interface LoansDao {

    @Query("SELECT * FROM loans ORDER BY id")
    fun observeLoans(): Flow<List<Loan>>

    @Query("$EVENT_SELECT ORDER BY at, e.id")
    fun observeEvents(): Flow<List<LoanEventRow>>

    @Query("SELECT * FROM loans ORDER BY id")
    suspend fun getLoans(): List<Loan>

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun getLoan(id: Long): Loan?

    @Query("$EVENT_SELECT WHERE e.loanId = :loanId ORDER BY at, e.id")
    suspend fun getEventRows(loanId: Long): List<LoanEventRow>

    @Query("SELECT * FROM loan_events ORDER BY id")
    suspend fun getEvents(): List<LoanEvent>

    @Query("SELECT * FROM loan_events WHERE transactionId = :transactionId")
    suspend fun getEventFor(transactionId: Long): LoanEvent?

    /** The person a transfer's title names, by its key. */
    @Query("SELECT personId FROM person_aliases WHERE aliasKey = :key AND :key != ''")
    suspend fun getPersonFor(key: String): Long?

    @Insert
    suspend fun insertLoan(loan: Loan): Long

    @Update
    suspend fun updateLoan(loan: Loan)

    /** Its events go with it (they cascade). */
    @Query("DELETE FROM loans WHERE id = :id")
    suspend fun deleteLoan(id: Long)

    @Insert
    suspend fun insertEvent(event: LoanEvent): Long

    @Query("DELETE FROM loan_events WHERE id = :id")
    suspend fun deleteEvent(id: Long)

    /** What a transfer is now: a loan's kind takes no category, so whatever filed it lets go. */
    @Query("UPDATE transactions SET kind = :kind, categoryId = NULL, expenseType = NULL, ruleId = NULL WHERE id = :id")
    suspend fun setKind(id: Long, kind: TransactionKind)

    /** A transfer becomes part of a loan: the event and the transfer's kind, as one write. */
    @Transaction
    suspend fun link(event: LoanEvent, kind: TransactionKind): Long {
        val id = insertEvent(event)
        event.transactionId?.let { setKind(it, kind) }
        return id
    }

    /** A new loan and the transfer that lent (or borrowed) it, as one write. */
    @Transaction
    suspend fun open(loan: Loan, eventUid: String, transactionId: Long, kind: TransactionKind): Long {
        val loanId = insertLoan(loan)
        link(LoanEvent(uid = eventUid, loanId = loanId, type = LoanEventType.DISBURSEMENT, transactionId = transactionId), kind)
        return loanId
    }

    /** Transfers back to plain transfers ([kinds]: id → kind), and the events or loan gone, as one write. */
    @Transaction
    suspend fun unlink(kinds: Map<Long, TransactionKind>, eventId: Long?, loanId: Long?) {
        eventId?.let { deleteEvent(it) }
        loanId?.let { deleteLoan(it) }
        for ((id, kind) in kinds) setKind(id, kind)
    }
}

private const val EVENT_SELECT = """
        SELECT e.id, e.loanId, e.type, e.transactionId,
            COALESCE(t.amountMinor, e.amountMinor) AS amountMinor, COALESCE(t.occurredAt, e.at) AS at,
            t.kind AS kind, a.nickname AS accountNickname, i.name AS institutionName
        FROM loan_events e
        LEFT JOIN transactions t ON t.id = e.transactionId
        LEFT JOIN accounts a ON a.id = t.accountId
        LEFT JOIN institutions i ON i.id = a.institutionId
    """
