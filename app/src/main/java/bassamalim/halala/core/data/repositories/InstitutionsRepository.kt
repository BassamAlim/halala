package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.InstitutionsDao
import bassamalim.halala.core.data.dataSources.room.entities.Institution
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** The banks and brokers. Seeded with the database; read-only for now. */
@Singleton
class InstitutionsRepository @Inject constructor(
    private val institutionsDao: InstitutionsDao
) {

    fun observeAll(): Flow<List<Institution>> = institutionsDao.observeAll()

    suspend fun getAll(): List<Institution> = institutionsDao.getAll()
}
