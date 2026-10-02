package bassamalim.halala.core.data.dataSources.room.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import bassamalim.halala.core.data.dataSources.room.entities.AccountRef
import bassamalim.halala.core.data.dataSources.room.entities.BalanceCheckpoint
import bassamalim.halala.core.data.dataSources.room.entities.RawMessage
import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.data.dataSources.room.relations.SmsStats
import bassamalim.halala.core.data.dataSources.room.relations.UnroutedGroup
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.RawStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** The SMS side of the ledger: raw messages, the digits learned per bank, reported balances. */
@Dao
interface SmsDao {

    /** -1 when the message is already stored (same hash). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRaw(message: RawMessage): Long

    @Query("SELECT * FROM raw_messages WHERE id = :id")
    suspend fun getRaw(id: Long): RawMessage?

    /** Oldest first, so the first leg of a move is recorded before the second looks for it. */
    @Query("SELECT id FROM raw_messages WHERE status IN (:statuses) ORDER BY receivedAt, id")
    suspend fun getRawIds(statuses: List<RawStatus>): List<Long>

    @Query(
        "UPDATE raw_messages SET status = :status, parserVersion = :parserVersion, " +
                "unroutedRefs = :unroutedRefs WHERE id = :id"
    )
    suspend fun setStatus(id: Long, status: RawStatus, parserVersion: Int, unroutedRefs: String?)

    @Query(
        "SELECT sender, COALESCE(unroutedRefs, '') AS refs, COUNT(*) AS count FROM raw_messages " +
                "WHERE status = 'UNROUTED' GROUP BY sender, refs ORDER BY count DESC"
    )
    fun observeUnrouted(): Flow<List<UnroutedGroup>>

    /** How much has been read, for the "Your history is in" summary. */
    @Query(
        "SELECT (SELECT COUNT(*) FROM raw_messages) AS messages, " +
                "(SELECT COUNT(*) FROM transactions WHERE rawMessageId IS NOT NULL) AS transactions, " +
                "(SELECT MIN(receivedAt) FROM raw_messages) AS since"
    )
    fun observeStats(): Flow<SmsStats>

    @Query("SELECT * FROM raw_messages ORDER BY id")
    suspend fun getAllRaw(): List<RawMessage>

    @Query("SELECT * FROM balance_checkpoints ORDER BY id")
    suspend fun getCheckpoints(): List<BalanceCheckpoint>

    @Query("SELECT * FROM account_refs")
    suspend fun getRefs(): List<AccountRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRef(ref: AccountRef)

    @Insert
    suspend fun insertCheckpoint(checkpoint: BalanceCheckpoint)

    /** SMS transactions like this one, close in time: one may be the same event sent twice. */
    @Query(
        "SELECT t.* FROM transactions t WHERE t.accountId = :accountId " +
                "AND t.direction = :direction AND t.amountMinor = :amountMinor " +
                "AND t.occurredAt BETWEEN :from AND :to AND t.rawMessageId IS NOT NULL"
    )
    suspend fun findSimilar(
        accountId: Long,
        direction: Direction,
        amountMinor: Long,
        from: Instant,
        to: Instant
    ): List<Transaction>

    /**
     * The lowest an account's transactions alone ever brought it, up to [until]: below zero, it
     * must have started with at least that much. Null with none.
     */
    @Query(
        "SELECT MIN(run) FROM (SELECT SUM(CASE WHEN direction = 'CREDIT' THEN amountMinor ELSE -amountMinor END) " +
                "OVER (ORDER BY occurredAt, id) AS run " +
                "FROM transactions WHERE accountId = :accountId AND occurredAt <= :until)"
    )
    suspend fun lowestBalance(accountId: Long, until: Instant): Long?

    /** Arrivals from SMS, up to [until], that no sending leg was ever paired with. */
    @Query(
        "SELECT t.* FROM transactions t WHERE t.kind = 'TRANSFER_IN' AND t.direction = 'CREDIT' " +
                "AND t.rawMessageId IS NOT NULL AND t.occurredAt <= :until AND t.id NOT IN " +
                "(SELECT inTransactionId FROM internal_transfers) ORDER BY t.occurredAt"
    )
    suspend fun unpairedArrivals(until: Instant): List<Transaction>

    /** Messages that recorded nothing (OTPs among them) received in a span of time. */
    @Query("SELECT * FROM raw_messages WHERE status = 'IGNORED' AND receivedAt BETWEEN :from AND :to")
    suspend fun ignoredBetween(from: Instant, to: Instant): List<RawMessage>

    /** The one of [accountIds] with the most transactions; null when none has any. */
    @Query(
        "SELECT accountId FROM transactions WHERE accountId IN (:accountIds) " +
                "GROUP BY accountId ORDER BY COUNT(*) DESC, accountId LIMIT 1"
    )
    suspend fun busiestOf(accountIds: List<Long>): Long?

    /** Which of [accountIds] already had a transaction by [at]: the accounts in use then. */
    @Query("SELECT DISTINCT accountId FROM transactions WHERE accountId IN (:accountIds) AND occurredAt <= :at")
    suspend fun inUseBy(accountIds: List<Long>, at: Instant): List<Long>

    /** Unpaired SMS legs on other accounts that could be the far side of a move. */
    @Query(
        "SELECT t.* FROM transactions t WHERE t.accountId != :accountId " +
                "AND t.direction = :direction AND t.amountMinor BETWEEN :minMinor AND :maxMinor " +
                "AND t.currency = :currency AND t.occurredAt BETWEEN :from AND :to " +
                "AND t.rawMessageId IS NOT NULL AND t.id NOT IN " +
                "(SELECT outTransactionId FROM internal_transfers " +
                "UNION SELECT inTransactionId FROM internal_transfers)"
    )
    suspend fun findUnpaired(
        accountId: Long,
        direction: Direction,
        minMinor: Long,
        maxMinor: Long,
        currency: String,
        from: Instant,
        to: Instant
    ): List<Transaction>
}
