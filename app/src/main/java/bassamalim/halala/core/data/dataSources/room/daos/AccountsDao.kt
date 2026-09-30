package bassamalim.halala.core.data.dataSources.room.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import bassamalim.halala.core.data.dataSources.room.entities.Account
import bassamalim.halala.core.data.dataSources.room.relations.AccountWithBalance
import kotlinx.coroutines.flow.Flow

/**
 * Every account column plus its bank's name and its balance: the opening balance plus every
 * credit less every debit, summed in SQL over integers, so no amount passes through a float.
 */
private const val WITH_BALANCE_SELECT = """
    SELECT a.*, i.name AS institutionName,
        a.openingBalanceMinor + COALESCE((
            SELECT SUM(CASE WHEN t.direction = 'CREDIT' THEN t.amountMinor ELSE -t.amountMinor END)
            FROM transactions t WHERE t.accountId = a.id
        ), 0) AS balanceMinor,
        (SELECT COUNT(*) FROM transactions t WHERE t.accountId = a.id) AS transactionCount
    FROM accounts a
    LEFT JOIN institutions i ON i.id = a.institutionId
"""

/** Active accounts first, the cash wallet at their head, then by bank and name. */
private const val LIST_ORDER =
    "ORDER BY a.archived, a.type = 'CASH' DESC, i.name COLLATE NOCASE, a.nickname COLLATE NOCASE"

@Dao
interface AccountsDao {

    @Query("$WITH_BALANCE_SELECT $LIST_ORDER")
    fun observeAllWithBalance(): Flow<List<AccountWithBalance>>

    @Query("$WITH_BALANCE_SELECT $LIST_ORDER")
    suspend fun getAllWithBalance(): List<AccountWithBalance>

    @Query("$WITH_BALANCE_SELECT WHERE a.id = :id")
    fun observeWithBalance(id: Long): Flow<AccountWithBalance?>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: Long): Account?

    @Query("SELECT * FROM accounts ORDER BY id")
    suspend fun getAll(): List<Account>

    /** The wallet seeded with the database; the first cash account if there are several. */
    @Query("SELECT * FROM accounts WHERE type = 'CASH' ORDER BY archived, id LIMIT 1")
    suspend fun getCashWallet(): Account?

    @Query("SELECT * FROM accounts WHERE institutionId = :institutionId AND last4 = :last4")
    suspend fun findByLast4(institutionId: Long, last4: String): Account?

    @Insert
    suspend fun insert(account: Account): Long

    @Update
    suspend fun update(account: Account)

    @Query("UPDATE accounts SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)
}
