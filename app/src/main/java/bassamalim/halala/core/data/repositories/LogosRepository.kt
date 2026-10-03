package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.MerchantsDao
import bassamalim.halala.core.data.dataSources.room.entities.MerchantLogo
import bassamalim.halala.core.data.dataSources.room.relations.MerchantSite
import bassamalim.halala.core.data.dataSources.room.relations.ToIdentify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Merchants' websites and the logos fetched from them. */
@Singleton
class LogosRepository @Inject constructor(private val merchantsDao: MerchantsDao) {

    suspend fun websitesToAsk(): List<ToIdentify> = merchantsDao.getWebsitesToAsk()

    /** What was said each merchant's website is (null: none known); each is asked about once. */
    suspend fun recordWebsites(websites: Map<Long, String?>) {
        for ((id, website) in websites) merchantsDao.setWebsite(id, website)
    }

    suspend fun toFetch(): List<MerchantSite> = merchantsDao.getLogosToFetch()

    /** A logo, or null for one tried that gave nothing worth showing: never tried again. */
    suspend fun putLogo(merchantId: Long, image: ByteArray?) = merchantsDao.putLogo(MerchantLogo(merchantId, image))

    /** Each merchant's logo, as the image file it came as. */
    fun observeLogos(): Flow<Map<Long, ByteArray>> =
        merchantsDao.observeLogos().map { logos -> logos.mapNotNull { logo -> logo.image?.let { logo.merchantId to it } }.toMap() }
}
