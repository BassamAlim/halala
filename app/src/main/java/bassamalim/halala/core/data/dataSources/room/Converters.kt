package bassamalim.halala.core.data.dataSources.room

import androidx.room.TypeConverter
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.AliasMatch
import bassamalim.halala.core.data.dataSources.room.entities.RuleActions
import bassamalim.halala.core.data.dataSources.room.entities.RuleConditions
import bassamalim.halala.core.enums.AuditAction
import bassamalim.halala.core.enums.AuditEntity
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.ExpenseType
import bassamalim.halala.core.enums.RuleSource
import bassamalim.halala.core.enums.RawStatus
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import kotlinx.serialization.json.Json
import java.time.Instant

private val lenientJson = Json { ignoreUnknownKeys = true }

/**
 * Enums are stored by name. A name that no longer exists decays to a safe value rather than
 * throwing, so dropping an entry from a list can never crash anyone who stored one. (Direction
 * is two values that will never change, so it is read strictly.)
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
}
