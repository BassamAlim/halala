package bassamalim.halala.features.editGoal

import bassamalim.halala.core.Globals
import bassamalim.halala.core.data.dataSources.room.entities.SavingsGoal
import bassamalim.halala.core.data.dataSources.room.relations.AccountWithBalance
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.GoalsRepository
import bassamalim.halala.core.domain.Money
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class GoalForm(
    val name: String = "",
    val target: String = "",
    val targetDate: LocalDate? = null,
    val accountIds: List<Long> = emptyList(),
    val currency: String = Globals.PRIMARY_CURRENCY,
    val uid: String = "",
    val createdAt: Instant = Instant.EPOCH
)

enum class GoalProblem { NameMissing, TargetInvalid, AccountsMissing }

class EditGoalDomain @Inject constructor(
    private val goalsRepository: GoalsRepository,
    private val accountsRepository: AccountsRepository,
    private val clock: Clock
) {

    fun observeAccounts(): Flow<List<AccountWithBalance>> = accountsRepository.observeAll()

    fun today(): LocalDate = LocalDate.now(clock)

    suspend fun load(id: Long): GoalForm? = goalsRepository.get(id)?.let {
        GoalForm(it.name, Money.plain(it.targetMinor, it.currency), it.targetDate, it.accountIds, it.currency, it.uid, it.createdAt)
    }

    suspend fun save(id: Long, form: GoalForm): Set<GoalProblem> {
        val (goal, problems) = validate(id, form)
        if (goal != null) goalsRepository.save(goal)
        return problems
    }

    suspend fun delete(id: Long) = goalsRepository.delete(id)

    companion object {
        fun validate(id: Long, form: GoalForm): Pair<SavingsGoal?, Set<GoalProblem>> {
            val problems = mutableSetOf<GoalProblem>()
            if (form.name.isBlank()) problems += GoalProblem.NameMissing
            val target = Money.parse(form.target, form.currency)?.takeIf { it > 0 }
            if (target == null) problems += GoalProblem.TargetInvalid
            if (form.accountIds.isEmpty()) problems += GoalProblem.AccountsMissing
            if (problems.isNotEmpty()) return null to problems
            return SavingsGoal(id, form.uid, form.name.trim(), target!!, form.currency, form.targetDate, form.accountIds, form.createdAt) to emptySet()
        }
    }
}
