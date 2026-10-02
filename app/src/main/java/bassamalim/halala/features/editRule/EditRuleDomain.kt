package bassamalim.halala.features.editRule

import bassamalim.halala.core.Globals
import bassamalim.halala.core.data.dataSources.room.entities.Category
import bassamalim.halala.core.data.dataSources.room.entities.RuleActions
import bassamalim.halala.core.data.dataSources.room.entities.RuleConditions
import bassamalim.halala.core.data.dataSources.room.relations.AccountWithBalance
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.enums.ExpenseType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** What the rule form says before it is checked. */
data class RuleForm(
    val merchant: String = "",
    /**
     * The merchant a learned rule was taught, and the name it was shown by: left as it is, the
     * rule keeps following that merchant; edited, it matches the words written.
     */
    val merchantId: Long? = null,
    val merchantName: String = "",
    val contains: String = "",
    val accountId: Long? = null,
    val min: String = "",
    val max: String = "",
    val categoryId: Long? = null,
    val expenseType: ExpenseType? = null
)

enum class RuleProblem { ConditionMissing, AmountInvalid, RangeInverted, CategoryMissing }

sealed interface CheckedRule {
    data class Valid(val conditions: RuleConditions, val actions: RuleActions) : CheckedRule
    data class Invalid(val problems: Set<RuleProblem>) : CheckedRule
}

class EditRuleDomain @Inject constructor(
    private val classificationRepository: ClassificationRepository,
    private val accountsRepository: AccountsRepository
) {

    fun observeAccounts(): Flow<List<AccountWithBalance>> = accountsRepository.observeAll()

    fun observeCategories(): Flow<List<Category>> = classificationRepository.observeCategories()

    /** The form for an existing rule, its amounts written in its account's currency (SAR when it names none). */
    suspend fun load(id: Long): RuleForm? {
        val rule = classificationRepository.getRule(id) ?: return null
        val conditions = rule.conditions
        val currency = conditions.accountId?.let { accountsRepository.get(it) }?.currency ?: Globals.PRIMARY_CURRENCY
        val merchant = conditions.merchantId?.let { classificationRepository.getMerchant(it) }

        return RuleForm(
            merchant = merchant?.name ?: conditions.merchant.orEmpty(),
            merchantId = merchant?.id,
            merchantName = merchant?.name.orEmpty(),
            contains = conditions.contains.orEmpty(),
            accountId = conditions.accountId,
            min = conditions.minMinor?.let { Money.input(it, currency) }.orEmpty(),
            max = conditions.maxMinor?.let { Money.input(it, currency) }.orEmpty(),
            categoryId = rule.actions.categoryId,
            expenseType = rule.actions.expenseType
        )
    }

    /** Checks and writes. [id] 0 writes a new rule of yours. */
    suspend fun save(id: Long, form: RuleForm, currency: String): Set<RuleProblem> =
        when (val checked = validate(form, currency)) {
            is CheckedRule.Invalid -> checked.problems
            is CheckedRule.Valid -> {
                classificationRepository.saveRule(id, checked.conditions, checked.actions)
                emptySet()
            }
        }

    companion object {

        /**
         * The rules, without storage: at least one condition (a rule that matches everything
         * isn't a rule); amounts that read as money in [currency], the first no larger than the
         * second; and a category to file under.
         */
        fun validate(form: RuleForm, currency: String): CheckedRule {
            val problems = mutableSetOf<RuleProblem>()
            val min = form.min.takeIf { it.isNotBlank() }?.let { Money.parse(it, currency) }
            val max = form.max.takeIf { it.isNotBlank() }?.let { Money.parse(it, currency) }

            if ((form.min.isNotBlank() && min == null) || (form.max.isNotBlank() && max == null))
                problems += RuleProblem.AmountInvalid
            if (min != null && max != null && min > max) problems += RuleProblem.RangeInverted

            val merchant = form.merchant.trim().ifEmpty { null }
            val conditions = RuleConditions(
                merchant = merchant,
                merchantId = form.merchantId?.takeIf { merchant != null && merchant == form.merchantName },
                contains = form.contains.trim().ifEmpty { null },
                accountId = form.accountId,
                minMinor = min,
                maxMinor = max
            )
            if (conditions.size == 0 && RuleProblem.AmountInvalid !in problems) problems += RuleProblem.ConditionMissing
            if (form.categoryId == null) problems += RuleProblem.CategoryMissing

            return if (problems.isNotEmpty() || form.categoryId == null) CheckedRule.Invalid(problems)
            else CheckedRule.Valid(conditions, RuleActions(form.categoryId, form.expenseType))
        }
    }
}
