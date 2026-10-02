package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.PlacesDao
import bassamalim.halala.core.data.dataSources.room.entities.TransactionPlace
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlacesRepository @Inject constructor(private val placesDao: PlacesDao) {

    fun observeAll(): Flow<List<TransactionPlace>> = placesDao.observeAll()

    fun observe(transactionId: Long): Flow<TransactionPlace?> = placesDao.observe(transactionId)

    suspend fun getAll(): List<TransactionPlace> = placesDao.getAll()

    suspend fun add(rows: List<TransactionPlace>) = placesDao.add(rows)

    suspend fun forget(transactionId: Long) = placesDao.delete(transactionId)

    /** Every place remembered, when you turn it off and choose to forget them. */
    suspend fun forgetAll() = placesDao.deleteAll()
}
