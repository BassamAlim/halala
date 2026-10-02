package bassamalim.halala.features.rules

import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.domain.DescribedRule
import bassamalim.halala.core.domain.Rules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class RulesDomain @Inject constructor(
    private val classificationRepository: ClassificationRepository,
    private val accountsRepository: AccountsRepository,
    private val clock: Clock
) {

    fun observeRules(): Flow<List<DescribedRule>> = combine(
        classificationRepository.observeRules(),
        accountsRepository.observeAll(),
        Rules::describe
    )

    suspend fun setEnabled(ruleId: Long, enabled: Boolean) = classificationRepository.setEnabled(ruleId, enabled)

    suspend fun delete(ruleId: Long) = classificationRepository.delete(ruleId)

    fun zone(): ZoneId = clock.zone

    fun today(): LocalDate = LocalDate.now(clock)
}
