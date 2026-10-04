package bassamalim.halala.features.merchants

import bassamalim.halala.core.data.dataSources.room.relations.MerchantWithStats
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.PreferencesRepository
import bassamalim.halala.core.data.dataSources.room.entities.MerchantAlias
import bassamalim.halala.core.di.DefaultDispatcher
import bassamalim.halala.core.domain.Merchants
import bassamalim.halala.core.domain.People
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class MerchantsDomain @Inject constructor(
    private val classificationRepository: ClassificationRepository,
    private val preferencesRepository: PreferencesRepository,
    @param:DefaultDispatcher private val default: CoroutineDispatcher
) {

    /** The busiest first. */
    fun observeMerchants(): Flow<List<MerchantWithStats>> = classificationRepository.observeMerchants()

    /** Pairs that may be one merchant, the one that would stay first. */
    fun observeSuggestions(): Flow<List<MergeOffer>> = combine(
        observeMerchants(),
        classificationRepository.observeAllAliases(),
        preferencesRepository.observeMerchantsDismissed(),
        ::suggest
    ).flowOn(default)

    suspend fun merge(fromId: Long, intoId: Long) = classificationRepository.mergeMerchants(fromId, intoId)

    suspend fun dismiss(pairKey: String) = preferencesRepository.dismissMerchantPair(pairKey)

    /**
     * [goes] would become [keep]: both have [website], or (null) one's spelling is the other's
     * cut short. [key] is `People.pairKey` of their uids.
     */
    data class MergeOffer(val key: String, val keep: MerchantWithStats, val goes: MerchantWithStats, val website: String?)

    companion object {

        /** Who stays when two are merged: the one you named, then the busier, then the older. */
        private val STAYS: Comparator<MerchantWithStats> =
            compareByDescending<MerchantWithStats> { it.merchant.namedByYou }
                .thenByDescending { it.transactions }
                .thenBy { it.merchant.id }

        /**
         * Pairs that may be one merchant: a spelling of one cut short from the other's (from
         * before such spellings joined on their own, or that came first), then a shared website.
         * One offer a pair, never one you said isn't ([dismissed]).
         */
        fun suggest(merchants: List<MerchantWithStats>, aliases: List<MerchantAlias>, dismissed: Set<String>): List<MergeOffer> {
            val byId = merchants.associateBy { it.merchant.id }
            val cut = Merchants.cutShortPairs(aliases.associate { it.aliasKey to it.merchantId }).mapNotNull { (a, b) ->
                val (keep, goes) = listOf(byId[a] ?: return@mapNotNull null, byId[b] ?: return@mapNotNull null).sortedWith(STAYS)
                MergeOffer(People.pairKey(keep.merchant.uid, goes.merchant.uid), keep, goes, website = null)
            }
            return (cut.sortedByDescending { it.goes.transactions } + sameWebsite(merchants, dismissed))
                .distinctBy { it.key }
                .filter { it.key !in dismissed }
        }

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
