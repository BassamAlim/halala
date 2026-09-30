package bassamalim.halala.core.data.dataSources.room

import androidx.room.TypeConverter
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import java.time.Instant

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
}
