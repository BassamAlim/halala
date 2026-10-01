package bassamalim.halala.features.onboarding

import bassamalim.halala.core.data.dataSources.room.relations.SmsStats
import bassamalim.halala.core.data.dataSources.room.relations.UnroutedGroup
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.InstitutionsRepository
import bassamalim.halala.core.data.repositories.PreferencesRepository
import bassamalim.halala.core.data.repositories.SmsRepository
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.models.AccountDraft
import bassamalim.halala.core.sms.BankFormats
import bassamalim.halala.core.sms.SmsImport
import bassamalim.halala.core.sms.SmsIngest
import bassamalim.halala.core.sms.SmsParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** An account the SMS point at that the ledger doesn't have yet. */
data class FoundAccount(
    /** The institution's name, as seeded. */
    val bank: String,
    /** Every digit group its SMS quote, the account's own first. Empty: its SMS quote none. */
    val refs: List<String>,
    val messages: Int
) {
    val key get() = "$bank|${refs.firstOrNull().orEmpty()}"
}

class OnboardingDomain @Inject constructor(
    private val smsRepository: SmsRepository,
    private val accountsRepository: AccountsRepository,
    private val institutionsRepository: InstitutionsRepository,
    private val preferencesRepository: PreferencesRepository,
    private val smsImport: SmsImport
) {

    val importing: Flow<Boolean> get() = smsImport.running

    fun observeFound(): Flow<List<FoundAccount>> = smsRepository.observeUnrouted().map(::found)

    fun observeStats(): Flow<SmsStats> = smsRepository.observeStats()

    fun startImport() = smsImport.start()

    /**
     * Creates the accounts you named and teaches each its digits, then sends the waiting SMS
     * through again. Rows at one bank given the same name are one account (a card and the
     * account it is on); a row left blank stays unasked for now.
     */
    suspend fun nameAccounts(named: List<Pair<FoundAccount, String>>) {
        val institutions = institutionsRepository.getAll().associate { it.name to it.id }

        for (account in plan(named)) {
            val institutionId = institutions[account.bank] ?: continue
            val id = accountsRepository.create(
                AccountDraft(institutionId, account.name, typeFor(account.bank), account.last4, DEFAULT_CURRENCY, 0)
            )
            for (ref in account.otherRefs) smsRepository.addRef(institutionId, ref, id)
        }
        smsImport.retry()
    }

    suspend fun finish() = preferencesRepository.setOnboarded()

    data class NewAccount(val bank: String, val name: String, val last4: String?, val otherRefs: List<String>)

    companion object {
        const val DEFAULT_CURRENCY = "SAR"

        /** A broker holds funds and the wallets are wallets; anything else starts as current. */
        fun typeFor(bank: String): AccountType = when (bank) {
            BankFormats.AL_RAJHI_CAPITAL.institution -> AccountType.INVESTMENT
            BankFormats.STC_BANK.institution, BankFormats.BARQ.institution -> AccountType.WALLET
            else -> AccountType.CURRENT
        }

        /**
         * The accounts behind the SMS no account matched. Digits quoted together are one account
         * (the account and its card); three digits are the tail of a four-digit number seen at
         * the same bank ("444*690" is ••4444). Each account leads with the digits its SMS put
         * first, which is the account's own number. A bank's SMS that quote no digits are their
         * own row only when they can't simply belong to that bank's one account.
         */
        fun found(groups: List<UnroutedGroup>): List<FoundAccount> =
            groups.groupBy { SmsParser.bankFor(it.sender)?.institution }
                .flatMap { (bank, messages) -> if (bank == null) emptyList() else foundAt(bank, messages) }
                .sortedWith(compareBy<FoundAccount> { it.bank }.thenByDescending { it.messages })

        private fun foundAt(bank: String, groups: List<UnroutedGroup>): List<FoundAccount> {
            val quoted = groups.map { it.refs.split(',').filter(String::isNotEmpty) to it.count }
            val full = quoted.flatMap { it.first }.filter { it.length >= 4 }.toSet()

            // A short ref stands for the one full number it ends; alone, it stands for itself.
            fun resolve(refs: List<String>): List<String> {
                val known = refs.mapNotNull { ref ->
                    if (ref in full) ref else full.singleOrNull { it.endsWith(ref) }
                }.distinct()
                return known.ifEmpty { refs.take(1) }
            }
            val messages = quoted.map { (refs, count) -> resolve(refs) to count }

            val cluster = mutableMapOf<String, String>()
            fun root(ref: String): String = cluster[ref]?.takeIf { it != ref }?.let(::root) ?: ref
            for ((refs, _) in messages) refs.forEach { cluster[root(it)] = root(refs.first()) }

            val accounts = messages.filter { it.first.isNotEmpty() }
                .groupBy { root(it.first.first()) }
                .map { (_, members) ->
                    val refs = members.flatMap { it.first }.distinct()
                    // The account's own number comes first in an SMS that also quotes its card.
                    val lead = members.filter { it.first.size > 1 }
                        .groupBy({ it.first.first() }, { it.second })
                        .maxByOrNull { it.value.sum() }?.key
                        ?: refs.first()
                    FoundAccount(bank, listOf(lead) + (refs - lead), members.sumOf { it.second })
                }
            val digitless = messages.filter { it.first.isEmpty() }.sumOf { it.second }

            return when {
                digitless == 0 -> accounts
                accounts.size == 1 -> accounts.map { it.copy(messages = it.messages + digitless) }
                else -> accounts + FoundAccount(bank, emptyList(), digitless)
            }
        }

        /** Which accounts naming those rows creates: one per bank and name, blank rows skipped. */
        fun plan(named: List<Pair<FoundAccount, String>>): List<NewAccount> =
            named.map { (found, name) -> found to name.trim() }
                .filter { it.second.isNotEmpty() }
                .groupBy { (found, name) -> found.bank to name.lowercase() }
                .map { (_, rows) ->
                    val refs = rows.flatMap { it.first.refs }.distinct()
                    val quotesNone = rows.any { it.first.refs.isEmpty() }
                    NewAccount(
                        bank = rows.first().first.bank,
                        name = rows.first().second,
                        last4 = refs.firstOrNull(),
                        otherRefs = refs.drop(1) + if (quotesNone && refs.isNotEmpty()) listOf(SmsIngest.NO_DIGITS) else emptyList()
                    )
                }
    }
}
