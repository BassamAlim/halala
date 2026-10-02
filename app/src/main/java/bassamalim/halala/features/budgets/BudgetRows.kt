package bassamalim.halala.features.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import bassamalim.halala.R
import bassamalim.halala.core.data.dataSources.room.entities.Category
import bassamalim.halala.core.data.dataSources.room.entities.Merchant
import bassamalim.halala.core.domain.BudgetState
import bassamalim.halala.core.domain.BudgetStatus
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.enums.BudgetScope
import bassamalim.halala.core.enums.ExpenseType
import bassamalim.halala.core.ui.components.ProgressBar
import bassamalim.halala.core.ui.expenseTypeLabel
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/** What a budget is called: everything, an expense type (a word), or a category's or merchant's name. */
sealed interface BudgetLabel {
    data object Everything : BudgetLabel
    data class Type(val type: ExpenseType) : BudgetLabel
    data class Named(val name: String) : BudgetLabel
}

/**
 * One budget as the Plan board lists it: "1,420 / 2,000 · 580 left", or "over by 62". Summary
 * figures, no decimals.
 */
data class BudgetRow(
    val id: Long,
    val label: BudgetLabel,
    val spent: String,
    val limit: String,
    val left: String,
    val over: Boolean,
    val state: BudgetState,
    val progress: Float
)

object BudgetRows {

    fun of(statuses: List<BudgetStatus>, categories: List<Category>, merchants: List<Merchant>): List<BudgetRow> {
        val categoryNames = categories.associate { it.id to it.name }
        val merchantNames = merchants.associate { it.id to it.name }
        return statuses
            .sortedWith(compareBy({ it.budget.scope != BudgetScope.TOTAL }, { it.budget.id }))
            .map { status ->
                val budget = status.budget
                BudgetRow(
                    id = budget.id,
                    label = when (budget.scope) {
                        BudgetScope.TOTAL -> BudgetLabel.Everything
                        BudgetScope.EXPENSE_TYPE -> budget.expenseType?.let { BudgetLabel.Type(it) } ?: BudgetLabel.Everything
                        BudgetScope.CATEGORY -> BudgetLabel.Named(categoryNames[budget.categoryId].orEmpty())
                        BudgetScope.MERCHANT -> BudgetLabel.Named(merchantNames[budget.merchantId].orEmpty())
                    },
                    spent = Money.format(status.spentMinor, budget.currency, decimals = false),
                    limit = Money.format(status.limitMinor, budget.currency, decimals = false),
                    left = Money.format(kotlin.math.abs(status.leftMinor), budget.currency, decimals = false),
                    over = status.leftMinor < 0,
                    state = if (status.state == BudgetState.OK && status.paceAhead) BudgetState.WARN else status.state,
                    progress = BudgetState.progress(status.spentMinor, status.limitMinor)
                )
            }
    }
}

@Composable
fun budgetLabel(label: BudgetLabel): String = when (label) {
    BudgetLabel.Everything -> stringResource(R.string.budget_everything)
    is BudgetLabel.Type -> expenseTypeLabel(label.type)
    is BudgetLabel.Named -> label.name
}

/** The Plan board's budget rows: name, "spent / limit · left", and the bar in the state's colour. */
@Composable
fun BudgetRowsList(rows: List<BudgetRow>, onClick: ((Long) -> Unit)? = null) {
    Column {
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(thickness = Sizes.border, color = HalalaColors.Line)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (onClick != null) Modifier.clickable(role = Role.Button) { onClick(row.id) } else Modifier)
                    .padding(vertical = Insets.row),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text(text = budgetLabel(row.label), style = HalalaType.Body)
                    val detailColor = if (row.over) HalalaColors.StateOver else HalalaColors.TextMuted
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontFamily = HalalaNumbers.Amount.fontFamily, color = HalalaColors.Text)) { append(row.spent) }
                            append(" / ${row.limit} · ")
                            append(
                                if (row.over) stringResource(R.string.budget_over_by, row.left)
                                else stringResource(R.string.budget_left, row.left)
                            )
                        },
                        style = HalalaType.Caption,
                        color = detailColor
                    )
                }
                ProgressBar(progress = row.progress, state = row.state)
            }
        }
    }
}
