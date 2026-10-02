package bassamalim.halala.core.data.dataSources.room

import androidx.room.TypeConverter
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.AliasMatch
import bassamalim.halala.core.data.dataSources.room.entities.RuleActions
import bassamalim.halala.core.data.dataSources.room.entities.RuleConditions
import bassamalim.halala.core.enums.AuditAction
import bassamalim.halala.core.enums.AuditEntity
import bassamalim.halala.core.enums.BudgetScope
import bassamalim.halala.core.enums.BusinessType
import bassamalim.halala.core.enums.CadenceUnit
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.ExpenseType
import bassamalim.halala.core.enums.IdentifiedBy
import bassamalim.halala.core.enums.LoanDirection
import bassamalim.halala.core.enums.LoanEventType
import bassamalim.halala.core.enums.RuleSource
import bassamalim.halala.core.enums.RawStatus
import bassamalim.halala.core.enums.RecurringKind
import bassamalim.halala.core.enums.SeriesStatus
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

private val lenientJson = Json { ignoreUnknownKeys = true }

/**
 * Enums are stored by name. A name that no longer exists decays to a safe value rather than
 * throwing, so dropping an entry from a list can never crash anyone who stored one. (Direction,
 * a loan's direction and what happened to a loan are values that will never change, so they are
 * read strictly: no safe value could stand in for money.)
 */
class Converters {

    @TypeConverter
    fun toEpochMilli(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun fromEpochMilli(millis: Long?): Instant? = millis?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun toAccountType(name: String): AccountType =
        AccountType.entries.firstOrNull { it.name == name } ?: AccountType.CURRENT

    @TypeConverter
    fun fromAccountType(type: AccountType): String = type.name

    @TypeConverter
    fun toDirection(name: String): Direction = Direction.valueOf(name)

    @TypeConverter
    fun fromDirection(direction: Direction): String = direction.name

    @TypeConverter
    fun toTransactionKind(name: String): TransactionKind =
        TransactionKind.entries.firstOrNull { it.name == name } ?: TransactionKind.OTHER

    @TypeConverter
    fun fromTransactionKind(kind: TransactionKind): String = kind.name

    @TypeConverter
    fun toTransactionSource(name: String): TransactionSource =
        TransactionSource.entries.firstOrNull { it.name == name } ?: TransactionSource.MANUAL

    @TypeConverter
    fun fromTransactionSource(source: TransactionSource): String = source.name

    @TypeConverter
    fun toExpenseType(name: String?): ExpenseType? = ExpenseType.entries.firstOrNull { it.name == name }

    @TypeConverter
    fun fromExpenseType(type: ExpenseType?): String? = type?.name

    @TypeConverter
    fun toRuleSource(name: String): RuleSource =
        RuleSource.entries.firstOrNull { it.name == name } ?: RuleSource.LEARNED

    @TypeConverter
    fun fromRuleSource(source: RuleSource): String = source.name

    // A condition or action a newer version wrote is skipped rather than failing the read.
    @TypeConverter
    fun toRuleConditions(json: String): RuleConditions = lenientJson.decodeFromString(json)

    @TypeConverter
    fun fromRuleConditions(conditions: RuleConditions): String = lenientJson.encodeToString(conditions)

    @TypeConverter
    fun toRuleActions(json: String): RuleActions = lenientJson.decodeFromString(json)

    @TypeConverter
    fun fromRuleActions(actions: RuleActions): String = lenientJson.encodeToString(actions)

    @TypeConverter
    fun toAuditAction(name: String): AuditAction =
        AuditAction.entries.firstOrNull { it.name == name } ?: AuditAction.FILED

    @TypeConverter
    fun fromAuditAction(action: AuditAction): String = action.name

    @TypeConverter
    fun toAuditEntity(name: String): AuditEntity =
        AuditEntity.entries.firstOrNull { it.name == name } ?: AuditEntity.TRANSACTION

    @TypeConverter
    fun fromAuditEntity(entity: AuditEntity): String = entity.name

    @TypeConverter
    fun toRawStatus(name: String): RawStatus =
        RawStatus.entries.firstOrNull { it.name == name } ?: RawStatus.PENDING

    @TypeConverter
    fun fromRawStatus(status: RawStatus): String = status.name

    @TypeConverter
    fun toAliasMatch(name: String): AliasMatch =
        AliasMatch.entries.firstOrNull { it.name == name } ?: AliasMatch.SIMILAR

    @TypeConverter
    fun fromAliasMatch(match: AliasMatch): String = match.name

    @TypeConverter
    fun toBusinessType(name: String?): BusinessType? =
        name?.let { BusinessType.entries.firstOrNull { type -> type.name == it } ?: BusinessType.UNKNOWN }

    @TypeConverter
    fun fromBusinessType(type: BusinessType?): String? = type?.name

    /** A category's business types, comma-separated; names no longer known are dropped. */
    @TypeConverter
    fun toBusinessTypes(names: String): List<BusinessType> =
        names.split(',').mapNotNull { name -> BusinessType.entries.firstOrNull { it.name == name } }

    @TypeConverter
    fun fromBusinessTypes(types: List<BusinessType>): String = types.joinToString(",") { it.name }

    @TypeConverter
    fun toIdentifiedBy(name: String?): IdentifiedBy? = IdentifiedBy.entries.firstOrNull { it.name == name }

    @TypeConverter
    fun fromIdentifiedBy(by: IdentifiedBy?): String? = by?.name

    /** A day, as days since 1970-01-01. */
    @TypeConverter
    fun toEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun fromEpochDay(day: Long?): LocalDate? = day?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun toLoanDirection(name: String): LoanDirection = LoanDirection.valueOf(name)

    @TypeConverter
    fun fromLoanDirection(direction: LoanDirection): String = direction.name

    @TypeConverter
    fun toLoanEventType(name: String): LoanEventType = LoanEventType.valueOf(name)

    @TypeConverter
    fun fromLoanEventType(type: LoanEventType): String = type.name

    @TypeConverter
    fun toRecurringKind(name: String): RecurringKind =
        RecurringKind.entries.firstOrNull { it.name == name } ?: RecurringKind.BILL

    @TypeConverter
    fun fromRecurringKind(kind: RecurringKind): String = kind.name

    @TypeConverter
    fun toCadenceUnit(name: String): CadenceUnit = CadenceUnit.valueOf(name)

    @TypeConverter
    fun fromCadenceUnit(unit: CadenceUnit): String = unit.name

    @TypeConverter
    fun toSeriesStatus(name: String): SeriesStatus =
        SeriesStatus.entries.firstOrNull { it.name == name } ?: SeriesStatus.PROPOSED

    @TypeConverter
    fun fromSeriesStatus(status: SeriesStatus): String = status.name

    @TypeConverter
    fun toBudgetScope(name: String): BudgetScope = BudgetScope.entries.firstOrNull { it.name == name } ?: BudgetScope.TOTAL

    @TypeConverter
    fun fromBudgetScope(scope: BudgetScope): String = scope.name

    /** A list of ids, as "3,7,12". */
    @TypeConverter
    fun toIds(text: String): List<Long> = text.split(',').mapNotNull { it.trim().toLongOrNull() }

    @TypeConverter
    fun fromIds(ids: List<Long>): String = ids.joinToString(",")
}
