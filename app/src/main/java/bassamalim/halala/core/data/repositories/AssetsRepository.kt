package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.AssetsDao
import bassamalim.halala.core.data.dataSources.room.entities.Asset
import bassamalim.halala.core.data.dataSources.room.entities.NetWorthSnapshot
import bassamalim.halala.core.domain.Assets
import bassamalim.halala.core.domain.Money
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Assets outside your accounts (funds, gold, a car, property) and the daily snapshot of their worth. */
@Singleton
class AssetsRepository @Inject constructor(
    private val assetsDao: AssetsDao,
    private val clock: Clock
) {

    fun observeAll(): Flow<List<Asset>> = assetsDao.observeAll()

    fun observeSnapshots(): Flow<List<NetWorthSnapshot>> = assetsDao.observeSnapshots()

    suspend fun getAll(): List<Asset> = assetsDao.getAll()

    suspend fun get(id: Long): Asset? = assetsDao.get(id)

    suspend fun getSnapshots(): List<NetWorthSnapshot> = assetsDao.getSnapshots()

    suspend fun save(asset: Asset): Long =
        if (asset.id == 0L) assetsDao.insert(asset.copy(uid = asset.uid.ifEmpty { UUID.randomUUID().toString() }, createdAt = clock.instant()))
        else {
            assetsDao.update(asset)
            asset.id
        }

    suspend fun delete(id: Long) = assetsDao.delete(id)

    /** Today's worth of everything in [currency]: kept (and replaced through the day) for the timeline. */
    suspend fun snapshot(currency: String) {
        val today = LocalDate.now(clock)
        val total = Money.sum(assetsDao.getAll().filter { it.currency == currency }.map { Assets.valueOf(it, today) })
        assetsDao.putSnapshot(NetWorthSnapshot(today, total, currency))
    }
}
