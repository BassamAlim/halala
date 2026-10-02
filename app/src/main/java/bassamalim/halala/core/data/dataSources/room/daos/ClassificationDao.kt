package bassamalim.halala.core.data.dataSources.room.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import bassamalim.halala.core.data.dataSources.room.entities.Category
import bassamalim.halala.core.data.dataSources.room.entities.Rule
import bassamalim.halala.core.data.dataSources.room.relations.CategoryWithUse
import bassamalim.halala.core.data.dataSources.room.relations.RuleWithStats
import kotlinx.coroutines.flow.Flow

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
}
