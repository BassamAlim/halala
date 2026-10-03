package bassamalim.halala.core.data.dataSources.room.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import bassamalim.halala.core.data.dataSources.room.entities.GoalContribution
import bassamalim.halala.core.data.dataSources.room.entities.SavingsGoal
import bassamalim.halala.core.enums.TransactionKind
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalsDao {

    @Query("SELECT * FROM savings_goals ORDER BY id")
    fun observeAll(): Flow<List<SavingsGoal>>

    @Query("SELECT * FROM savings_goals ORDER BY id")
    suspend fun getAll(): List<SavingsGoal>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun get(id: Long): SavingsGoal?

    @Insert
    suspend fun insert(goal: SavingsGoal): Long

    @Update
    suspend fun update(goal: SavingsGoal)

    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun deleteGoal(id: Long)

    /** A goal goes, and what was put toward it counts as it did before it was marked. */
    @Transaction
    suspend fun delete(id: Long) {
        for (contribution in getContributionsOf(id)) contribution.kindBefore?.let { setKind(contribution.transactionId, it) }
        deleteGoal(id)
    }

    @Query("SELECT * FROM goal_contributions ORDER BY id")
    fun observeContributions(): Flow<List<GoalContribution>>

    @Query("SELECT * FROM goal_contributions ORDER BY id")
    suspend fun getContributions(): List<GoalContribution>

    @Query("SELECT * FROM goal_contributions WHERE goalId = :goalId")
    suspend fun getContributionsOf(goalId: Long): List<GoalContribution>

    @Query("SELECT * FROM goal_contributions WHERE transactionId = :transactionId")
    suspend fun getContributionFor(transactionId: Long): GoalContribution?

    @Insert
    suspend fun insertContribution(contribution: GoalContribution): Long

    @Query("DELETE FROM goal_contributions WHERE id = :id")
    suspend fun deleteContribution(id: Long)

    /** Savings take no category, so whatever filed it lets go. */
    @Query("UPDATE transactions SET kind = :kind, categoryId = NULL, expenseType = NULL, ruleId = NULL WHERE id = :id")
    suspend fun setKind(id: Long, kind: TransactionKind)

    /** Toward a goal, and its kind with it, as one write. */
    @Transaction
    suspend fun contribute(contribution: GoalContribution, kind: TransactionKind?) {
        insertContribution(contribution)
        kind?.let { setKind(contribution.transactionId, it) }
    }

    /** No longer toward a goal: it counts as it did before. */
    @Transaction
    suspend fun withdrawContribution(contribution: GoalContribution) {
        deleteContribution(contribution.id)
        contribution.kindBefore?.let { setKind(contribution.transactionId, it) }
    }
}
