package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.ClassificationDao
import bassamalim.halala.core.data.dataSources.room.daos.TransactionsDao
import bassamalim.halala.core.data.dataSources.room.entities.Category
import bassamalim.halala.core.data.dataSources.room.entities.Rule
import bassamalim.halala.core.data.dataSources.room.entities.RuleActions
import bassamalim.halala.core.data.dataSources.room.entities.RuleConditions
import bassamalim.halala.core.data.dataSources.room.relations.CategoryWithUse
import bassamalim.halala.core.data.dataSources.room.relations.RuleWithStats
import bassamalim.halala.core.domain.Rules
import bassamalim.halala.core.enums.ExpenseType
import bassamalim.halala.core.enums.RuleSource
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Categories, rules, and filing transactions under them. A transaction you filed yourself has a
 * category and no rule, and no rule ever changes it; everything else is the rules' to file.
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
        classificationDao.getRules()
            .filter { it.actions.categoryId == id }
            .forEach { classificationDao.deleteRule(it.id) }
        transactionsDao.clearTypeOf(id)
        classificationDao.deleteCategory(id)
    }

    fun observeRules(): Flow<List<RuleWithStats>> = classificationDao.observeRules()

    suspend fun getRules(): List<Rule> = classificationDao.getRules()

    /** Your own answer for one transaction. It sticks: rules leave it alone from now on. */
    suspend fun file(transactionId: Long, categoryId: Long?, expenseType: ExpenseType?) =
        transactionsDao.setCategory(listOf(transactionId), categoryId, expenseType, ruleId = null)

    /** A category's own type: what choosing it fills in. */
    suspend fun defaultTypeOf(categoryId: Long): ExpenseType? = classificationDao.getCategory(categoryId)?.expenseType

    /**
     * Never teach it twice: [merchant] goes under [categoryId] from now on, and so does all of
     * its past spending that you haven't filed yourself. A merchant has one learned rule; a new
     * answer replaces the old one.
     */
    suspend fun learn(merchant: String, categoryId: Long) {
        val key = Rules.merchantKey(merchant)
        val actions = RuleActions(categoryId, defaultTypeOf(categoryId))
        val existing = classificationDao.getRules().firstOrNull {
            it.source == RuleSource.LEARNED && it.conditions.merchant?.let(Rules::merchantKey) == key
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

    /** Points a rule at another category and re-files what it filed. */
    suspend fun retarget(ruleId: Long, categoryId: Long) {
        val rule = classificationDao.getRule(ruleId) ?: return
        classificationDao.updateRule(rule.copy(actions = RuleActions(categoryId, defaultTypeOf(categoryId))))
        applyRules()
    }

    /** A rule that is off files nothing new; what it filed stays filed. */
    suspend fun setEnabled(ruleId: Long, enabled: Boolean) {
        val rule = classificationDao.getRule(ruleId) ?: return
        classificationDao.updateRule(rule.copy(enabled = enabled))
        applyRules()
    }

    /** What the rule filed keeps its category, as if you had chosen it. */
    suspend fun delete(ruleId: Long) = classificationDao.deleteRule(ruleId)

    /**
     * Runs every enabled rule over everything a rule may file, and writes only what changes, so
     * it can run after every SMS, every save and every rule edit.
     */
    // ponytail: re-reads every candidate each run; narrow it to new rows if history gets slow.
    suspend fun applyRules() {
        val rules = Rules.index(classificationDao.getRules())
        if (rules.isEmpty()) return

        transactionsDao.getRuleCandidates()
            .filter { it.kind.countsInTotals }
            .groupBy { rules[Rules.merchantKey(it.title)] }
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
}
