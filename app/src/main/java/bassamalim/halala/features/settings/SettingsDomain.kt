package bassamalim.halala.features.settings

import bassamalim.halala.core.data.dataSources.room.relations.AccountWithBalance
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SettingsDomain @Inject constructor(
    private val accountsRepository: AccountsRepository,
    private val preferencesRepository: PreferencesRepository
) {

    fun observeAccounts(): Flow<List<AccountWithBalance>> = accountsRepository.observeAll()

    fun observeLockTimeoutSeconds(): Flow<Int> = preferencesRepository.observeLockTimeoutSeconds()
}
