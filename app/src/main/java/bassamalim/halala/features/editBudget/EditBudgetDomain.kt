package bassamalim.halala.features.editBudget

import bassamalim.halala.core.Globals
import bassamalim.halala.core.data.dataSources.room.entities.Budget
import bassamalim.halala.core.data.dataSources.room.entities.Category
import bassamalim.halala.core.data.dataSources.room.relations.MerchantWithStats
import bassamalim.halala.core.data.repositories.BudgetsRepository
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.enums.BudgetScope
import bassamalim.halala.core.enums.ExpenseType
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject

data class BudgetForm(
    val scope: BudgetScope = BudgetScope.TOTAL,
    val categoryId: Long? = null,
    val expenseType: ExpenseType? = null,
    val merchantId: Long? = null,
    val amount: String = "",
    val rollover: Boolean = false,
    val currency: String = Globals.PRIMARY_CURRENCY,
    val uid: String = "",
    val createdAt: Instant = Instant.EPOCH
)

enum class BudgetProblem { AmountInvalid, ChoiceMissing }

class EditBudgetDomain @Inject constructor(
    private val budgetsRepository: BudgetsRepository,
    private val classificationRepository: ClassificationRepository
) {

    fun observeCategories(): Flow<List<Category>> = classificationRepository.observeCategories()

    fun observeMerchants(): Flow<List<MerchantWithStats>> = classificationRepository.observeMerchants()

    suspend fun load(id: Long): BudgetForm? = budgetsRepository.get(id)?.let {
        BudgetForm(
            scope = it.scope,
            categoryId = it.categoryId,
            expenseType = it.expenseType,
            merchantId = it.merchantId,
            amount = Money.plain(it.amountMinor, it.currency),
            rollover = it.rollover,
            currency = it.currency,
            uid = it.uid,
            createdAt = it.createdAt
        )
    }

    suspend fun save(id: Long, form: BudgetForm): Set<BudgetProblem> {
        val (budget, problems) = validate(id, form)
        if (budget != null) budgetsRepository.save(budget)
        return problems
    }

    suspend fun delete(id: Long) = budgetsRepository.delete(id)

    companion object {

        /** The budget [form] describes, or the problems that stop it. */
        fun validate(id: Long, form: BudgetForm): Pair<Budget?, Set<BudgetProblem>> {
            val problems = mutableSetOf<BudgetProblem>()
            val amount = Money.parse(form.amount, form.currency)?.takeIf { it > 0 }
            if (amount == null) problems += BudgetProblem.AmountInvalid
            val chosen = when (form.scope) {
                BudgetScope.TOTAL -> true
                BudgetScope.CATEGORY -> form.categoryId != null
                BudgetScope.EXPENSE_TYPE -> form.expenseType != null
                BudgetScope.MERCHANT -> form.merchantId != null
            }
            if (!chosen) problems += BudgetProblem.ChoiceMissing
            if (problems.isNotEmpty()) return null to problems

            return Budget(
                id = id,
                uid = form.uid,
                scope = form.scope,
                // Only what the scope names is kept.
                categoryId = form.categoryId.takeIf { form.scope == BudgetScope.CATEGORY },
                expenseType = form.expenseType.takeIf { form.scope == BudgetScope.EXPENSE_TYPE },
                merchantId = form.merchantId.takeIf { form.scope == BudgetScope.MERCHANT },
                amountMinor = amount!!,
                currency = form.currency,
                rollover = form.rollover,
                createdAt = form.createdAt
            ) to emptySet()
        }
    }
}
