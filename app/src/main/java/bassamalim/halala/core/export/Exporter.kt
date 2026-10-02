package bassamalim.halala.core.export

import bassamalim.halala.core.data.dataSources.room.entities.Account
import bassamalim.halala.core.data.dataSources.room.entities.AccountRef
import bassamalim.halala.core.data.dataSources.room.entities.Asset
import bassamalim.halala.core.data.dataSources.room.entities.NetWorthSnapshot
import bassamalim.halala.core.data.dataSources.room.entities.BalanceCheckpoint
import bassamalim.halala.core.data.dataSources.room.entities.Budget
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
import bassamalim.halala.core.data.dataSources.room.entities.RecurringSeries
import bassamalim.halala.core.data.dataSources.room.entities.RetirementScenario
import bassamalim.halala.core.data.dataSources.room.entities.Rule
import bassamalim.halala.core.data.dataSources.room.entities.SavingsGoal
import bassamalim.halala.core.data.dataSources.room.entities.SavingsTerms
import bassamalim.halala.core.data.dataSources.room.entities.Tag
import bassamalim.halala.core.data.dataSources.room.entities.TransactionTag
import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.data.dataSources.room.entities.ZakatProfile
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.AssetsRepository
import bassamalim.halala.core.data.repositories.BudgetsRepository
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.GoalsRepository
import bassamalim.halala.core.data.repositories.InstitutionsRepository
import bassamalim.halala.core.data.repositories.LoansRepository
import bassamalim.halala.core.data.repositories.PeopleRepository
import bassamalim.halala.core.data.repositories.PlannerRepository
import bassamalim.halala.core.data.repositories.RecurringRepository
import bassamalim.halala.core.data.repositories.SavingsRepository
import bassamalim.halala.core.data.repositories.SmsRepository
import bassamalim.halala.core.data.repositories.TagsRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.data.repositories.ZakatRepository
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.utils.accountLabel
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/** Everything the exports are built from, read once so the files agree with each other. */
data class LedgerSnapshot(
    val institutions: List<Institution>,
    val accounts: List<Account>,
    /** Balances by account id, in minor units. */
    val balances: Map<Long, Long>,
    val transactions: List<Transaction>,
    val transfers: List<InternalTransfer>,
    val categories: List<Category> = emptyList(),
    val rules: List<Rule> = emptyList(),
    val merchants: List<Merchant> = emptyList(),
    val aliases: List<MerchantAlias> = emptyList(),
    val rawMessages: List<RawMessage> = emptyList(),
    val refs: List<AccountRef> = emptyList(),
    val checkpoints: List<BalanceCheckpoint> = emptyList(),
    val people: List<Person> = emptyList(),
    val personAliases: List<PersonAlias> = emptyList(),
    val loans: List<Loan> = emptyList(),
    val loanEvents: List<LoanEvent> = emptyList(),
    val recurring: List<RecurringSeries> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val goals: List<SavingsGoal> = emptyList(),
    val assets: List<Asset> = emptyList(),
    val snapshots: List<NetWorthSnapshot> = emptyList(),
    val zakat: ZakatProfile? = null,
    val scenarios: List<RetirementScenario> = emptyList(),
    val savingsTerms: List<SavingsTerms> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val transactionTags: List<TransactionTag> = emptyList()
) {
    /** The merchant each transaction's title names, by transaction id. */
    fun merchantOf(): Map<Long, Merchant> {
        val byId = merchants.associateBy { it.id }
        val byKey = aliases.associate { it.aliasKey to it.merchantId }
        return transactions
            .mapNotNull { tx -> byKey[tx.merchantKey]?.let(byId::get)?.let { tx.id to it } }
            .toMap()
    }
}

/**
 * The CSV and JSON exports (the spec's `.halala` encrypted backup arrives in Phase 6). The
 * screen owns the file the user picked; this owns what goes in it.
 */
class Exporter @Inject constructor(
    private val institutionsRepository: InstitutionsRepository,
    private val accountsRepository: AccountsRepository,
    private val transactionsRepository: TransactionsRepository,
    private val classificationRepository: ClassificationRepository,
    private val smsRepository: SmsRepository,
    private val peopleRepository: PeopleRepository,
    private val loansRepository: LoansRepository,
    private val recurringRepository: RecurringRepository,
    private val budgetsRepository: BudgetsRepository,
    private val goalsRepository: GoalsRepository,
    private val assetsRepository: AssetsRepository,
    private val zakatRepository: ZakatRepository,
    private val plannerRepository: PlannerRepository,
    private val savingsRepository: SavingsRepository,
    private val tagsRepository: TagsRepository,
    private val clock: Clock
) {

    suspend fun snapshot(): LedgerSnapshot = LedgerSnapshot(
        institutions = institutionsRepository.getAll(),
        accounts = accountsRepository.getAll(),
        balances = accountsRepository.getAllWithBalance().associate { it.account.id to it.balanceMinor },
        transactions = transactionsRepository.getAll(),
        transfers = transactionsRepository.getAllTransfers(),
        categories = classificationRepository.getCategories(),
        rules = classificationRepository.getRules(),
        merchants = classificationRepository.getMerchants(),
        aliases = classificationRepository.getAliases(),
        rawMessages = smsRepository.getAllRaw(),
        refs = smsRepository.getRefs(),
        checkpoints = smsRepository.getCheckpoints(),
        people = peopleRepository.getPeople(),
        personAliases = peopleRepository.getAliases(),
        loans = loansRepository.getLoans(),
        loanEvents = loansRepository.getEvents(),
        recurring = recurringRepository.getAll(),
        budgets = budgetsRepository.getAll(),
        goals = goalsRepository.getAll(),
        assets = assetsRepository.getAll(),
        snapshots = assetsRepository.getSnapshots(),
        zakat = zakatRepository.get().takeIf { it != ZakatProfile() },
        scenarios = plannerRepository.getScenarios(),
        savingsTerms = savingsRepository.getAll(),
        tags = tagsRepository.getAll(),
        transactionTags = tagsRepository.getRows()
    )

    fun fileStem(): String = "halala-${clock.instant().atZone(clock.zone).toLocalDate()}"

    suspend fun json(appVersion: String): ByteArray =
        json(snapshot(), appVersion, clock.instant()).toByteArray(Charsets.UTF_8)

    suspend fun csvZip(): ByteArray = csvZip(snapshot(), clock.zone)

    companion object {

        // encodeDefaults, or schemaVersion (a default) would be left out of the very file it describes.
        private val prettyJson = Json { prettyPrint = true; encodeDefaults = true }

        fun json(snapshot: LedgerSnapshot, appVersion: String, now: Instant): String {
            val institutionNames = snapshot.institutions.associate { it.id to it.name }
            val accountUids = snapshot.accounts.associate { it.id to it.uid }
            val transactionUids = snapshot.transactions.associate { it.id to it.uid }
            val categoryUids = snapshot.categories.associate { it.id to it.uid }
            val ruleUids = snapshot.rules.associate { it.id to it.uid }
            val merchantUids = snapshot.merchants.associate { it.id to it.uid }
            val merchantOf = snapshot.merchantOf()
            val rawHashes = snapshot.rawMessages.associate { it.id to it.hash }
            val personUids = snapshot.people.associate { it.id to it.uid }

            val file = ExportFile(
                appVersion = appVersion,
                exportedAt = now.toString(),
                institutions = snapshot.institutions.map { institution ->
                    ExportInstitution(
                        name = institution.name,
                        senderIds = institution.senderIds.split(',').map(String::trim).filter(String::isNotEmpty)
                    )
                },
                accounts = snapshot.accounts.map { account ->
                    ExportAccount(
                        uid = account.uid,
                        institution = account.institutionId?.let(institutionNames::get),
                        nickname = account.nickname,
                        type = account.type.name,
                        last4 = account.last4,
                        ibanSuffix = account.ibanSuffix,
                        currency = account.currency,
                        openingBalanceMinor = account.openingBalanceMinor,
                        archived = account.archived,
                        createdAt = account.createdAt.toString(),
                        refs = snapshot.refs.filter { it.accountId == account.id }.map {
                            ExportAccountRef(institutionNames.getValue(it.institutionId), it.ref)
                        }
                    )
                },
                transactions = snapshot.transactions.map { tx ->
                    ExportTransaction(
                        uid = tx.uid,
                        accountUid = accountUids.getValue(tx.accountId),
                        direction = tx.direction.name,
                        amountMinor = tx.amountMinor,
                        currency = tx.currency,
                        occurredAt = tx.occurredAt.toString(),
                        kind = tx.kind.name,
                        title = tx.title,
                        note = tx.note,
                        source = tx.source.name,
                        createdAt = tx.createdAt.toString(),
                        categoryUid = tx.categoryId?.let(categoryUids::get),
                        expenseType = tx.expenseType?.name,
                        ruleUid = tx.ruleId?.let(ruleUids::get),
                        merchantUid = merchantOf[tx.id]?.uid,
                        originalAmountMinor = tx.originalAmountMinor,
                        originalCurrency = tx.originalCurrency,
                        rawMessageHash = tx.rawMessageId?.let(rawHashes::get)
                    )
                },
                internalTransfers = snapshot.transfers.map { pair ->
                    ExportInternalTransfer(
                        uid = pair.uid,
                        outTransactionUid = transactionUids.getValue(pair.outTransactionId),
                        inTransactionUid = transactionUids.getValue(pair.inTransactionId),
                        matchConfidence = pair.matchConfidence
                    )
                },
                categories = snapshot.categories.map { category ->
                    ExportCategory(
                        uid = category.uid,
                        name = category.name,
                        expenseType = category.expenseType?.name,
                        businessTypes = category.businessTypes.map { it.name }
                    )
                },
                rules = snapshot.rules.map { rule ->
                    ExportRule(
                        uid = rule.uid,
                        merchant = rule.conditions.merchant,
                        merchantUid = rule.conditions.merchantId?.let(merchantUids::get),
                        categoryUid = categoryUids[rule.actions.categoryId],
                        expenseType = rule.actions.expenseType?.name,
                        source = rule.source.name,
                        enabled = rule.enabled,
                        createdAt = rule.createdAt.toString(),
                        contains = rule.conditions.contains,
                        accountUid = rule.conditions.accountId?.let(accountUids::get),
                        minMinor = rule.conditions.minMinor,
                        maxMinor = rule.conditions.maxMinor
                    )
                },
                merchants = snapshot.merchants.map { merchant ->
                    ExportMerchant(
                        uid = merchant.uid,
                        name = merchant.name,
                        aliases = snapshot.aliases
                            .filter { it.merchantId == merchant.id }
                            .map { ExportAlias(it.aliasKey, it.descriptor, it.matchedBy.name) },
                        businessType = merchant.businessType?.name,
                        identifiedBy = merchant.identifiedBy?.name,
                        confidence = merchant.confidence,
                        namedByYou = merchant.namedByYou,
                        autoRuled = merchant.autoRuled
                    )
                },
                rawMessages = snapshot.rawMessages.map { message ->
                    ExportRawMessage(
                        sender = message.sender,
                        body = message.body,
                        receivedAt = message.receivedAt.toString(),
                        hash = message.hash,
                        status = message.status.name,
                        parserVersion = message.parserVersion,
                        unroutedRefs = message.unroutedRefs
                    )
                },
                balanceCheckpoints = snapshot.checkpoints.map { checkpoint ->
                    ExportCheckpoint(
                        accountUid = accountUids.getValue(checkpoint.accountId),
                        balanceMinor = checkpoint.balanceMinor,
                        at = checkpoint.at.toString(),
                        rawMessageHash = checkpoint.rawMessageId?.let(rawHashes::get)
                    )
                },
                people = snapshot.people.map { person ->
                    ExportPerson(
                        uid = person.uid,
                        name = person.name,
                        namedByYou = person.namedByYou,
                        aliases = snapshot.personAliases
                            .filter { it.personId == person.id }
                            .map { ExportPersonAlias(it.aliasKey, it.descriptor) }
                    )
                },
                loans = snapshot.loans.map { loan ->
                    ExportLoan(
                        uid = loan.uid,
                        personUid = personUids.getValue(loan.personId),
                        direction = loan.direction.name,
                        currency = loan.currency,
                        dueOn = loan.dueOn?.toString(),
                        createdAt = loan.createdAt.toString(),
                        events = snapshot.loanEvents.filter { it.loanId == loan.id }.map { event ->
                            ExportLoanEvent(
                                uid = event.uid,
                                type = event.type.name,
                                transactionUid = event.transactionId?.let(transactionUids::getValue),
                                amountMinor = event.amountMinor,
                                at = event.at?.toString()
                            )
                        },
                        splitOfTransactionUid = loan.splitOf?.let(transactionUids::getValue)
                    )
                },
                recurring = snapshot.recurring.map { series ->
                    ExportRecurring(
                        uid = series.uid,
                        kind = series.kind.name,
                        name = series.name,
                        merchantUid = series.merchantId?.let(merchantUids::getValue),
                        personUid = series.personId?.let(personUids::getValue),
                        amountMinor = series.amountMinor,
                        currency = series.currency,
                        every = series.every,
                        unit = series.unit.name,
                        anchor = series.anchor.toString(),
                        autoRenew = series.autoRenew,
                        endsOn = series.endsOn?.toString(),
                        reminderDays = series.reminderDays,
                        cancelReminder = series.cancelReminder,
                        status = series.status.name,
                        createdAt = series.createdAt.toString()
                    )
                },
                budgets = snapshot.budgets.map { budget ->
                    ExportBudget(
                        uid = budget.uid,
                        scope = budget.scope.name,
                        categoryUid = budget.categoryId?.let(categoryUids::getValue),
                        expenseType = budget.expenseType?.name,
                        merchantUid = budget.merchantId?.let(merchantUids::getValue),
                        amountMinor = budget.amountMinor,
                        currency = budget.currency,
                        rollover = budget.rollover,
                        createdAt = budget.createdAt.toString()
                    )
                },
                goals = snapshot.goals.map { goal ->
                    ExportGoal(
                        uid = goal.uid,
                        name = goal.name,
                        targetMinor = goal.targetMinor,
                        currency = goal.currency,
                        targetDate = goal.targetDate?.toString(),
                        accountUids = goal.accountIds.mapNotNull(accountUids::get),
                        createdAt = goal.createdAt.toString()
                    )
                },
                assets = snapshot.assets.map { asset ->
                    ExportAsset(
                        uid = asset.uid,
                        type = asset.type.name,
                        name = asset.name,
                        quantity = asset.quantity,
                        karat = asset.karat,
                        unitPrice = asset.unitPrice,
                        priceDate = asset.priceDate?.toString(),
                        valueMinor = asset.valueMinor,
                        costMinor = asset.costMinor,
                        spreadPercent = asset.spreadPercent,
                        depreciationPercent = asset.depreciationPercent,
                        currency = asset.currency,
                        createdAt = asset.createdAt.toString(),
                        priceSource = asset.priceSource
                    )
                },
                assetSnapshots = snapshot.snapshots.map { ExportSnapshot(it.date.toString(), it.assetsMinor, it.currency) },
                zakat = snapshot.zakat?.let {
                    ExportZakat(
                        it.hijriMonth, it.hijriDay, it.goldPricePerGram, it.includeAccounts, it.includeSavings,
                        it.includeFunds, it.includeGold, it.includeOwed, it.otherDebtsMinor, it.paidHijriYear, it.remind
                    )
                },
                scenarios = snapshot.scenarios.map {
                    ExportScenario(
                        it.uid, it.name, it.ageNow, it.retireAt, it.startMinor, it.monthlyMinor, it.returnPercent,
                        it.inflationPercent, it.wantedMinor, it.currency, it.createdAt.toString()
                    )
                },
                savingsTerms = snapshot.savingsTerms.map {
                    ExportSavingsTerms(
                        accountUids.getValue(it.accountId), it.kind.name, it.ratePercent, it.startDate?.toString(),
                        it.tenorMonths, it.maturityChoice?.name
                    )
                },
                tags = snapshot.tags.map {
                    ExportTag(it.uid, it.name, it.startsOn?.toString(), it.endsOn?.toString(), it.auto, it.createdAt.toString())
                },
                transactionTags = snapshot.tags.associate { it.id to it.uid }.let { tagUids ->
                    snapshot.transactionTags
                        .sortedWith(compareBy({ it.transactionId }, { it.tagId }))
                        .map { ExportTransactionTag(transactionUids.getValue(it.transactionId), tagUids.getValue(it.tagId), it.removed) }
                }
            )

            return prettyJson.encodeToString(ExportFile.serializer(), file)
        }

        /** `accounts.csv` and `transactions.csv`, zipped. */
        fun csvZip(snapshot: LedgerSnapshot, zone: ZoneId): ByteArray {
            val bytes = ByteArrayOutputStream()

            ZipOutputStream(bytes).use { zip ->
                for ((name, content) in csvFiles(snapshot, zone)) {
                    zip.putNextEntry(ZipEntry(name))
                    // A byte-order mark, so Excel reads the UTF-8 (Arabic names) as UTF-8.
                    zip.write(UTF8_BOM)
                    zip.write(content.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }

            return bytes.toByteArray()
        }

        /**
         * One table per entity. Amounts are signed decimals a spreadsheet can sum ("-214.50"),
         * with the exact minor units beside them; times are local, as you would read them.
         */
        fun csvFiles(snapshot: LedgerSnapshot, zone: ZoneId): Map<String, String> {
            val institutionNames = snapshot.institutions.associate { it.id to it.name }
            val accountsById = snapshot.accounts.associateBy { it.id }
            val transactionUids = snapshot.transactions.associate { it.id to it.uid }
            val categoryNames = snapshot.categories.associate { it.id to it.name }
            val merchantOf = snapshot.merchantOf()
            val counterpartOf = snapshot.transfers
                .flatMap { listOf(it.outTransactionId to it.inTransactionId, it.inTransactionId to it.outTransactionId) }
                .toMap()

            val accounts = Csv.table(
                header = listOf(
                    "uid", "name", "bank", "type", "last4", "currency",
                    "opening_balance", "balance", "archived"
                ),
                rows = snapshot.accounts.map { account ->
                    listOf(
                        account.uid,
                        Csv.text(account.nickname),
                        Csv.text(account.institutionId?.let(institutionNames::get)),
                        account.type.name,
                        account.last4.orEmpty(),
                        account.currency,
                        Csv.number(Money.plain(account.openingBalanceMinor, account.currency)),
                        Csv.number(Money.plain(snapshot.balances[account.id] ?: account.openingBalanceMinor, account.currency)),
                        account.archived.toString()
                    )
                }
            )

            val transactions = Csv.table(
                header = listOf(
                    "uid", "date", "time", "account", "account_uid", "kind", "title", "merchant", "category", "expense_type",
                    "amount", "amount_minor", "currency", "note", "source", "transfer_counterpart_uid"
                ),
                rows = snapshot.transactions.map { tx ->
                    val account = accountsById.getValue(tx.accountId)
                    val local = tx.occurredAt.atZone(zone)
                    val signed = if (tx.direction == Direction.DEBIT) -tx.amountMinor else tx.amountMinor

                    listOf(
                        tx.uid,
                        local.toLocalDate().toString(),
                        local.toLocalTime().format(TIME),
                        Csv.text(accountLabel(account.institutionId?.let(institutionNames::get), account.nickname)),
                        account.uid,
                        tx.kind.name,
                        Csv.text(tx.title),
                        Csv.text(merchantOf[tx.id]?.name),
                        Csv.text(tx.categoryId?.let(categoryNames::get)),
                        tx.expenseType?.name.orEmpty(),
                        Csv.number(Money.plain(signed, tx.currency)),
                        signed.toString(),
                        tx.currency,
                        Csv.text(tx.note),
                        tx.source.name,
                        counterpartOf[tx.id]?.let(transactionUids::get).orEmpty()
                    )
                }
            )

            return linkedMapOf("accounts.csv" to accounts, "transactions.csv" to transactions)
        }

        private val TIME = DateTimeFormatter.ofPattern("HH:mm")
        private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    }
}
