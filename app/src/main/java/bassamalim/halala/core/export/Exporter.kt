package bassamalim.halala.core.export

import bassamalim.halala.core.data.dataSources.room.entities.Account
import bassamalim.halala.core.data.dataSources.room.entities.Category
import bassamalim.halala.core.data.dataSources.room.entities.Institution
import bassamalim.halala.core.data.dataSources.room.entities.InternalTransfer
import bassamalim.halala.core.data.dataSources.room.entities.Merchant
import bassamalim.halala.core.data.dataSources.room.entities.MerchantAlias
import bassamalim.halala.core.data.dataSources.room.entities.Rule
import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.InstitutionsRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
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
    val aliases: List<MerchantAlias> = emptyList()
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
        aliases = classificationRepository.getAliases()
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
                        createdAt = account.createdAt.toString()
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
                        merchantUid = merchantOf[tx.id]?.uid
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
                        createdAt = rule.createdAt.toString()
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
                        confidence = merchant.confidence
                    )
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
