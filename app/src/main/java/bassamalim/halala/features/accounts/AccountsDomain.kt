package bassamalim.halala.features.accounts

import bassamalim.halala.core.data.dataSources.room.relations.AccountWithBalance
import bassamalim.halala.core.data.repositories.AccountsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AccountsDomain @Inject constructor(
    private val accountsRepository: AccountsRepository
) {

    fun observeAccounts(): Flow<List<AccountWithBalance>> = accountsRepository.observeAll()
}
