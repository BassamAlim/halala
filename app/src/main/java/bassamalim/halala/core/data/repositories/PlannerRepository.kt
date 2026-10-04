package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.AccountsDao
import bassamalim.halala.core.data.dataSources.room.daos.AssetsDao
import bassamalim.halala.core.data.dataSources.room.daos.ScenariosDao
import bassamalim.halala.core.data.dataSources.room.daos.TransactionsDao
import bassamalim.halala.core.data.dataSources.room.entities.RetirementScenario
import bassamalim.halala.core.domain.DigestKind
import bassamalim.halala.core.domain.Digests
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.NetWorth
import bassamalim.halala.core.domain.WealthClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** What the planner starts from (what is invested, what you save a month) and the scenarios you keep. */
@Singleton
class PlannerRepository @Inject constructor(
    private val scenariosDao: ScenariosDao,
    private val accountsDao: AccountsDao,
    private val assetsDao: AssetsDao,
    private val transactionsDao: TransactionsDao,
    private val loansRepository: LoansRepository,
    private val clock: Clock
) {

    fun observeScenarios(): Flow<List<RetirementScenario>> = scenariosDao.observeAll()

    suspend fun getScenarios(): List<RetirementScenario> = scenariosDao.getAll()

    suspend fun save(scenario: RetirementScenario): Long =
        scenariosDao.insert(scenario.copy(uid = scenario.uid.ifEmpty { UUID.randomUUID().toString() }, createdAt = clock.instant()))

    suspend fun delete(id: Long) = scenariosDao.delete(id)

    /** The pot to start from: savings, funds and gold, as net worth has them now. */
    suspend fun investedNow(currency: String): Long {
        val worth = NetWorth.now(
            accountsDao.observeAllWithBalance().first(), assetsDao.getAll(), loansRepository.observeStates().first(),
            currency, LocalDate.now(clock)
        )
        return listOf(WealthClass.SAVINGS, WealthClass.FUNDS, WealthClass.GOLD).sumOf { maxOf(0, worth.parts[it] ?: 0) }
    }

    /** What you saved a month (income less spending) over the last three months, on average; never below zero. */
    suspend fun averageSaving(currency: String): Long {
        val details = transactionsDao.getAllDetails()
        val months = Digests.finished(DigestKind.MONTH, LocalDate.now(clock), 3)
        val saved = months.sumOf {
            Digests.income(details, it, currency, clock.zone) - Money.sum(Digests.spendingByCategory(details, it, currency, clock.zone).values)
        }
        return maxOf(0, saved / 3)
    }
}
