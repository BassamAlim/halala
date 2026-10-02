package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.definitions.DefinitionsFile
import bassamalim.halala.core.data.dataSources.room.daos.ClassificationDao
import bassamalim.halala.core.data.dataSources.room.daos.MerchantsDao
import bassamalim.halala.core.data.dataSources.room.daos.PeopleDao
import bassamalim.halala.core.data.dataSources.room.daos.TransactionsDao
import bassamalim.halala.core.data.dataSources.room.entities.AuditBatch
import bassamalim.halala.core.data.dataSources.room.entities.AuditChange
import bassamalim.halala.core.data.dataSources.room.entities.Category
import bassamalim.halala.core.data.dataSources.room.entities.Merchant
import bassamalim.halala.core.data.dataSources.room.entities.MerchantAlias
import bassamalim.halala.core.data.dataSources.room.entities.Person
import bassamalim.halala.core.data.dataSources.room.entities.PersonAlias
import bassamalim.halala.core.data.dataSources.room.entities.Rule
import bassamalim.halala.core.data.dataSources.room.entities.RuleActions
import bassamalim.halala.core.data.dataSources.room.entities.RuleConditions
import bassamalim.halala.core.data.dataSources.room.relations.AliasWithCount
import bassamalim.halala.core.data.dataSources.room.relations.BatchWithCount
import bassamalim.halala.core.data.dataSources.room.relations.CategoryWithUse
import bassamalim.halala.core.data.dataSources.room.relations.Filing
import bassamalim.halala.core.data.dataSources.room.relations.MerchantWithStats
import bassamalim.halala.core.data.dataSources.room.relations.RuleWithStats
import bassamalim.halala.core.data.dataSources.room.relations.ToIdentify
import bassamalim.halala.core.domain.Identification
import bassamalim.halala.core.domain.KnownMerchants
import bassamalim.halala.core.domain.MerchantLookup
import bassamalim.halala.core.domain.Merchants
import bassamalim.halala.core.domain.People
import bassamalim.halala.core.domain.Rules
import bassamalim.halala.core.domain.Tier
import bassamalim.halala.core.enums.AliasMatch
import bassamalim.halala.core.enums.AuditAction
import bassamalim.halala.core.enums.AuditEntity
import bassamalim.halala.core.enums.BusinessType
import bassamalim.halala.core.enums.ExpenseType
import bassamalim.halala.core.enums.IdentifiedBy
import bassamalim.halala.core.enums.RuleSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Categories, rules, merchants, and filing transactions under them. A transaction you filed
 * yourself has a category and no rule, and no rule ever changes it; everything else is the
 * rules' to file.
 *
 * Everything you do here is recorded as one batch (what every category, rule, merchant, alias
 * and filing read before and after), so it can be undone as one thing. What happens on its own
 * as SMS arrive isn't a batch: the rules filing them (the transaction names its rule, and the
 * rule is what you would change), new descriptors finding their merchant (the merchant
 * screen says how each one did, and is where you would change it), and merchants being
 * identified, with the automatic rules that file them (the rule says why).
 */
@Singleton
class ClassificationRepository @Inject constructor(
    private val classificationDao: ClassificationDao,
    private val merchantsDao: MerchantsDao,
    private val transactionsDao: TransactionsDao,
    private val peopleDao: PeopleDao,
    private val definitions: DefinitionsFile,
    private val clock: Clock
) {

    /**
     * One writer at a time: an SMS run and the app opening may both resolve merchants, and two
     * of them would give one new descriptor two merchants.
     */
    private val writing = Mutex()

    fun observeCategories(): Flow<List<Category>> = classificationDao.observeCategories()

    suspend fun getCategories(): List<Category> = classificationDao.getCategories()

    fun observeCategoriesWithUse(): Flow<List<CategoryWithUse>> = classificationDao.observeCategoriesWithUse()

    suspend fun addCategory(name: String, expenseType: ExpenseType?): Long = classificationDao.insertCategory(
        Category(uid = UUID.randomUUID().toString(), name = name, expenseType = expenseType)
    )

    /**
     * Renames a category, changes its type, or the business types it takes. A business type
     * belongs to one category: taking one here takes it from wherever it was. What the
     * automatic rules file follows the change.
     */
    suspend fun editCategory(id: Long, name: String, expenseType: ExpenseType?, businessTypes: List<BusinessType>): Long? {
        val category = classificationDao.getCategory(id) ?: return null
        val taken = businessTypes.filter { it != BusinessType.UNKNOWN }.distinct()

        return audited(AuditAction.CATEGORY_EDITED, category.name, name) {
            for (other in classificationDao.getCategories()) {
                if (other.id == id || other.businessTypes.none { it in taken }) continue
                classificationDao.updateCategory(other.copy(businessTypes = other.businessTypes - taken.toSet()))
            }
            classificationDao.updateCategory(category.copy(name = name, expenseType = expenseType, businessTypes = taken))
            syncAutoRules()
            fileByRules()
        }
    }

    /**
     * Removes a category. Rules that file under it go with it (they would point at nothing), and
     * what was filed under it is uncategorised again, back in the review inbox.
     */
    suspend fun deleteCategory(id: Long) {
        val category = classificationDao.getCategory(id) ?: return
        audited(AuditAction.CATEGORY_DELETED, category.name) {
            classificationDao.getRules()
                .filter { it.actions.categoryId == id }
                .forEach { if (it.source == RuleSource.AI) dropAutoRule(it) else classificationDao.deleteRule(it.id) }
            transactionsDao.clearTypeOf(id)
            classificationDao.deleteCategory(id)
        }
    }

    fun observeRules(): Flow<List<RuleWithStats>> = classificationDao.observeRules()

    suspend fun getRules(): List<Rule> = classificationDao.getRules()

    suspend fun getRule(id: Long): Rule? = classificationDao.getRule(id)

    /** Your own answer for one transaction. It sticks: rules leave it alone from now on. */
    suspend fun file(transactionId: Long, categoryId: Long?, expenseType: ExpenseType?, typeOnly: Boolean = false): Long? {
        val title = transactionsDao.get(transactionId)?.title.orEmpty()
        val category = categoryId?.let { classificationDao.getCategory(it) }?.name.orEmpty()

        return audited(if (typeOnly) AuditAction.TYPE_CHANGED else AuditAction.FILED, title, category) {
            transactionsDao.setCategory(listOf(transactionId), categoryId, expenseType, ruleId = null)
        }
    }

    /** A category's own type: what choosing it fills in. */
    suspend fun defaultTypeOf(categoryId: Long): ExpenseType? = classificationDao.getCategory(categoryId)?.expenseType

    /**
     * Never teach it twice: [merchant] goes under [categoryId] from now on, and so does all of
     * its past spending that you haven't filed yourself. A merchant has one learned rule; a new
     * answer replaces the old one. [alsoFile] is the transaction the answer was given on, filed
     * as yours in the same batch. Returns the batch, to offer its undo.
     */
    suspend fun learn(merchant: String, categoryId: Long, alsoFile: Long? = null): Long? {
        val category = classificationDao.getCategory(categoryId) ?: return null
        // The descriptor finds its merchant first, so the rule is taught the merchant.
        writing.withLock { resolveMerchants() }
        val key = Merchants.key(merchant)
        val lookup = lookup()
        val merchantId = lookup.of(key)
        val name = merchantId?.let { merchantsDao.getMerchant(it)?.name } ?: merchant.trim()

        return audited(AuditAction.LEARNED, name, category.name) {
            if (alsoFile != null)
                transactionsDao.setCategory(listOf(alsoFile), categoryId, category.expenseType, ruleId = null)

            val actions = RuleActions(categoryId, category.expenseType)
            val existing = classificationDao.getRules().firstOrNull { rule ->
                val conditions = rule.conditions
                rule.source == RuleSource.LEARNED && conditions.size == 1 && when {
                    conditions.merchantId != null -> conditions.merchantId == merchantId
                    conditions.merchant == null -> false
                    else -> Merchants.key(conditions.merchant).let { it == key || (merchantId != null && lookup.of(it) == merchantId) }
                }
            }
            val conditions = RuleConditions(merchant = name, merchantId = merchantId)

            if (existing != null)
                classificationDao.updateRule(existing.copy(conditions = conditions, actions = actions, enabled = true))
            else classificationDao.insertRule(
                Rule(
                    uid = UUID.randomUUID().toString(),
                    conditions = conditions,
                    actions = actions,
                    source = RuleSource.LEARNED,
                    createdAt = clock.instant()
                )
            )
            fileByRules()
        }
    }

    /** How many transactions, other than [exceptId], a rule for [merchant] could file: every spelling of it. */
    suspend fun countFor(merchant: String, exceptId: Long): Int {
        val conditions = RuleConditions(merchant = merchant)
        val lookup = lookup()
        return transactionsDao.getRuleCandidates().count {
            it.id != exceptId && it.kind.countsInTotals && Rules.matches(conditions, it, lookup)
        }
    }

    /**
     * Writes a rule by hand ([id] 0), or rewrites one, and re-files what it now matches. A rule
     * keeps who made it: editing a learned rule doesn't make it yours.
     */
    suspend fun saveRule(id: Long, conditions: RuleConditions, actions: RuleActions) {
        val category = classificationDao.getCategory(actions.categoryId)?.name.orEmpty()

        audited(AuditAction.RULE_SAVED, conditions.subject, category) {
            val existing = classificationDao.getRule(id)
            if (existing != null) classificationDao.updateRule(existing.copy(conditions = conditions, actions = actions))
            else classificationDao.insertRule(
                Rule(
                    uid = UUID.randomUUID().toString(),
                    conditions = conditions,
                    actions = actions,
                    source = RuleSource.MANUAL,
                    createdAt = clock.instant()
                )
            )
            fileByRules()
        }
    }

    /** A rule that is off files nothing new; what it filed stays filed. */
    suspend fun setEnabled(ruleId: Long, enabled: Boolean) {
        val rule = classificationDao.getRule(ruleId) ?: return
        audited(if (enabled) AuditAction.RULE_ON else AuditAction.RULE_OFF, rule.conditions.subject) {
            classificationDao.updateRule(rule.copy(enabled = enabled))
            fileByRules()
        }
    }

    /** What the rule filed keeps its category, as if you had chosen it. */
    suspend fun delete(ruleId: Long) {
        val rule = classificationDao.getRule(ruleId) ?: return
        audited(AuditAction.RULE_DELETED, rule.conditions.subject) { classificationDao.deleteRule(ruleId) }
    }

    /**
     * Finds every new descriptor its merchant and every new name its person, then runs every enabled rule over everything a
     * rule may file, and writes only what changes, so it can run after every SMS, every save,
     * and whenever the app opens.
     */
    suspend fun applyRules() = writing.withLock {
        resolveMerchants()
        resolvePeople()
        identifyKnown()
        syncAutoRules()
        fileByRules()
    }

    /** Runs the rules alone, for what you do here (inside [audited], which holds the lock). */
    // ponytail: re-reads every candidate and tries every rule on each; index rules by merchant
    // key and narrow to new rows if history gets slow.
    private suspend fun fileByRules() {
        val rules = classificationDao.getRules()
        if (rules.none { it.enabled }) return
        val ruleFor = Rules.matcher(rules, lookup())

        transactionsDao.getRuleCandidates()
            .filter { it.kind.countsInTotals }
            .groupBy(ruleFor)
            .forEach { (rule, transactions) ->
                if (rule == null) return@forEach
                val stale = transactions.filter {
                    it.ruleId != rule.id || it.categoryId != rule.actions.categoryId ||
                            it.expenseType != rule.actions.expenseType
                }
                // Under SQLite's limit of 999 bound values.
                stale.map { it.id }.chunked(500).forEach { ids ->
                    transactionsDao.setCategory(ids, rule.actions.categoryId, rule.actions.expenseType, rule.id)
                }
            }
    }

    // Merchants.

    fun observeMerchants(): Flow<List<MerchantWithStats>> = merchantsDao.observeMerchants()

    fun observeMerchant(id: Long): Flow<Merchant?> = merchantsDao.observeMerchant(id)

    fun observeAliases(merchantId: Long): Flow<List<AliasWithCount>> = merchantsDao.observeAliases(merchantId)

    suspend fun getMerchants(): List<Merchant> = merchantsDao.getMerchants()

    suspend fun getMerchant(id: Long): Merchant? = merchantsDao.getMerchant(id)

    suspend fun getAliases(): List<MerchantAlias> = merchantsDao.getAliases()

    /** What you call it. The bank's descriptors stay as they were written. */
    suspend fun renameMerchant(id: Long, name: String): Long? {
        val merchant = merchantsDao.getMerchant(id) ?: return null
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == merchant.name) return null

        return audited(AuditAction.MERCHANT_RENAMED, merchant.name, trimmed) {
            merchantsDao.updateMerchant(merchant.copy(name = trimmed, namedByYou = true))
            syncAutoRules()
        }
    }

    /**
     * What the business is, as you say: it files as that from now on, whatever the list or the
     * AI said, and its automatic rule (if you had deleted it) is made again.
     */
    suspend fun setBusinessType(merchantId: Long, type: BusinessType): Long? {
        val merchant = merchantsDao.getMerchant(merchantId) ?: return null
        if (merchant.identifiedBy == IdentifiedBy.YOU && merchant.businessType == type) return null

        return audited(AuditAction.MERCHANT_TYPED, merchant.name, type.name) {
            classificationDao.getRules().filter { it.isAutoFor(merchantId) }.forEach { dropAutoRule(it) }
            merchantsDao.updateMerchant(
                merchant.copy(businessType = type, identifiedBy = IdentifiedBy.YOU, confidence = null, autoRuled = false)
            )
            syncAutoRules()
            fileByRules()
        }
    }

    /**
     * [fromId] is [intoId] by another name: its descriptors, and its rules, become [intoId]'s.
     * When both were taught a category, [intoId]'s answer stands, and what [fromId]'s rule filed
     * goes back for it to file.
     */
    suspend fun mergeMerchants(fromId: Long, intoId: Long): Long? {
        if (fromId == intoId) return null
        val from = merchantsDao.getMerchant(fromId) ?: return null
        val into = merchantsDao.getMerchant(intoId) ?: return null

        return audited(AuditAction.MERCHANTS_MERGED, from.name, into.name) {
            val rules = classificationDao.getRules()
            val intoLearned = rules.any { it.isLearnedFor(intoId) }

            for (rule in rules.filter { it.conditions.merchantId == fromId }) {
                if (intoLearned && rule.isLearnedFor(fromId)) {
                    classificationDao.unfileRule(rule.id)
                    classificationDao.deleteRule(rule.id)
                } else classificationDao.updateRule(
                    rule.copy(conditions = rule.conditions.copy(merchant = into.name, merchantId = intoId))
                )
            }
            for (alias in merchantsDao.getAliases().filter { it.merchantId == fromId })
                merchantsDao.updateAlias(alias.copy(merchantId = intoId, matchedBy = AliasMatch.YOU))
            // What the merged-away merchant was known to be, when the other wasn't known yet.
            if (into.identifiedBy == null && from.identifiedBy != null)
                merchantsDao.updateMerchant(
                    into.copy(businessType = from.businessType, identifiedBy = from.identifiedBy, confidence = from.confidence)
                )
            merchantsDao.moveSeries(fromId, intoId)
            merchantsDao.moveBudgets(fromId, intoId)
            merchantsDao.deleteMerchant(fromId)
            syncAutoRules()
            fileByRules()
        }
    }

    /**
     * "Not this merchant": one descriptor becomes a merchant of its own. What the old
     * merchant's rules filed under it goes back to review, since that answer was about the
     * other merchant; what you filed yourself stays.
     */
    suspend fun splitAlias(aliasId: Long): Long? {
        val alias = merchantsDao.getAlias(aliasId) ?: return null
        val from = merchantsDao.getMerchant(alias.merchantId) ?: return null
        if (merchantsDao.getAliases().count { it.merchantId == from.id } < 2) return null

        return audited(AuditAction.ALIAS_SPLIT, alias.descriptor, from.name) {
            val merchantId = merchantsDao.insertMerchant(
                Merchant(uid = UUID.randomUUID().toString(), name = Merchants.nameOf(alias.descriptor))
            )
            merchantsDao.updateAlias(alias.copy(merchantId = merchantId, matchedBy = AliasMatch.YOU))
            merchantsDao.unfileByRules(alias.aliasKey)
            fileByRules()
        }
    }

    private fun Rule.isLearnedFor(merchantId: Long) =
        source == RuleSource.LEARNED && conditions.size == 1 && conditions.merchantId == merchantId

    private fun Rule.isAutoFor(merchantId: Long) = source == RuleSource.AI && conditions.merchantId == merchantId

    // Identifying merchants.

    /** Every merchant as it is, with what it was identified as. */
    fun observeAllMerchants(): Flow<List<Merchant>> = merchantsDao.observeAll()

    /** The merchants waiting to be identified, the busiest first, each with the name a bank wrote. */
    suspend fun toIdentify(): List<ToIdentify> = merchantsDao.getToIdentify()

    /**
     * What the AI said each merchant is (id → its answer). A merchant something else has
     * identified since keeps that; one you named keeps your name. Then the automatic rules file
     * what they now can.
     */
    suspend fun recordIdentifications(answers: Map<Long, IdentifiedAs>) = writing.withLock {
        for ((id, answer) in answers) {
            val merchant = merchantsDao.getMerchant(id) ?: continue
            if (merchant.identifiedBy != null) continue
            val name = answer.name.trim().takeIf { it.isNotEmpty() && it.length <= MAX_NAME && !merchant.namedByYou }
            merchantsDao.updateMerchant(
                merchant.copy(
                    name = name ?: merchant.name,
                    businessType = answer.type,
                    identifiedBy = IdentifiedBy.AI,
                    confidence = answer.confidence.coerceIn(0, 100)
                )
            )
        }
        syncAutoRules()
        fileByRules()
    }

    /** Merchants whose name is never sent (it may hold more than a shop's name): left for you. */
    suspend fun withhold(ids: Collection<Long>) = writing.withLock {
        for (id in ids) {
            val merchant = merchantsDao.getMerchant(id) ?: continue
            if (merchant.identifiedBy == null) merchantsDao.updateMerchant(merchant.copy(identifiedBy = IdentifiedBy.WITHHELD))
        }
    }

    /**
     * The well-known merchants, from the bundled list and the definitions file: no call, and
     * before any is made. The list beats what the AI said, never what you said; the file's
     * categories are made when missing (one you delete comes back while the file names it).
     */
    private suspend fun identifyKnown() {
        val defined = definitions.read()
        val have = classificationDao.getCategories().map { it.name.lowercase() }
        for ((name, type) in defined.categories)
            if (name.isNotBlank() && name.lowercase() !in have) addCategory(name.trim(), type)

        val keys = merchantsDao.getAliases().groupBy({ it.merchantId }, { it.aliasKey })
        for (merchant in merchantsDao.getMerchants()) {
            if (merchant.identifiedBy == IdentifiedBy.YOU) continue
            val known = KnownMerchants.identify(keys[merchant.id].orEmpty(), defined) ?: continue
            val name = if (merchant.namedByYou) merchant.name else known.name
            val same = merchant.businessType == known.type
            if (same && merchant.identifiedBy == IdentifiedBy.LIST && merchant.name == name) continue
            merchantsDao.updateMerchant(
                merchant.copy(
                    name = name,
                    businessType = known.type,
                    identifiedBy = IdentifiedBy.LIST,
                    confidence = null,
                    autoRuled = merchant.autoRuled && same
                )
            )
        }
    }

    /**
     * Keeps one automatic rule ("Merchant is Panda → Groceries", source AI) for each merchant
     * whose spending files itself (`Tier.AUTO`), pointing at the category that takes its business
     * type, and none for the rest. A rule you delete isn't made again ([Merchant.autoRuled]); one
     * you turn off stays off. Your rules and learned ones still beat it. A merchant the definitions
     * file sends to a category goes there, whatever its type.
     */
    private suspend fun syncAutoRules() {
        val defined = definitions.read()
        val keys = merchantsDao.getAliases().groupBy({ it.merchantId }, { it.aliasKey })
        val categories = classificationDao.getCategories()
        val merchants = merchantsDao.getMerchants()
        val autoRules = classificationDao.getRules().filter { it.source == RuleSource.AI }
        val known = merchants.map { it.id }.toSet()
        autoRules.filter { it.conditions.merchantId !in known }.forEach { dropAutoRule(it) }
        val rulesOf = autoRules.groupBy { it.conditions.merchantId }

        for (merchant in merchants) {
            val rules = rulesOf[merchant.id].orEmpty()
            val defined = merchant.takeIf { it.identifiedBy == IdentifiedBy.LIST }
                ?.let { KnownMerchants.identify(keys[it.id].orEmpty(), defined)?.category }
                ?.let { name -> categories.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } }
            val category = defined ?: Identification.categoryFor(merchant.businessType, categories)
                ?.takeIf { Identification.tierOf(merchant, categories) == Tier.AUTO }

            if (category == null) {
                rules.forEach { dropAutoRule(it) }
                continue
            }
            val conditions = RuleConditions(merchant = merchant.name, merchantId = merchant.id)
            val actions = RuleActions(category.id, category.expenseType)
            val rule = rules.firstOrNull()
            when {
                rule != null -> {
                    if (rule.conditions != conditions || rule.actions != actions)
                        classificationDao.updateRule(rule.copy(conditions = conditions, actions = actions))
                    rules.drop(1).forEach { dropAutoRule(it, forget = false) }
                }
                !merchant.autoRuled -> {
                    classificationDao.insertRule(
                        Rule(
                            uid = UUID.randomUUID().toString(),
                            conditions = conditions,
                            actions = actions,
                            source = RuleSource.AI,
                            createdAt = clock.instant()
                        )
                    )
                    merchantsDao.updateMerchant(merchant.copy(autoRuled = true))
                }
            }
        }
    }

    /**
     * Takes away an automatic rule and what it filed (back to review, not left as if you had
     * chosen it). Unless it was only a duplicate ([forget] false), the merchant may have one
     * again when it files itself once more.
     */
    private suspend fun dropAutoRule(rule: Rule, forget: Boolean = true) {
        classificationDao.unfileRule(rule.id)
        classificationDao.deleteRule(rule.id)
        if (!forget) return
        rule.conditions.merchantId?.let { merchantsDao.getMerchant(it) }
            ?.takeIf { it.autoRuled }
            ?.let { merchantsDao.updateMerchant(it.copy(autoRuled = false)) }
    }

    private suspend fun lookup() = MerchantLookup(merchantsDao.getAliases(), merchantsDao.getMerchants())

    /**
     * Keeps every transaction's merchant key in step with its title, then gives each descriptor
     * not seen before its merchant: the one it is spelled like, else a new one named after it.
     * Oldest first, so the first spelling of a merchant is the one others are compared with.
     */
    private suspend fun resolveMerchants() {
        val rows = merchantsDao.getKeyRows()
        val keys = rows.associate { it.id to Merchants.key(it.title) }
        val stale = rows.filter { keys.getValue(it.id) != it.merchantKey }
        if (stale.isNotEmpty()) merchantsDao.setMerchantKeys(stale.associate { it.id to keys.getValue(it.id) })

        val aliases = merchantsDao.getAliases().associate { it.aliasKey to it.merchantId }.toMutableMap()
        for (row in rows) {
            val key = keys.getValue(row.id)
            if (row.kind !in Merchants.KINDS || row.title.isBlank() || key in aliases) continue

            val similar = Merchants.similarTo(key, aliases)
            val merchantId = similar ?: merchantsDao.insertMerchant(
                Merchant(uid = UUID.randomUUID().toString(), name = Merchants.nameOf(row.title))
            )
            merchantsDao.insertAlias(
                MerchantAlias(
                    merchantId = merchantId,
                    aliasKey = key,
                    descriptor = row.title.trim(),
                    matchedBy = if (similar != null) AliasMatch.SIMILAR else AliasMatch.FIRST
                )
            )
            aliases[key] = merchantId
        }
    }

    /**
     * Gives each name a transfer was written with, not seen before, its person: always a new
     * one, never a look-alike (two people can share most of a name). Runs after
     * [resolveMerchants], which keeps the keys in step with the titles.
     */
    private suspend fun resolvePeople() {
        val known = peopleDao.getAliases().map { it.aliasKey }.toMutableSet()
        for (name in peopleDao.getTransferNames()) {
            if (name.merchantKey.isBlank() || name.merchantKey in known) continue
            val personId = peopleDao.insertPerson(
                Person(uid = UUID.randomUUID().toString(), name = People.nameOf(name.title))
            )
            peopleDao.insertAlias(PersonAlias(personId = personId, aliasKey = name.merchantKey, descriptor = name.title.trim()))
            known += name.merchantKey
        }
    }

    // The history of changes.

    fun observeBatches(): Flow<List<BatchWithCount>> = classificationDao.observeBatches()

    /**
     * Puts back what a batch changed: the categories and rules it changed or deleted, then each
     * transaction's filing, then away with what it created. Each row is put back only where it
     * still reads as the batch left it, so nothing you did since is overwritten.
     */
    suspend fun undo(batchId: Long) = writing.withLock {
        val batch = classificationDao.getBatch(batchId) ?: return@withLock
        if (batch.undoneAt != null) return@withLock
        val changes = classificationDao.getChanges(batchId).groupBy { it.entity }
        val created = mutableListOf<AuditChange>()

        for (change in changes[AuditEntity.CATEGORY].orEmpty()) {
            val old = change.old?.let { json.decodeFromString<Category>(it) }
            val now = classificationDao.getCategory(change.entityId)
            when {
                old == null -> created += change
                now == null -> classificationDao.insertCategory(old)
                now == json.decodeFromString<Category>(change.new!!) -> classificationDao.updateCategory(old)
            }
        }
        for (change in changes[AuditEntity.MERCHANT].orEmpty()) {
            val old = change.old?.let { json.decodeFromString<Merchant>(it) }
            val now = merchantsDao.getMerchant(change.entityId)
            when {
                old == null -> created += change
                now == null -> merchantsDao.insertMerchant(old)
                now == json.decodeFromString<Merchant>(change.new!!) -> merchantsDao.updateMerchant(old)
            }
        }
        val merchantIds = merchantsDao.getMerchants().map { it.id }.toSet()
        for (change in changes[AuditEntity.ALIAS].orEmpty()) {
            val old = change.old?.let { json.decodeFromString<MerchantAlias>(it) }
            val now = merchantsDao.getAlias(change.entityId)
            when {
                old == null -> created += change
                // A merchant that has since gone can't take it back.
                old.merchantId !in merchantIds -> Unit
                now == null -> merchantsDao.insertAlias(old)
                now == json.decodeFromString<MerchantAlias>(change.new!!) -> merchantsDao.updateAlias(old)
            }
        }
        for (change in changes[AuditEntity.RULE].orEmpty()) {
            val old = change.old?.let { json.decodeFromString<RuleSnapshot>(it).toRule() }
            val now = classificationDao.getRule(change.entityId)
            when {
                old == null -> created += change
                // A rule whose category has since gone would point at nothing.
                classificationDao.getCategory(old.actions.categoryId) == null -> Unit
                now == null -> classificationDao.insertRule(old)
                now == json.decodeFromString<RuleSnapshot>(change.new!!).toRule() -> classificationDao.updateRule(old)
            }
        }

        val categoryIds = classificationDao.getCategories().map { it.id }.toSet()
        val ruleIds = classificationDao.getRules().map { it.id }.toSet()
        for (change in changes[AuditEntity.TRANSACTION].orEmpty()) {
            val old = json.decodeFromString<Filing>(change.old ?: continue)
            val new = json.decodeFromString<Filing>(change.new ?: continue)
            if (old.categoryId != null && old.categoryId !in categoryIds) continue

            classificationDao.restoreFiling(
                id = change.entityId,
                categoryId = old.categoryId,
                expenseType = old.expenseType,
                ruleId = old.ruleId?.takeIf { it in ruleIds },
                nowCategoryId = new.categoryId,
                nowExpenseType = new.expenseType,
                nowRuleId = new.ruleId
            )
        }

        // A created merchant goes after its created aliases, so it is empty when it goes.
        for (change in created.sortedBy { it.entity == AuditEntity.MERCHANT }) when (change.entity) {
            AuditEntity.RULE ->
                if (classificationDao.getRule(change.entityId)?.let(::snapshot) == json.decodeFromString<RuleSnapshot>(change.new!!))
                    classificationDao.deleteRule(change.entityId)
            AuditEntity.CATEGORY ->
                if (classificationDao.getCategory(change.entityId) == json.decodeFromString<Category>(change.new!!))
                    classificationDao.deleteCategory(change.entityId)
            AuditEntity.ALIAS ->
                if (merchantsDao.getAlias(change.entityId) == json.decodeFromString<MerchantAlias>(change.new!!))
                    merchantsDao.deleteAlias(change.entityId)
            // Its aliases go with it; any still there are ones put back above, so it stays.
            AuditEntity.MERCHANT ->
                if (merchantsDao.getMerchant(change.entityId) == json.decodeFromString<Merchant>(change.new!!) &&
                    merchantsDao.getAliases().none { it.merchantId == change.entityId })
                    merchantsDao.deleteMerchant(change.entityId)
            AuditEntity.TRANSACTION -> Unit
        }

        classificationDao.markUndone(batchId, clock.instant())
    }

    /**
     * Runs [block] and records what it changed as one batch. Returns the batch's id, or null
     * when nothing changed.
     */
    private suspend fun audited(
        action: AuditAction,
        subject: String,
        detail: String = "",
        block: suspend () -> Unit
    ): Long? = writing.withLock {
        val before = state()
        block()
        val after = state()

        val changes = diff(AuditEntity.CATEGORY, before.categories, after.categories) { json.encodeToString(it) } +
                diff(AuditEntity.RULE, before.rules, after.rules) { json.encodeToString(snapshot(it)) } +
                diff(AuditEntity.MERCHANT, before.merchants, after.merchants) { json.encodeToString(it) } +
                diff(AuditEntity.ALIAS, before.aliases, after.aliases) { json.encodeToString(it) } +
                diff(AuditEntity.TRANSACTION, before.filings, after.filings) { json.encodeToString(it) }
        if (changes.isEmpty()) return@withLock null

        val batchId = classificationDao.insertBatch(
            AuditBatch(action = action, subject = subject, detail = detail, at = clock.instant())
        )
        classificationDao.insertChanges(changes.map { it.copy(batchId = batchId) })
        batchId
    }

    private data class State(
        val categories: Map<Long, Category>,
        val rules: Map<Long, Rule>,
        val merchants: Map<Long, Merchant>,
        val aliases: Map<Long, MerchantAlias>,
        val filings: Map<Long, Filing>
    )

    // ponytail: reads every transaction's filing before and after each thing you do; log from
    // the writes themselves if a large history makes a tap feel slow.
    private suspend fun state() = State(
        categories = classificationDao.getCategories().associateBy { it.id },
        rules = classificationDao.getRules().associateBy { it.id },
        merchants = merchantsDao.getMerchants().associateBy { it.id },
        aliases = merchantsDao.getAliases().associateBy { it.id },
        filings = classificationDao.getFilings().associate { it.id to it.filing }
    )

    private fun <T> diff(entity: AuditEntity, before: Map<Long, T>, after: Map<Long, T>, encode: (T) -> String) =
        (before.keys + after.keys).mapNotNull { id ->
            val old = before[id]
            val new = after[id]
            if (old == new) null
            else AuditChange(batchId = 0, entity = entity, entityId = id, old = old?.let(encode), new = new?.let(encode))
        }

    /** A rule as the log keeps it (an Instant isn't serializable as it stands). */
    @Serializable
    private data class RuleSnapshot(
        val id: Long,
        val uid: String,
        val conditions: RuleConditions,
        val actions: RuleActions,
        val source: RuleSource,
        val enabled: Boolean,
        val createdAt: Long
    ) {
        fun toRule() = Rule(id, uid, conditions, actions, source, enabled, Instant.ofEpochMilli(createdAt))
    }

    private fun snapshot(rule: Rule) = RuleSnapshot(
        rule.id, rule.uid, rule.conditions, rule.actions, rule.source, rule.enabled, rule.createdAt.toEpochMilli()
    )

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        /** A name the AI tidied that is longer than this is not a name; the bank's stays. */
        const val MAX_NAME = 60
    }
}

/** What the AI said a merchant is: its name, what the business is, and how sure it was (0–100). */
data class IdentifiedAs(val name: String, val type: BusinessType, val confidence: Int)
