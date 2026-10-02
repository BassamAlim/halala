package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.ClassificationDao
import bassamalim.halala.core.data.dataSources.room.daos.TransactionsDao
import bassamalim.halala.core.data.dataSources.room.entities.AuditBatch
import bassamalim.halala.core.data.dataSources.room.entities.AuditChange
import bassamalim.halala.core.data.dataSources.room.entities.Category
import bassamalim.halala.core.data.dataSources.room.entities.Rule
import bassamalim.halala.core.data.dataSources.room.entities.RuleActions
import bassamalim.halala.core.data.dataSources.room.entities.RuleConditions
import bassamalim.halala.core.data.dataSources.room.relations.BatchWithCount
import bassamalim.halala.core.data.dataSources.room.relations.CategoryWithUse
import bassamalim.halala.core.data.dataSources.room.relations.Filing
import bassamalim.halala.core.data.dataSources.room.relations.RuleWithStats
import bassamalim.halala.core.domain.Rules
import bassamalim.halala.core.enums.AuditAction
import bassamalim.halala.core.enums.AuditEntity
import bassamalim.halala.core.enums.ExpenseType
import bassamalim.halala.core.enums.RuleSource
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Categories, rules, and filing transactions under them. A transaction you filed yourself has a
 * category and no rule, and no rule ever changes it; everything else is the rules' to file.
 *
 * Everything you do here is recorded as one batch (what every category, rule and filing read
 * before and after), so it can be undone as one thing. What the rules file on their own as SMS
 * arrive isn't a batch: the transaction names its rule, and the rule is what you would change.
 */
@Singleton
class ClassificationRepository @Inject constructor(
    private val classificationDao: ClassificationDao,
    private val transactionsDao: TransactionsDao,
    private val clock: Clock
) {

    fun observeCategories(): Flow<List<Category>> = classificationDao.observeCategories()

    suspend fun getCategories(): List<Category> = classificationDao.getCategories()

    fun observeCategoriesWithUse(): Flow<List<CategoryWithUse>> = classificationDao.observeCategoriesWithUse()

    suspend fun addCategory(name: String, expenseType: ExpenseType?): Long = classificationDao.insertCategory(
        Category(uid = UUID.randomUUID().toString(), name = name, expenseType = expenseType)
    )

    /**
     * Removes a category. Rules that file under it go with it (they would point at nothing), and
     * what was filed under it is uncategorised again, back in the review inbox.
     */
    suspend fun deleteCategory(id: Long) {
        val category = classificationDao.getCategory(id) ?: return
        audited(AuditAction.CATEGORY_DELETED, category.name) {
            classificationDao.getRules()
                .filter { it.actions.categoryId == id }
                .forEach { classificationDao.deleteRule(it.id) }
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

        return audited(AuditAction.LEARNED, merchant.trim(), category.name) {
            if (alsoFile != null)
                transactionsDao.setCategory(listOf(alsoFile), categoryId, category.expenseType, ruleId = null)

            val key = Rules.merchantKey(merchant)
            val actions = RuleActions(categoryId, category.expenseType)
            val existing = classificationDao.getRules().firstOrNull {
                it.source == RuleSource.LEARNED && it.conditions.size == 1 &&
                        it.conditions.merchant?.let(Rules::merchantKey) == key
            }

            if (existing != null) classificationDao.updateRule(existing.copy(actions = actions, enabled = true))
            else classificationDao.insertRule(
                Rule(
                    uid = UUID.randomUUID().toString(),
                    conditions = RuleConditions(merchant = merchant.trim()),
                    actions = actions,
                    source = RuleSource.LEARNED,
                    createdAt = clock.instant()
                )
            )
            applyRules()
        }
    }

    /** How many transactions, other than [exceptId], a rule for [merchant] could file. */
    suspend fun countFor(merchant: String, exceptId: Long): Int {
        val key = Rules.merchantKey(merchant)
        return transactionsDao.getRuleCandidates().count {
            it.id != exceptId && it.kind.countsInTotals && Rules.merchantKey(it.title) == key
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
            applyRules()
        }
    }

    /** A rule that is off files nothing new; what it filed stays filed. */
    suspend fun setEnabled(ruleId: Long, enabled: Boolean) {
        val rule = classificationDao.getRule(ruleId) ?: return
        audited(if (enabled) AuditAction.RULE_ON else AuditAction.RULE_OFF, rule.conditions.subject) {
            classificationDao.updateRule(rule.copy(enabled = enabled))
            applyRules()
        }
    }

    /** What the rule filed keeps its category, as if you had chosen it. */
    suspend fun delete(ruleId: Long) {
        val rule = classificationDao.getRule(ruleId) ?: return
        audited(AuditAction.RULE_DELETED, rule.conditions.subject) { classificationDao.deleteRule(ruleId) }
    }

    /**
     * Runs every enabled rule over everything a rule may file, and writes only what changes, so
     * it can run after every SMS, every save and every rule edit.
     */
    // ponytail: re-reads every candidate and tries every rule on each; index rules by merchant
    // key and narrow to new rows if history gets slow.
    suspend fun applyRules() {
        val rules = classificationDao.getRules()
        if (rules.none { it.enabled }) return
        val ruleFor = Rules.matcher(rules)

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

    // The history of changes.

    fun observeBatches(): Flow<List<BatchWithCount>> = classificationDao.observeBatches()

    /**
     * Puts back what a batch changed: the categories and rules it changed or deleted, then each
     * transaction's filing, then away with what it created. Each row is put back only where it
     * still reads as the batch left it, so nothing you did since is overwritten.
     */
    suspend fun undo(batchId: Long) {
        val batch = classificationDao.getBatch(batchId) ?: return
        if (batch.undoneAt != null) return
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

        for (change in created) when (change.entity) {
            AuditEntity.RULE ->
                if (classificationDao.getRule(change.entityId)?.let(::snapshot) == json.decodeFromString<RuleSnapshot>(change.new!!))
                    classificationDao.deleteRule(change.entityId)
            AuditEntity.CATEGORY ->
                if (classificationDao.getCategory(change.entityId) == json.decodeFromString<Category>(change.new!!))
                    classificationDao.deleteCategory(change.entityId)
            AuditEntity.TRANSACTION -> Unit
        }

        classificationDao.markUndone(batchId, clock.instant())
    }

    /**
     * Runs [block] and records what it changed as one batch. Returns the batch's id, or null
     * when nothing changed.
     */
    private suspend fun audited(action: AuditAction, subject: String, detail: String = "", block: suspend () -> Unit): Long? {
        val before = state()
        block()
        val after = state()

        val changes = diff(AuditEntity.CATEGORY, before.categories, after.categories) { json.encodeToString(it) } +
                diff(AuditEntity.RULE, before.rules, after.rules) { json.encodeToString(snapshot(it)) } +
                diff(AuditEntity.TRANSACTION, before.filings, after.filings) { json.encodeToString(it) }
        if (changes.isEmpty()) return null

        val batchId = classificationDao.insertBatch(
            AuditBatch(action = action, subject = subject, detail = detail, at = clock.instant())
        )
        classificationDao.insertChanges(changes.map { it.copy(batchId = batchId) })
        return batchId
    }

    private data class State(
        val categories: Map<Long, Category>,
        val rules: Map<Long, Rule>,
        val filings: Map<Long, Filing>
    )

    // ponytail: reads every transaction's filing before and after each thing you do; log from
    // the writes themselves if a large history makes a tap feel slow.
    private suspend fun state() = State(
        categories = classificationDao.getCategories().associateBy { it.id },
        rules = classificationDao.getRules().associateBy { it.id },
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
    }
}
