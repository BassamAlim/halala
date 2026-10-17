package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.RetirementDao
import bassamalim.halala.core.data.dataSources.room.entities.RetirementPlan
import bassamalim.halala.core.data.dataSources.room.entities.SavingsGoal
import bassamalim.halala.core.domain.Planner
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Your retirement plan, and the savings goal it keeps in step. */
@Singleton
class PlannerRepository @Inject constructor(
    private val retirementDao: RetirementDao,
    private val goalsRepository: GoalsRepository,
    private val clock: Clock
) {

    fun observePlan(): Flow<RetirementPlan?> = retirementDao.observe()

    suspend fun getPlan(): RetirementPlan? = retirementDao.get()

    /**
     * Keeps [plan], and sets its goal's target to the pot the wanted income needs and its date to
     * the year you retire. Without a goal (or with one since deleted) a new one is made, named
     * [newGoalName]. False when the plan doesn't add up to a pot (an age already reached, nothing wanted).
     */
    suspend fun savePlan(plan: RetirementPlan, newGoalName: String): Boolean {
        val inputs = Planner.inputsOf(plan, 0, 0, LocalDate.now(clock)) ?: return false
        val needed = Planner.retirement(inputs)?.neededMinor?.takeIf { it > 0 } ?: return false
        val goal = plan.goalId?.let { goalsRepository.get(it) }
            ?: SavingsGoal(uid = "", name = newGoalName, targetMinor = needed, currency = plan.currency, accountIds = emptyList(), createdAt = clock.instant())
        val goalId = goalsRepository.save(goal.copy(targetMinor = needed, targetDate = Planner.retireOn(plan)))
        retirementDao.put(plan.copy(goalId = goalId))
        return true
    }
}
