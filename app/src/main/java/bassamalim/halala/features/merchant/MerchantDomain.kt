package bassamalim.halala.features.merchant

import bassamalim.halala.core.ai.ApiKeys
import bassamalim.halala.core.ai.GroqProtocol
import bassamalim.halala.core.ai.LookupFound
import bassamalim.halala.core.ai.LookupResult
import bassamalim.halala.core.ai.WebLookup
import bassamalim.halala.core.data.dataSources.room.entities.Category
import bassamalim.halala.core.data.dataSources.room.entities.Merchant
import bassamalim.halala.core.data.dataSources.room.relations.AliasWithCount
import bassamalim.halala.core.data.dataSources.room.relations.MerchantWithStats
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.LogosRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.toneOf
import bassamalim.halala.core.enums.AmountTone
import bassamalim.halala.core.enums.BusinessType
import bassamalim.halala.core.logos.LogoLookup
import bassamalim.halala.core.logos.LogoOutcome
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class NameProblem { Missing }

enum class LogoProblem { NotAWebsite, NoneFound, Offline }

class MerchantDomain @Inject constructor(
    private val classificationRepository: ClassificationRepository,
    private val transactionsRepository: TransactionsRepository,
    private val webLookup: WebLookup,
    private val logoLookup: LogoLookup,
    private val logosRepository: LogosRepository,
    private val keys: ApiKeys,
    private val clock: Clock
) {

    /** Whether this build can ask the AI at all. */
    fun canAsk(): Boolean = keys.hasGroq()

    /** Looks it up now: online when a search can be had, else the AI from its name alone. */
    /** Looks it up and offers what it found; nothing changes until you [acceptLookup]. */
    suspend fun lookUp(id: Long): LookupResult = webLookup.lookUpNow(id)

    /** "Use this": one change you can undo. */
    suspend fun acceptLookup(id: Long, found: LookupFound) = webLookup.accept(id, found)

    fun observeMerchant(id: Long): Flow<Merchant?> = classificationRepository.observeMerchant(id)

    fun observeAliases(id: Long): Flow<List<AliasWithCount>> = classificationRepository.observeAliases(id)

    fun observeMerchants(): Flow<List<MerchantWithStats>> = classificationRepository.observeMerchants()

    fun observeTransactions(): Flow<List<TransactionDetail>> = transactionsRepository.observeAll()

    fun observeCategories(): Flow<List<Category>> = classificationRepository.observeCategories()

    /** What the business is, as you say: one change you can undo. */
    suspend fun setBusinessType(id: Long, type: BusinessType) = classificationRepository.setBusinessType(id, type)

    /** Checks and writes; the problem when there is one. */
    suspend fun rename(id: Long, name: String): NameProblem? {
        validateName(name)?.let { return it }
        classificationRepository.renameMerchant(id, name)
        return null
    }

    /** Its logo is [text]'s icon, fetched now; the problem when there is one. */
    suspend fun setWebsite(id: Long, text: String): LogoProblem? {
        val website = GroqProtocol.domainOf(text) ?: return LogoProblem.NotAWebsite
        return when (logoLookup.correct(id, website)) {
            LogoOutcome.SHOWN -> null
            LogoOutcome.NONE -> LogoProblem.NoneFound
            LogoOutcome.OFFLINE -> LogoProblem.Offline
        }
    }

    /** It has no logo: the initial shows, and none is fetched again. */
    suspend fun removeLogo(id: Long) = logosRepository.correctWebsite(id, null)

    /** [fromId] becomes part of [intoId], as one change you can undo. */
    suspend fun merge(fromId: Long, intoId: Long) = classificationRepository.mergeMerchants(fromId, intoId)

    /** One spelling becomes a merchant of its own, as one change you can undo. */
    suspend fun split(aliasId: Long) = classificationRepository.splitAlias(aliasId)

    fun zone(): ZoneId = clock.zone

    fun today(): LocalDate = LocalDate.now(clock)

    companion object {

        fun validateName(name: String): NameProblem? = if (name.isBlank()) NameProblem.Missing else null

        /** What was spent at it in [currency]: its spending, refunds and moves left out. */
        // ponytail: one currency (SAR); a merchant abroad shows its spending in the feed below.
        fun spent(details: List<TransactionDetail>, currency: String): Long = Money.sum(
            details
                .filter {
                    it.transaction.currency == currency && it.transaction.kind.countsInTotals &&
                            toneOf(it) == AmountTone.Spending
                }
                .map { it.yourMinor }
        )

        /**
         * The merchants it could be merged into: every other one whose name holds [query], the
         * busiest first, the first [limit] of them (typing narrows the rest).
         */
        fun mergeOptions(
            merchants: List<MerchantWithStats>,
            exceptId: Long,
            query: String,
            limit: Int = 8
        ): List<MerchantWithStats> {
            val needle = query.trim()
            return merchants
                .filter { it.merchant.id != exceptId && it.merchant.name.contains(needle, ignoreCase = true) }
                .take(limit)
        }
    }
}
