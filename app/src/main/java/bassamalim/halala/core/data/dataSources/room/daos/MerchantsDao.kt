package bassamalim.halala.core.data.dataSources.room.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import bassamalim.halala.core.data.dataSources.room.entities.Merchant
import bassamalim.halala.core.data.dataSources.room.entities.MerchantAlias
import bassamalim.halala.core.data.dataSources.room.relations.AliasWithCount
import bassamalim.halala.core.data.dataSources.room.relations.KeyRow
import bassamalim.halala.core.data.dataSources.room.relations.MerchantWithStats
import bassamalim.halala.core.data.dataSources.room.relations.ToIdentify
import kotlinx.coroutines.flow.Flow

/** Merchants and the descriptors (aliases) each is known by. */
@Dao
interface MerchantsDao {

    @Query(
        """
        SELECT m.*,
            (SELECT COUNT(*) FROM merchant_aliases a WHERE a.merchantId = m.id) AS aliases,
            (SELECT COUNT(*) FROM transactions t JOIN merchant_aliases a ON a.aliasKey = t.merchantKey
                WHERE a.merchantId = m.id) AS transactions,
            (SELECT MAX(t.occurredAt) FROM transactions t JOIN merchant_aliases a ON a.aliasKey = t.merchantKey
                WHERE a.merchantId = m.id) AS lastAt
        FROM merchants m ORDER BY transactions DESC, m.name COLLATE NOCASE
        """
    )
    fun observeMerchants(): Flow<List<MerchantWithStats>>

    @Query("SELECT * FROM merchants WHERE id = :id")
    fun observeMerchant(id: Long): Flow<Merchant?>

    @Query(
        """
        SELECT a.*, (SELECT COUNT(*) FROM transactions t WHERE t.merchantKey = a.aliasKey) AS transactions
        FROM merchant_aliases a WHERE a.merchantId = :merchantId ORDER BY transactions DESC, a.id
        """
    )
    fun observeAliases(merchantId: Long): Flow<List<AliasWithCount>>

    @Query("SELECT * FROM merchants ORDER BY id")
    suspend fun getMerchants(): List<Merchant>

    @Query("SELECT * FROM merchants ORDER BY id")
    fun observeAll(): Flow<List<Merchant>>

    /**
     * Merchants nobody has identified that have spending nothing has filed, the busiest first,
     * each with the descriptor its first alias was written as.
     */
    @Query(
        """
        SELECT m.id AS merchantId,
            (SELECT a.descriptor FROM merchant_aliases a WHERE a.merchantId = m.id ORDER BY a.id LIMIT 1) AS descriptor
        FROM merchants m
        WHERE m.identifiedBy IS NULL AND $UNFILED
        ORDER BY (SELECT COUNT(*) FROM transactions t JOIN merchant_aliases a ON a.aliasKey = t.merchantKey
            WHERE a.merchantId = m.id) DESC, m.id
        """
    )
    suspend fun getToIdentify(): List<ToIdentify>

    /**
     * Merchants the AI identified at under [below]% sure, not yet looked up online, with spending
     * nothing has filed of at least [floorMinor] in all (small one-offs are never searched), the
     * most money first.
     */
    @Query(
        """
        SELECT m.id AS merchantId,
            (SELECT a.descriptor FROM merchant_aliases a WHERE a.merchantId = m.id ORDER BY a.id LIMIT 1) AS descriptor
        FROM merchants m
        WHERE m.identifiedBy = 'AI' AND m.confidence < :below AND m.searchedOnline = 0 AND $UNFILED
            AND $SPENT >= :floorMinor
        ORDER BY $SPENT DESC, m.id
        """
    )
    suspend fun getToSearch(below: Int, floorMinor: Long): List<ToIdentify>

    @Query("SELECT * FROM merchants WHERE id = :id")
    suspend fun getMerchant(id: Long): Merchant?

    @Insert
    suspend fun insertMerchant(merchant: Merchant): Long

    @Update
    suspend fun updateMerchant(merchant: Merchant)

    /** Its aliases go with it (they cascade). */
    @Query("DELETE FROM merchants WHERE id = :id")
    suspend fun deleteMerchant(id: Long)

    @Query("SELECT * FROM merchant_aliases ORDER BY id")
    suspend fun getAliases(): List<MerchantAlias>

    @Query("SELECT * FROM merchant_aliases WHERE id = :id")
    suspend fun getAlias(id: Long): MerchantAlias?

    /** A key already taken keeps its merchant. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlias(alias: MerchantAlias): Long

    @Update
    suspend fun updateAlias(alias: MerchantAlias)

    @Query("DELETE FROM merchant_aliases WHERE id = :id")
    suspend fun deleteAlias(id: Long)

    @Query("UPDATE merchant_aliases SET merchantId = :intoId WHERE merchantId = :fromId")
    suspend fun moveAliases(fromId: Long, intoId: Long)

    /** Subscriptions and bills follow a merged merchant. */
    @Query("UPDATE recurring_series SET merchantId = :intoId WHERE merchantId = :fromId")
    suspend fun moveSeries(fromId: Long, intoId: Long)

    /** And so do its budgets. */
    @Query("UPDATE budgets SET merchantId = :intoId WHERE merchantId = :fromId")
    suspend fun moveBudgets(fromId: Long, intoId: Long)

    @Query("SELECT id, title, merchantKey, kind, occurredAt FROM transactions ORDER BY occurredAt, id")
    suspend fun getKeyRows(): List<KeyRow>

    @Query("UPDATE transactions SET merchantKey = :key WHERE id = :id")
    suspend fun setMerchantKey(id: Long, key: String)

    /** Many at once, as one write: after the upgrade, every transaction's key is new. */
    @Transaction
    suspend fun setMerchantKeys(keys: Map<Long, String>) {
        for ((id, key) in keys) setMerchantKey(id, key)
    }

    /**
     * What rules filed under one descriptor goes back to review: it is no longer the merchant
     * those rules were taught. What you filed yourself stays.
     */
    @Query("UPDATE transactions SET categoryId = NULL, expenseType = NULL, ruleId = NULL WHERE merchantKey = :key AND ruleId IS NOT NULL")
    suspend fun unfileByRules(key: String)
}

/** The merchant (`m`) has a transaction no category has been chosen for. */
private const val UNFILED = "EXISTS (SELECT 1 FROM transactions t JOIN merchant_aliases a ON a.aliasKey = t.merchantKey " +
        "WHERE a.merchantId = m.id AND t.categoryId IS NULL)"

/** All the money the merchant (`m`) has taken, in minor units. */
private const val SPENT = "(SELECT COALESCE(SUM(t.amountMinor), 0) FROM transactions t JOIN merchant_aliases a " +
        "ON a.aliasKey = t.merchantKey WHERE a.merchantId = m.id AND t.direction = 'DEBIT')"
