package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.AccountsDao
import bassamalim.halala.core.data.dataSources.room.daos.AlertsDao
import bassamalim.halala.core.data.dataSources.room.daos.TransactionsDao
import bassamalim.halala.core.data.dataSources.room.entities.DismissedAlert
import bassamalim.halala.core.domain.Anomalies
import bassamalim.halala.core.domain.Anomaly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/** Anomaly alerts: found in the ledger as it is; only what you dismissed is stored. */
@Singleton
class AlertsRepository @Inject constructor(
    private val alertsDao: AlertsDao,
    private val transactionsDao: TransactionsDao,
    private val accountsDao: AccountsDao,
    private val clock: Clock
) {

    fun observeAlerts(): Flow<List<Anomaly>> = combine(
        transactionsDao.observeAllDetails(),
        alertsDao.observeCheckpoints(),
        alertsDao.observeForAlerts(clock.instant().minus(Duration.ofDays(Anomalies.PARSER_WINDOW_DAYS))),
        accountsDao.observeAllWithBalance(),
        alertsDao.observeDismissed()
    ) { details, checkpoints, messages, accounts, dismissed ->
        Anomalies.find(
            details = details,
            checkpoints = checkpoints,
            messages = messages,
            currencies = accounts.associate { it.account.id to it.account.currency },
            dismissed = dismissed.toSet(),
            now = clock.instant()
        )
    }.flowOn(Dispatchers.Default)

    suspend fun dismiss(key: String) = alertsDao.dismiss(DismissedAlert(key, clock.instant()))

    /** Every key dismissed: anomalies, and the heads-ups of subscriptions and bills. */
    fun observeDismissed(): Flow<Set<String>> = alertsDao.observeDismissed().map { it.toSet() }
}
