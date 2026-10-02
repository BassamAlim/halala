package bassamalim.halala.core.export

import bassamalim.halala.core.data.dataSources.room.Converters
import bassamalim.halala.core.data.dataSources.room.Seed
import bassamalim.halala.core.data.dataSources.room.entities.Account
import bassamalim.halala.core.data.dataSources.room.entities.AccountRef
import bassamalim.halala.core.data.dataSources.room.entities.BalanceCheckpoint
import bassamalim.halala.core.data.dataSources.room.entities.Category
import bassamalim.halala.core.data.dataSources.room.entities.Institution
import bassamalim.halala.core.data.dataSources.room.entities.InternalTransfer
import bassamalim.halala.core.data.dataSources.room.entities.Loan
import bassamalim.halala.core.data.dataSources.room.entities.LoanEvent
import bassamalim.halala.core.data.dataSources.room.entities.Merchant
import bassamalim.halala.core.data.dataSources.room.entities.MerchantAlias
import bassamalim.halala.core.data.dataSources.room.entities.Person
import bassamalim.halala.core.data.dataSources.room.entities.PersonAlias
import bassamalim.halala.core.data.dataSources.room.entities.RawMessage
import bassamalim.halala.core.data.dataSources.room.entities.Rule
import bassamalim.halala.core.data.dataSources.room.entities.RuleActions
import bassamalim.halala.core.data.dataSources.room.entities.RuleConditions
import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.data.repositories.RestoreRepository
import bassamalim.halala.core.domain.Merchants
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.LoanDirection
import bassamalim.halala.core.enums.LoanEventType
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * Restores a JSON export ([ExportFile]): the whole ledger is replaced by what the file holds,
 * for moving to a new phone or a reinstall. Reading is separate from writing, so a file is
 * checked in full, and you are asked, before anything on the phone changes.
 */
class Importer @Inject constructor(
    private val restoreRepository: RestoreRepository
) {

    /** Replaces everything in the ledger with [snapshot], or changes nothing if it doesn't fit. */
    suspend fun restore(snapshot: LedgerSnapshot) = restoreRepository.replaceAll(
        institutions = snapshot.institutions,
        accounts = snapshot.accounts,
        refs = snapshot.refs,
        rawMessages = snapshot.rawMessages,
        categories = snapshot.categories,
        rules = snapshot.rules,
        merchants = snapshot.merchants,
        aliases = snapshot.aliases,
        transactions = snapshot.transactions,
        transfers = snapshot.transfers,
        checkpoints = snapshot.checkpoints,
        people = snapshot.people,
        personAliases = snapshot.personAliases,
        loans = snapshot.loans,
        loanEvents = snapshot.loanEvents
    )

    companion object {

        private val lenientJson = Json { ignoreUnknownKeys = true }
        private val converters = Converters()

        /**
         * The ledger a JSON export describes, its rows numbered afresh and pointing at each other
         * by those numbers. Throws when the text isn't a Halala export this version can restore,
         * or when a row points at one the file doesn't hold.
         */
        // ponytail: files older than schema 5 are refused, since they lack the bank messages a
        // restore needs to not record them twice. Migrate them forward if one ever must be read.
        fun read(text: String): LedgerSnapshot {
            val file = lenientJson.decodeFromString(ExportFile.serializer(), text)
            require(file.app == "Halala") { "Not a Halala export." }
            require(file.schemaVersion in RESTORABLE_SINCE..ExportFile.SCHEMA_VERSION) {
                "Schema ${file.schemaVersion} can't be restored by this version."
            }

            // The banks this version knows are kept even when the file is from before them.
            val institutionNames = (file.institutions.map { it.name } + Seed.INSTITUTIONS).distinct()
            val senderIds = file.institutions.associate { it.name to it.senderIds.joinToString(",") }
            val institutions = institutionNames.mapIndexed { index, name ->
                Institution(id = index + 1L, name = name, senderIds = senderIds[name].orEmpty())
            }
            val institutionIds = institutions.associate { it.name to it.id }

            val accounts = file.accounts.mapIndexed { index, account ->
                Account(
                    id = index + 1L,
                    uid = account.uid,
                    institutionId = account.institution?.let { institutionIds.named(it, "institution") },
                    nickname = account.nickname,
                    type = converters.toAccountType(account.type),
                    last4 = account.last4,
                    ibanSuffix = account.ibanSuffix,
                    currency = account.currency,
                    openingBalanceMinor = account.openingBalanceMinor,
                    archived = account.archived,
                    createdAt = Instant.parse(account.createdAt)
                )
            }
            val accountIds = accounts.associate { it.uid to it.id }
            val currencies = accounts.associate { it.id to it.currency }

            val refs = file.accounts.flatMap { account ->
                account.refs.map {
                    AccountRef(
                        institutionId = institutionIds.named(it.institution, "institution"),
                        ref = it.ref,
                        accountId = accountIds.getValue(account.uid)
                    )
                }
            }

            val rawMessages = file.rawMessages.mapIndexed { index, message ->
                RawMessage(
                    id = index + 1L,
                    sender = message.sender,
                    body = message.body,
                    receivedAt = Instant.parse(message.receivedAt),
                    hash = message.hash,
                    status = converters.toRawStatus(message.status),
                    parserVersion = message.parserVersion,
                    unroutedRefs = message.unroutedRefs
                )
            }
            val rawIds = rawMessages.associate { it.hash to it.id }

            val categories = file.categories.mapIndexed { index, category ->
                Category(
                    id = index + 1L,
                    uid = category.uid,
                    name = category.name,
                    expenseType = converters.toExpenseType(category.expenseType),
                    businessTypes = converters.toBusinessTypes(category.businessTypes.joinToString(","))
                )
            }
            val categoryIds = categories.associate { it.uid to it.id }

            val merchants = file.merchants.mapIndexed { index, merchant ->
                Merchant(
                    id = index + 1L,
                    uid = merchant.uid,
                    name = merchant.name,
                    businessType = converters.toBusinessType(merchant.businessType),
                    identifiedBy = converters.toIdentifiedBy(merchant.identifiedBy),
                    confidence = merchant.confidence,
                    namedByYou = merchant.namedByYou,
                    autoRuled = merchant.autoRuled
                )
            }
            val merchantIds = merchants.associate { it.uid to it.id }
            val aliases = file.merchants
                .flatMap { merchant -> merchant.aliases.map { merchant.uid to it } }
                .mapIndexed { index, (merchantUid, alias) ->
                    MerchantAlias(
                        id = index + 1L,
                        merchantId = merchantIds.getValue(merchantUid),
                        aliasKey = alias.key,
                        descriptor = alias.descriptor,
                        matchedBy = converters.toAliasMatch(alias.matchedBy)
                    )
                }

            // A rule with no category files nothing: it is left behind.
            val rules = file.rules.filter { it.categoryUid != null }.mapIndexed { index, rule ->
                Rule(
                    id = index + 1L,
                    uid = rule.uid,
                    conditions = RuleConditions(
                        merchant = rule.merchant,
                        contains = rule.contains,
                        accountId = rule.accountUid?.let { accountIds.named(it, "account") },
                        minMinor = rule.minMinor,
                        maxMinor = rule.maxMinor,
                        merchantId = rule.merchantUid?.let { merchantIds.named(it, "merchant") }
                    ),
                    actions = RuleActions(
                        categoryId = categoryIds.named(rule.categoryUid!!, "category"),
                        expenseType = converters.toExpenseType(rule.expenseType)
                    ),
                    source = converters.toRuleSource(rule.source),
                    enabled = rule.enabled,
                    createdAt = Instant.parse(rule.createdAt)
                )
            }
            val ruleIds = rules.associate { it.uid to it.id }

            val transactions = file.transactions.mapIndexed { index, tx ->
                val accountId = accountIds.named(tx.accountUid, "account")
                require(tx.amountMinor > 0) { "Transaction ${tx.uid} has no positive amount." }
                require(tx.currency == currencies[accountId]) { "Transaction ${tx.uid} isn't in its account's currency." }
                Transaction(
                    id = index + 1L,
                    uid = tx.uid,
                    accountId = accountId,
                    direction = Direction.valueOf(tx.direction),
                    amountMinor = tx.amountMinor,
                    currency = tx.currency,
                    occurredAt = Instant.parse(tx.occurredAt),
                    kind = converters.toTransactionKind(tx.kind),
                    title = tx.title,
                    note = tx.note,
                    source = converters.toTransactionSource(tx.source),
                    createdAt = Instant.parse(tx.createdAt),
                    rawMessageId = tx.rawMessageHash?.let(rawIds::get),
                    originalAmountMinor = tx.originalAmountMinor,
                    originalCurrency = tx.originalCurrency,
                    categoryId = tx.categoryUid?.let { categoryIds.named(it, "category") },
                    expenseType = converters.toExpenseType(tx.expenseType),
                    // Filed by a rule left behind: it reads as filed by you, and stays filed.
                    ruleId = tx.ruleUid?.let(ruleIds::get),
                    merchantKey = Merchants.key(tx.title)
                )
            }
            val transactionIds = transactions.associate { it.uid to it.id }

            val transfers = file.internalTransfers.mapIndexed { index, pair ->
                InternalTransfer(
                    id = index + 1L,
                    uid = pair.uid,
                    outTransactionId = transactionIds.named(pair.outTransactionUid, "transaction"),
                    inTransactionId = transactionIds.named(pair.inTransactionUid, "transaction"),
                    matchConfidence = pair.matchConfidence
                )
            }

            val checkpoints = file.balanceCheckpoints.mapIndexed { index, checkpoint ->
                BalanceCheckpoint(
                    id = index + 1L,
                    accountId = accountIds.named(checkpoint.accountUid, "account"),
                    balanceMinor = checkpoint.balanceMinor,
                    at = Instant.parse(checkpoint.at),
                    rawMessageId = checkpoint.rawMessageHash?.let(rawIds::get)
                )
            }

            // A file from before people (schema 5) has none: the app finds them again on opening.
            val people = file.people.mapIndexed { index, person ->
                Person(id = index + 1L, uid = person.uid, name = person.name, namedByYou = person.namedByYou)
            }
            val personIds = people.associate { it.uid to it.id }
            val personAliases = file.people
                .flatMap { person -> person.aliases.map { person.uid to it } }
                .mapIndexed { index, (personUid, alias) ->
                    PersonAlias(
                        id = index + 1L,
                        personId = personIds.getValue(personUid),
                        aliasKey = alias.key,
                        descriptor = alias.descriptor
                    )
                }

            val loans = file.loans.mapIndexed { index, loan ->
                Loan(
                    id = index + 1L,
                    uid = loan.uid,
                    personId = personIds.named(loan.personUid, "person"),
                    direction = LoanDirection.valueOf(loan.direction),
                    currency = loan.currency,
                    dueOn = loan.dueOn?.let(LocalDate::parse),
                    createdAt = Instant.parse(loan.createdAt)
                )
            }
            val loanIds = loans.associate { it.uid to it.id }
            val loanEvents = file.loans
                .flatMap { loan -> loan.events.map { loan.uid to it } }
                .mapIndexed { index, (loanUid, event) ->
                    require(event.transactionUid != null || (event.amountMinor ?: 0) > 0) {
                        "Loan event ${event.uid} has neither a transaction nor an amount."
                    }
                    LoanEvent(
                        id = index + 1L,
                        uid = event.uid,
                        loanId = loanIds.getValue(loanUid),
                        type = LoanEventType.valueOf(event.type),
                        transactionId = event.transactionUid?.let { transactionIds.named(it, "transaction") },
                        amountMinor = event.amountMinor,
                        at = event.at?.let(Instant::parse)
                    )
                }

            return LedgerSnapshot(
                institutions = institutions,
                accounts = accounts,
                balances = emptyMap(),
                transactions = transactions,
                transfers = transfers,
                categories = categories,
                rules = rules,
                merchants = merchants,
                aliases = aliases,
                rawMessages = rawMessages,
                refs = refs,
                checkpoints = checkpoints,
                people = people,
                personAliases = personAliases,
                loans = loans,
                loanEvents = loanEvents
            )
        }

        /** The first schema that holds everything a restore needs. */
        private const val RESTORABLE_SINCE = 5

        private fun Map<String, Long>.named(key: String, what: String): Long =
            requireNotNull(this[key]) { "The file points at $what $key, which it doesn't hold." }
    }
}
