package bassamalim.halala.features.transaction

import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.data.repositories.TransactionsRepository
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class TransactionDomain @Inject constructor(
    private val transactionsRepository: TransactionsRepository,
    private val clock: Clock
) {

    fun observe(id: Long): Flow<TransactionDetail?> = transactionsRepository.observe(id)

    /** A move goes as a whole: both legs. */
    suspend fun delete(id: Long) = transactionsRepository.delete(id)

    fun zone(): ZoneId = clock.zone

    fun today(): LocalDate = LocalDate.now(clock)
}
