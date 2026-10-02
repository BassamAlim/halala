package bassamalim.halala.features.merchants

import bassamalim.halala.core.data.dataSources.room.relations.MerchantWithStats
import bassamalim.halala.core.data.repositories.ClassificationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MerchantsDomain @Inject constructor(
    private val classificationRepository: ClassificationRepository
) {

    /** The busiest first. */
    fun observeMerchants(): Flow<List<MerchantWithStats>> = classificationRepository.observeMerchants()

    companion object {

        /** Merchants whose name holds [query], whatever its capitals; blank keeps them all. */
        fun filter(merchants: List<MerchantWithStats>, query: String): List<MerchantWithStats> {
            val needle = query.trim()
            return if (needle.isEmpty()) merchants
            else merchants.filter { it.merchant.name.contains(needle, ignoreCase = true) }
        }
    }
}
