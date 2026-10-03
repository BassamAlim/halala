package bassamalim.halala.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import bassamalim.halala.R
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.CadenceUnit
import bassamalim.halala.core.enums.RecurringKind
import bassamalim.halala.core.enums.AliasMatch
import bassamalim.halala.core.enums.AuditAction
import bassamalim.halala.core.enums.BusinessType
import bassamalim.halala.core.enums.ExpenseType
import bassamalim.halala.core.enums.IdentifiedBy
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.models.RuleWords
import bassamalim.halala.core.models.TransactionItem
import bassamalim.halala.core.utils.DayLabel
import java.time.DayOfWeek
import java.time.format.TextStyle

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
fun recurringKindLabel(kind: RecurringKind): String = stringResource(
    when (kind) {
        RecurringKind.SUBSCRIPTION -> R.string.recurring_subscription
        RecurringKind.BILL -> R.string.recurring_bill
        RecurringKind.PLANNED -> R.string.recurring_planned
    }
)

/** "Monthly", "Weekly", "Every 2 months". */
@Composable
fun cadenceLabel(every: Int, unit: CadenceUnit): String = if (every == 1) stringResource(
    when (unit) {
        CadenceUnit.DAY -> R.string.cadence_daily
        CadenceUnit.WEEK -> R.string.cadence_weekly
        CadenceUnit.MONTH -> R.string.cadence_monthly
        CadenceUnit.YEAR -> R.string.cadence_yearly
    }
) else pluralStringResource(
    when (unit) {
        CadenceUnit.DAY -> R.plurals.cadence_days
        CadenceUnit.WEEK -> R.plurals.cadence_weeks
        CadenceUnit.MONTH -> R.plurals.cadence_months
        CadenceUnit.YEAR -> R.plurals.cadence_years
    },
    every, every
)

/** "a month", "a year", "every 2 months": how often a price is paid, after an amount. */
@Composable
fun perLabel(every: Int, unit: CadenceUnit): String = if (every == 1) stringResource(
    when (unit) {
        CadenceUnit.DAY -> R.string.per_day
        CadenceUnit.WEEK -> R.string.per_week
        CadenceUnit.MONTH -> R.string.per_month
        CadenceUnit.YEAR -> R.string.per_year
    }
) else cadenceLabel(every, unit).lowercase()

@Composable
fun accountTypeLabel(type: AccountType): String = stringResource(
    when (type) {
        AccountType.CURRENT -> R.string.account_type_current
        AccountType.SAVINGS, AccountType.DEPOSIT -> R.string.account_type_savings
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

/** "Saturday", or "Sat": the phone's own words for the day, so nothing here needs translating. */
@Composable
fun dayOfWeekLabel(day: DayOfWeek, short: Boolean = false): String =
    day.getDisplayName(if (short) TextStyle.SHORT else TextStyle.FULL, LocalConfiguration.current.locales[0])

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
fun expenseTypeLabel(type: ExpenseType): String = stringResource(expenseTypeRes(type))

/** The expense type's words, for places outside Compose (notifications). */
fun expenseTypeRes(type: ExpenseType): Int = when (type) {
    ExpenseType.FIXED_ESSENTIAL -> R.string.expense_fixed_essential
    ExpenseType.FIXED_DISCRETIONARY -> R.string.expense_fixed_discretionary
    ExpenseType.VARIABLE_ESSENTIAL -> R.string.expense_variable_essential
    ExpenseType.VARIABLE_DISCRETIONARY -> R.string.expense_variable_discretionary
}

/**
 * A rule in plain words: "Merchant is Jahez · Amount up to 150.00 → Delivery · Variable ·
 * Discretionary".
 */
@Composable
fun ruleSentence(words: RuleWords, category: String, type: ExpenseType?): String {
    val conditions = listOfNotNull(
        words.merchant?.let { stringResource(R.string.rule_when_merchant, it) },
        words.contains?.let { stringResource(R.string.rule_when_contains, it) },
        words.account?.let { stringResource(R.string.rule_when_account, it) },
        when {
            words.min != null && words.max != null -> stringResource(R.string.rule_when_between, words.min, words.max)
            words.min != null -> stringResource(R.string.rule_when_from, words.min)
            words.max != null -> stringResource(R.string.rule_when_up_to, words.max)
            else -> null
        }
    ).joinToString(stringResource(R.string.separator))

    return stringResource(
        R.string.rule_sentence,
        conditions,
        if (type == null) category else stringResource(R.string.meta_pair, category, expenseTypeLabel(type))
    )
}

/** A recorded change in plain words: "Always file Panda under Groceries". */
@Composable
fun auditSentence(action: AuditAction, subject: String, detail: String): String {
    val name = subject.ifBlank { stringResource(R.string.audit_unnamed) }
    return when (action) {
        AuditAction.FILED -> stringResource(R.string.audit_filed, name, detail)
        AuditAction.TYPE_CHANGED -> stringResource(R.string.audit_type_changed, name)
        AuditAction.LEARNED -> stringResource(R.string.audit_learned, name, detail)
        AuditAction.RULE_SAVED -> stringResource(R.string.audit_rule_saved, name, detail)
        AuditAction.RULE_OFF -> stringResource(R.string.audit_rule_off, name)
        AuditAction.RULE_ON -> stringResource(R.string.audit_rule_on, name)
        AuditAction.RULE_DELETED -> stringResource(R.string.audit_rule_deleted, name)
        AuditAction.CATEGORY_DELETED -> stringResource(R.string.audit_category_deleted, name)
        AuditAction.CATEGORY_EDITED ->
            if (detail.isBlank() || detail == subject) stringResource(R.string.audit_category_edited, name)
            else stringResource(R.string.audit_category_renamed, name, detail)
        AuditAction.MERCHANT_RENAMED -> stringResource(R.string.audit_merchant_renamed, name, detail)
        AuditAction.MERCHANTS_MERGED -> stringResource(R.string.audit_merchants_merged, name, detail)
        AuditAction.ALIAS_SPLIT -> stringResource(R.string.audit_alias_split, name, detail)
        // The batch keeps the type by its name, which is the same in every language.
        AuditAction.MERCHANT_TYPED -> stringResource(
            R.string.audit_merchant_typed,
            name,
            businessTypeLabel(BusinessType.entries.firstOrNull { it.name == detail } ?: BusinessType.UNKNOWN)
        )
    }
}

/** How a spelling came to be the merchant's, for the merchant screen to say. */
@Composable
fun aliasMatchLabel(match: AliasMatch): String = stringResource(
    when (match) {
        AliasMatch.FIRST -> R.string.merchant_spelling_first
        AliasMatch.SIMILAR -> R.string.merchant_spelling_similar
        AliasMatch.YOU -> R.string.merchant_spelling_you
    }
)

/** What a business is, in words: "Supermarket". */
@Composable
fun businessTypeLabel(type: BusinessType): String = stringResource(
    when (type) {
        BusinessType.SUPERMARKET -> R.string.business_supermarket
        BusinessType.CONVENIENCE_STORE -> R.string.business_convenience_store
        BusinessType.BAKERY -> R.string.business_bakery
        BusinessType.RESTAURANT -> R.string.business_restaurant
        BusinessType.FAST_FOOD -> R.string.business_fast_food
        BusinessType.CAFE -> R.string.business_cafe
        BusinessType.FOOD_DELIVERY -> R.string.business_food_delivery
        BusinessType.FUEL_STATION -> R.string.business_fuel_station
        BusinessType.CAR_SERVICE -> R.string.business_car_service
        BusinessType.PARKING -> R.string.business_parking
        BusinessType.RIDE_HAILING -> R.string.business_ride_hailing
        BusinessType.PUBLIC_TRANSPORT -> R.string.business_public_transport
        BusinessType.CAR_RENTAL -> R.string.business_car_rental
        BusinessType.AIRLINE -> R.string.business_airline
        BusinessType.HOTEL -> R.string.business_hotel
        BusinessType.TRAVEL_AGENCY -> R.string.business_travel_agency
        BusinessType.PHARMACY -> R.string.business_pharmacy
        BusinessType.CLINIC -> R.string.business_clinic
        BusinessType.OPTICIAN -> R.string.business_optician
        BusinessType.GYM -> R.string.business_gym
        BusinessType.TELECOM -> R.string.business_telecom
        BusinessType.UTILITY -> R.string.business_utility
        BusinessType.GOVERNMENT -> R.string.business_government
        BusinessType.INSURANCE -> R.string.business_insurance
        BusinessType.EDUCATION -> R.string.business_education
        BusinessType.BOOKSTORE -> R.string.business_bookstore
        BusinessType.ELECTRONICS -> R.string.business_electronics
        BusinessType.CLOTHING -> R.string.business_clothing
        BusinessType.BEAUTY -> R.string.business_beauty
        BusinessType.SALON -> R.string.business_salon
        BusinessType.JEWELRY -> R.string.business_jewelry
        BusinessType.GIFTS -> R.string.business_gifts
        BusinessType.SPORTS_GOODS -> R.string.business_sports_goods
        BusinessType.TOYS -> R.string.business_toys
        BusinessType.HOME_FURNISHING -> R.string.business_home_furnishing
        BusinessType.HARDWARE -> R.string.business_hardware
        BusinessType.LAUNDRY -> R.string.business_laundry
        BusinessType.REAL_ESTATE -> R.string.business_real_estate
        BusinessType.DEPARTMENT_STORE -> R.string.business_department_store
        BusinessType.ONLINE_MARKETPLACE -> R.string.business_online_marketplace
        BusinessType.STREAMING -> R.string.business_streaming
        BusinessType.SOFTWARE -> R.string.business_software
        BusinessType.GAMING -> R.string.business_gaming
        BusinessType.ENTERTAINMENT -> R.string.business_entertainment
        BusinessType.CHARITY -> R.string.business_charity
        BusinessType.MONEY_TRANSFER -> R.string.business_money_transfer
        BusinessType.UNKNOWN -> R.string.business_unknown
    }
)

/** Who said what a merchant is, and how sure: "Identified by AI, 92% sure". */
@Composable
fun identifiedLabel(by: IdentifiedBy, confidence: Int?): String = when (by) {
    IdentifiedBy.LIST -> stringResource(R.string.identified_list)
    IdentifiedBy.AI -> stringResource(R.string.identified_ai, confidence ?: 0)
    IdentifiedBy.YOU -> stringResource(R.string.identified_you)
    IdentifiedBy.WITHHELD -> stringResource(R.string.identified_withheld)
}
