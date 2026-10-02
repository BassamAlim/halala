package bassamalim.halala.core.data.dataSources.room.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import bassamalim.halala.core.data.dataSources.room.entities.AuditBatch
import bassamalim.halala.core.data.dataSources.room.entities.AuditChange
import bassamalim.halala.core.data.dataSources.room.entities.Category
import bassamalim.halala.core.data.dataSources.room.entities.Rule
import bassamalim.halala.core.data.dataSources.room.relations.BatchWithCount
import bassamalim.halala.core.data.dataSources.room.relations.CategoryWithUse
import bassamalim.halala.core.data.dataSources.room.relations.FilingRow
import bassamalim.halala.core.data.dataSources.room.relations.RuleWithStats
import bassamalim.halala.core.enums.ExpenseType
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Categories and the rules that file transactions under them. */
@Dao
interface ClassificationDao {

    // By id: the seeded order, then yours as you added them.
    @Query("SELECT * FROM categories ORDER BY id")
    fun observeCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY id")
    suspend fun getCategories(): List<Category>

    @Query(
        "SELECT c.*, (SELECT COUNT(*) FROM transactions t WHERE t.categoryId = c.id) AS uses " +
                "FROM categories c ORDER BY c.id"
    )
    fun observeCategoriesWithUse(): Flow<List<CategoryWithUse>>

    @Insert
    suspend fun insertCategory(category: Category): Long

    /** Its transactions lose their category (the foreign key sets it null). */
    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategory(id: Long): Category?

    @Query(
        """
        SELECT r.*,
            (SELECT c.name FROM categories c WHERE c.id = json_extract(r.actions, '$.categoryId')) AS categoryName,
            (SELECT COUNT(*) FROM transactions t WHERE t.ruleId = r.id) AS hits,
            (SELECT MAX(t.occurredAt) FROM transactions t WHERE t.ruleId = r.id) AS lastHitAt
        FROM rules r ORDER BY hits DESC, r.id DESC
        """
    )
    fun observeRules(): Flow<List<RuleWithStats>>

    @Query("SELECT * FROM rules ORDER BY id")
    suspend fun getRules(): List<Rule>

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun getRule(id: Long): Rule?

    @Insert
    suspend fun insertRule(rule: Rule): Long

    @Update
    suspend fun updateRule(rule: Rule)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteRule(id: Long)

    @Update
    suspend fun updateCategory(category: Category)

    // The history of changes, and what undo needs.

    @Query("SELECT id, categoryId, expenseType, ruleId FROM transactions")
    suspend fun getFilings(): List<FilingRow>

    /**
     * Puts a transaction's filing back, only where it still reads as the batch left it, so undo
     * never overwrites something you did since. Returns how many rows it changed.
     */
    @Query(
        """
        UPDATE transactions SET categoryId = :categoryId, expenseType = :expenseType, ruleId = :ruleId
        WHERE id = :id AND categoryId IS :nowCategoryId AND expenseType IS :nowExpenseType AND ruleId IS :nowRuleId
        """
    )
    suspend fun restoreFiling(
        id: Long,
        categoryId: Long?,
        expenseType: ExpenseType?,
        ruleId: Long?,
        nowCategoryId: Long?,
        nowExpenseType: ExpenseType?,
        nowRuleId: Long?
    ): Int

    @Insert
    suspend fun insertBatch(batch: AuditBatch): Long

    @Insert
    suspend fun insertChanges(changes: List<AuditChange>)

    // ponytail: the log grows without bound and the screen shows the newest 200; prune by age
    // if the table ever weighs on the database.
    @Query(
        """
        SELECT b.*, (SELECT COUNT(*) FROM audit_changes c
            WHERE c.batchId = b.id AND c.entity = 'TRANSACTION') AS transactions
        FROM audit_batches b ORDER BY b.id DESC LIMIT 200
        """
    )
    fun observeBatches(): Flow<List<BatchWithCount>>

    @Query("SELECT * FROM audit_batches WHERE id = :id")
    suspend fun getBatch(id: Long): AuditBatch?

    @Query("SELECT * FROM audit_changes WHERE batchId = :batchId ORDER BY id")
    suspend fun getChanges(batchId: Long): List<AuditChange>

    @Query("UPDATE audit_batches SET undoneAt = :at WHERE id = :id")
    suspend fun markUndone(id: Long, at: Instant)
}
