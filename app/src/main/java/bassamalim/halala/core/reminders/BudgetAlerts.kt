package bassamalim.halala.core.reminders

import android.content.Context
import bassamalim.halala.R
import bassamalim.halala.core.Globals
import bassamalim.halala.core.data.repositories.BudgetsRepository
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.PreferencesRepository
import bassamalim.halala.core.domain.Budgets
import bassamalim.halala.core.enums.BudgetScope
import bassamalim.halala.core.ui.expenseTypeRes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * The spec's budget alerts at 50, 80 and 100%: a budget that has gone past one since the last
 * look gets one notice, naming it and the percent, never an amount. Each is told once a cycle.
 * Looked at after every SMS run and by the daily reminder work (for what you add by hand).
 */
class BudgetAlerts @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val budgets: BudgetsRepository,
    private val classification: ClassificationRepository,
    private val preferences: PreferencesRepository
) {

    /** What to tell about now; remembered as told, so the next look doesn't repeat it. */
    suspend fun notices(): List<DueNotice> {
        val overview = budgets.observeOverview(Globals.PRIMARY_CURRENCY).first()
        val cycle = overview.cycle
        val told = preferences.budgetAlertsTold()
        val due = Budgets.alerts(overview.statuses, cycle, told)
        // Keeps only this cycle's keys, so the set never grows past a few per budget.
        val thisCycle = told.filter { it.split(":").getOrNull(1) == cycle.start.toString() }.toSet()
        val now = due.map { (status, percent) -> Budgets.toldKey(status.budget.id, cycle.start, percent) }
        if (now.isNotEmpty() || thisCycle != told) preferences.setBudgetAlertsTold(thisCycle + now)
        if (due.isEmpty()) return emptyList()

        val categories = classification.getCategories().associate { it.id to it.name }
        val merchants = classification.getMerchants().associate { it.id to it.name }
        return due.map { (status, percent) ->
            val budget = status.budget
            val name = when (budget.scope) {
                BudgetScope.TOTAL -> context.getString(R.string.budget_everything)
                BudgetScope.EXPENSE_TYPE -> budget.expenseType?.let { context.getString(expenseTypeRes(it)) }
                    ?: context.getString(R.string.budget_everything)
                BudgetScope.CATEGORY -> categories[budget.categoryId].orEmpty()
                BudgetScope.MERCHANT -> merchants[budget.merchantId].orEmpty()
                BudgetScope.TAG -> overview.tagNames[budget.tagId].orEmpty()
            }
            DueNotice.Budget(BUDGET_KEYS + budget.id.toInt(), name, percent)
        }
    }

    private companion object {
        // Notification ids, apart from the other reminders' (see DueReminders).
        const val BUDGET_KEYS = 900_000
    }
}
