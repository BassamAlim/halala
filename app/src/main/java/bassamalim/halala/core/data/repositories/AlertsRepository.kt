package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.AccountsDao
import bassamalim.halala.core.data.dataSources.room.daos.AlertsDao
import bassamalim.halala.core.data.dataSources.room.daos.TransactionsDao
import bassamalim.halala.core.data.dataSources.room.entities.DismissedAlert
import bassamalim.halala.core.domain.Anomalies
import bassamalim.halala.core.domain.Anomaly
import bassamalim.halala.core.enums.RawStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
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
        alertsDao.observeByStatus(RawStatus.DECLINED),
        accountsDao.observeAllWithBalance(),
        alertsDao.observeDismissed()
    ) { details, checkpoints, declined, accounts, dismissed ->
        Anomalies.find(
            details = details,
            checkpoints = checkpoints,
            messages = declined,
            currencies = accounts.associate { it.account.id to it.account.currency },
            dismissed = dismissed.toSet(),
            now = clock.instant()
        )
    }

    suspend fun dismiss(key: String) = alertsDao.dismiss(DismissedAlert(key, clock.instant()))
}
