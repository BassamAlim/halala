package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.AccountsDao
import bassamalim.halala.core.data.dataSources.room.daos.AssetsDao
import bassamalim.halala.core.data.dataSources.room.daos.ZakatDao
import bassamalim.halala.core.data.dataSources.room.entities.ZakatProfile
import bassamalim.halala.core.domain.Assets
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.NetWorth
import bassamalim.halala.core.domain.Zakat
import bassamalim.halala.core.domain.ZakatState
import bassamalim.halala.core.enums.AssetType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Your zakat method and where zakat stands, from net worth as it is today. */
@Singleton
class ZakatRepository @Inject constructor(
    private val zakatDao: ZakatDao,
    private val accountsDao: AccountsDao,
    private val assetsDao: AssetsDao,
    private val loansRepository: LoansRepository,
    private val clock: Clock
) {

    fun observeProfile(): Flow<ZakatProfile> = zakatDao.observe().map { it ?: ZakatProfile() }

    fun observeState(currency: String): Flow<Pair<ZakatProfile, ZakatState>> = combine(
        observeProfile(),
        accountsDao.observeAllWithBalance(),
        assetsDao.observeAll(),
        loansRepository.observeStates()
    ) { profile, accounts, assets, loans ->
        val today = LocalDate.now(clock)
        val worth = NetWorth.now(accounts, assets, loans, currency, today)
        // Your price for the day, else the one your gold was last priced at.
        val price = Assets.decimal(profile.goldPricePerGram)
            ?: assets.firstOrNull { it.type == AssetType.GOLD && it.currency == currency }?.let { Assets.decimal(it.unitPrice) }
        profile to Zakat.stateOf(profile, worth, price, Money.fractionDigits(currency), today)
    }

    suspend fun get(): ZakatProfile = zakatDao.get() ?: ZakatProfile()

    suspend fun update(change: (ZakatProfile) -> ZakatProfile) = zakatDao.put(change(get()))
}
