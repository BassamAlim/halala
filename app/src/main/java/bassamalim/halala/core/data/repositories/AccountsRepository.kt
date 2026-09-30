package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.AccountsDao
import bassamalim.halala.core.data.dataSources.room.entities.Account
import bassamalim.halala.core.data.dataSources.room.relations.AccountWithBalance
import bassamalim.halala.core.models.AccountDraft
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** The only way into the accounts table. Storage details stop here. */
@Singleton
class AccountsRepository @Inject constructor(
    private val accountsDao: AccountsDao,
    private val clock: Clock
) {

    fun observeAll(): Flow<List<AccountWithBalance>> = accountsDao.observeAllWithBalance()

    fun observe(id: Long): Flow<AccountWithBalance?> = accountsDao.observeWithBalance(id)

    suspend fun get(id: Long): Account? = accountsDao.get(id)

    suspend fun getAllWithBalance(): List<AccountWithBalance> = accountsDao.getAllWithBalance()

    suspend fun getAll(): List<Account> = accountsDao.getAll()

    suspend fun getCashWallet(): Account? = accountsDao.getCashWallet()

    /** The account at this bank that already answers to these four digits, if any. */
    suspend fun findByLast4(institutionId: Long, last4: String): Account? =
        accountsDao.findByLast4(institutionId, last4)

    suspend fun create(draft: AccountDraft): Long = accountsDao.insert(
        Account(
            uid = UUID.randomUUID().toString(),
            institutionId = draft.institutionId,
            nickname = draft.nickname.trim(),
            type = draft.type,
            last4 = draft.last4.normalized(),
            currency = draft.currency.uppercase(Locale.ROOT),
            openingBalanceMinor = draft.openingBalanceMinor,
            createdAt = clock.instant()
        )
    )

    /** Rewrites the row in place, id, uid and archive state kept. */
    suspend fun update(id: Long, draft: AccountDraft) {
        val existing = checkNotNull(accountsDao.get(id)) { "No account $id" }

        accountsDao.update(
            existing.copy(
                institutionId = draft.institutionId,
                nickname = draft.nickname.trim(),
                type = draft.type,
                last4 = draft.last4.normalized(),
                currency = draft.currency.uppercase(Locale.ROOT),
                openingBalanceMinor = draft.openingBalanceMinor
            )
        )
    }

    /** Accounts are archived, never deleted: their history still points at them. */
    suspend fun setArchived(id: Long, archived: Boolean) = accountsDao.setArchived(id, archived)

    private fun String?.normalized() = this?.trim()?.ifEmpty { null }
}
