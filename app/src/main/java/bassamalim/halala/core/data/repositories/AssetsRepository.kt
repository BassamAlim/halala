package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.AssetsDao
import bassamalim.halala.core.data.dataSources.room.entities.Asset
import bassamalim.halala.core.data.dataSources.room.entities.AssetPurchase
import bassamalim.halala.core.data.dataSources.room.entities.NetWorthSnapshot
import bassamalim.halala.core.domain.Assets
import bassamalim.halala.core.domain.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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

    /** Each with what it cost: its purchases' total once any are linked, else what you typed. */
    fun observeAll(): Flow<List<Asset>> = combine(assetsDao.observeAll(), assetsDao.observePaid()) { assets, paid ->
        val byAsset = paid.associate { it.assetId to it.paidMinor }
        assets.map { asset -> byAsset[asset.id]?.let { asset.copy(costMinor = it) } ?: asset }
    }

    /** Which asset each transaction you said bought one bought: transaction id → asset id. */
    fun observePurchases(): Flow<Map<Long, Long>> =
        assetsDao.observePurchases().map { purchases -> purchases.associate { it.transactionId to it.assetId } }

    suspend fun getPurchases(): List<AssetPurchase> = assetsDao.getPurchases()

    /** Transaction [transactionId] bought [assetId] (and nothing else). */
    suspend fun link(transactionId: Long, assetId: Long) {
        if (assetsDao.get(assetId) == null) return
        assetsDao.insertPurchase(AssetPurchase(transactionId = transactionId, assetId = assetId))
    }

    suspend fun unlink(transactionId: Long) = assetsDao.deletePurchase(transactionId)

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
