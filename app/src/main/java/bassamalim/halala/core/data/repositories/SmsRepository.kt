package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.SmsDao
import bassamalim.halala.core.data.dataSources.room.entities.AccountRef
import bassamalim.halala.core.data.dataSources.room.entities.BalanceCheckpoint
import bassamalim.halala.core.data.dataSources.room.entities.RawMessage
import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.data.dataSources.room.relations.SmsStats
import bassamalim.halala.core.data.dataSources.room.relations.UnroutedGroup
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.RawStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Raw bank SMS, the digits learned for each account, and the balances banks report. */
@Singleton
class SmsRepository @Inject constructor(
    private val smsDao: SmsDao
) {

    /** The new message's id, or null when it was already stored. */
    suspend fun insertRaw(message: RawMessage): Long? = smsDao.insertRaw(message).takeIf { it > 0 }

    suspend fun getRaw(id: Long): RawMessage? = smsDao.getRaw(id)

    suspend fun getRawIds(statuses: List<RawStatus>): List<Long> = smsDao.getRawIds(statuses)

    suspend fun setStatus(id: Long, status: RawStatus, parserVersion: Int, unroutedRefs: String? = null) =
        smsDao.setStatus(id, status, parserVersion, unroutedRefs)

    fun observeUnrouted(): Flow<List<UnroutedGroup>> = smsDao.observeUnrouted()

    fun observeStats(): Flow<SmsStats> = smsDao.observeStats()

    suspend fun getRefs(): List<AccountRef> = smsDao.getRefs()

    suspend fun addRef(institutionId: Long, ref: String, accountId: Long) =
        smsDao.insertRef(AccountRef(institutionId = institutionId, ref = ref, accountId = accountId))

    suspend fun addCheckpoint(checkpoint: BalanceCheckpoint) = smsDao.insertCheckpoint(checkpoint)

    suspend fun findSimilar(
        accountId: Long,
        direction: Direction,
        amountMinor: Long,
        from: Instant,
        to: Instant
    ): List<Transaction> = smsDao.findSimilar(accountId, direction, amountMinor, from, to)

    suspend fun lowestDailyBalance(accountId: Long, until: Instant): Long? =
        smsDao.lowestDailyBalance(accountId, until)

    suspend fun inUseBy(accountIds: List<Long>, at: Instant): List<Long> = smsDao.inUseBy(accountIds, at)

    suspend fun findUnpaired(
        accountId: Long,
        direction: Direction,
        amounts: LongRange,
        currency: String,
        from: Instant,
        to: Instant
    ): List<Transaction> = smsDao.findUnpaired(accountId, direction, amounts.first, amounts.last, currency, from, to)
}
