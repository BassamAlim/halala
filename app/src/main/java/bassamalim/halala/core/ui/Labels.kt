package bassamalim.halala.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import bassamalim.halala.R
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.ExpenseType
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.models.TransactionItem
import bassamalim.halala.core.utils.DayLabel

/**
 * Words the UI layer owns. ViewModels hand over enums and [DayLabel]s; these turn them into
 * string resources, so every word on screen can be translated later.
 */

@Composable
fun kindLabel(kind: TransactionKind): String = stringResource(
    when (kind) {
        TransactionKind.PURCHASE -> R.string.kind_purchase
        TransactionKind.REFUND -> R.string.kind_refund
        TransactionKind.TRANSFER_OUT -> R.string.kind_transfer_out
        TransactionKind.TRANSFER_IN -> R.string.kind_transfer_in
        TransactionKind.INTERNAL_TRANSFER -> R.string.kind_internal_transfer
        TransactionKind.SALARY -> R.string.kind_salary
        TransactionKind.ATM_WITHDRAWAL -> R.string.kind_atm_withdrawal
        TransactionKind.CASH_DEPOSIT -> R.string.kind_cash_deposit
        TransactionKind.FEE -> R.string.kind_fee
        TransactionKind.BILL_PAYMENT -> R.string.kind_bill_payment
        TransactionKind.INVESTMENT_BUY -> R.string.kind_investment_buy
        TransactionKind.INVESTMENT_SELL -> R.string.kind_investment_sell
        TransactionKind.SAVINGS_DEPOSIT -> R.string.kind_savings_deposit
        TransactionKind.SAVINGS_WITHDRAWAL -> R.string.kind_savings_withdrawal
        TransactionKind.LOAN_GIVEN -> R.string.kind_loan_given
        TransactionKind.LOAN_RECEIVED -> R.string.kind_loan_received
        TransactionKind.LOAN_REPAYMENT -> R.string.kind_loan_repayment
        TransactionKind.ADJUSTMENT -> R.string.kind_adjustment
        TransactionKind.OTHER -> R.string.kind_other
    }
)

@Composable
fun accountTypeLabel(type: AccountType): String = stringResource(
    when (type) {
        AccountType.CURRENT -> R.string.account_type_current
        AccountType.SAVINGS -> R.string.account_type_savings
        AccountType.CARD -> R.string.account_type_card
        AccountType.WALLET -> R.string.account_type_wallet
        AccountType.INVESTMENT -> R.string.account_type_investment
        AccountType.CASH -> R.string.account_type_cash
    }
)

@Composable
fun dayText(day: DayLabel): String = when (day) {
    DayLabel.Today -> stringResource(R.string.today)
    DayLabel.Yesterday -> stringResource(R.string.yesterday)
    is DayLabel.On -> day.text
}

/** A row's title: what you wrote, else the kind's name. */
@Composable
fun itemTitle(item: TransactionItem): String = item.title.ifBlank { kindLabel(item.kind) }

/**
 * A row's second line: "Between your accounts · not spending" for a move, else the kind and
 * either the account ("Purchase · Al Rajhi – Salary") or the day ("Purchase · Today"); once it
 * has a category, that reads in the kind's place ("Groceries · Today").
 */
@Composable
fun itemMeta(item: TransactionItem, withDay: Boolean): String = when {
    item.isMove -> stringResource(R.string.between_your_accounts)
    withDay -> stringResource(R.string.meta_pair, item.category ?: kindLabel(item.kind), dayText(item.day))
    else -> stringResource(R.string.meta_pair, item.category ?: kindLabel(item.kind), item.accountLabel)
}

@Composable
fun expenseTypeLabel(type: ExpenseType): String = stringResource(
    when (type) {
        ExpenseType.FIXED_ESSENTIAL -> R.string.expense_fixed_essential
        ExpenseType.FIXED_DISCRETIONARY -> R.string.expense_fixed_discretionary
        ExpenseType.VARIABLE_ESSENTIAL -> R.string.expense_variable_essential
        ExpenseType.VARIABLE_DISCRETIONARY -> R.string.expense_variable_discretionary
    }
)

/** A rule in plain words: "Merchant is Jahez → Delivery · Variable · Discretionary". */
@Composable
fun ruleSentence(merchant: String, category: String, type: ExpenseType?): String = stringResource(
    R.string.rule_sentence,
    merchant,
    if (type == null) category else stringResource(R.string.meta_pair, category, expenseTypeLabel(type))
)
