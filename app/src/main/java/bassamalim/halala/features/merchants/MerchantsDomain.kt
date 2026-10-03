package bassamalim.halala.features.merchants

import bassamalim.halala.core.data.dataSources.room.relations.MerchantWithStats
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.PreferencesRepository
import bassamalim.halala.core.domain.People
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class MerchantsDomain @Inject constructor(
    private val classificationRepository: ClassificationRepository,
    private val preferencesRepository: PreferencesRepository
) {

    /** The busiest first. */
    fun observeMerchants(): Flow<List<MerchantWithStats>> = classificationRepository.observeMerchants()

    /** Pairs that may be one merchant, the one that would stay first. */
    fun observeSuggestions(): Flow<List<MergeOffer>> =
        combine(observeMerchants(), preferencesRepository.observeMerchantsDismissed(), ::sameWebsite)

    suspend fun merge(fromId: Long, intoId: Long) = classificationRepository.mergeMerchants(fromId, intoId)

    suspend fun dismiss(pairKey: String) = preferencesRepository.dismissMerchantPair(pairKey)

    /** [goes] would become [keep]: both have [website]. [key] is `People.pairKey` of their uids. */
    data class MergeOffer(val key: String, val keep: MerchantWithStats, val goes: MerchantWithStats, val website: String)

    companion object {

        /** Who stays when two are merged: the one you named, then the busier, then the older. */
        private val STAYS: Comparator<MerchantWithStats> =
            compareByDescending<MerchantWithStats> { it.merchant.namedByYou }
                .thenByDescending { it.transactions }
                .thenBy { it.merchant.id }

        /**
         * Merchants the AI gave the same website (for their logos) may be one business by two
         * names ("جرير" and "JARIR BOOKSTORE"). Only ever suggested: a marketplace and its
         * subscription share a site too. Each joins the one that stays; none you said aren't
         * the same ([dismissed]).
         */
        // ponytail: pairs only with the group's keeper; a dismissed pair hides a third look-alike's link to the second.
        fun sameWebsite(merchants: List<MerchantWithStats>, dismissed: Set<String>): List<MergeOffer> =
            merchants.filter { it.merchant.website != null }
                .groupBy { it.merchant.website!! }
                .flatMap { (website, group) ->
                    val sorted = group.sortedWith(STAYS)
                    sorted.drop(1).map { goes ->
                        MergeOffer(People.pairKey(sorted.first().merchant.uid, goes.merchant.uid), sorted.first(), goes, website)
                    }
                }
                .filter { it.key !in dismissed }

        /** Merchants whose name holds [query], whatever its capitals; blank keeps them all. */
        fun filter(merchants: List<MerchantWithStats>, query: String): List<MerchantWithStats> {
            val needle = query.trim()
            return if (needle.isEmpty()) merchants
            else merchants.filter { it.merchant.name.contains(needle, ignoreCase = true) }
        }
    }
}
