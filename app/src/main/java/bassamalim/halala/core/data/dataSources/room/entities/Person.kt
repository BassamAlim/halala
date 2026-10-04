package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Someone you send money to or get it from (the spec's counterparty): "AHMED ALI ALQAHTANI" and
 * "Ahmed Ali" are one person with two aliases. [name] starts as the bank first wrote it, tidied,
 * until you name them yourself ([namedByYou]). [salarySince]: they pay your salary, so their
 * transfers in from then on are your salary (`PeopleDao.markSalaries`); null when they don't.
 */
@Entity(tableName = "people", indices = [Index(value = ["uid"], unique = true)])
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val name: String,
    @ColumnInfo(defaultValue = "0")
    val namedByYou: Boolean = false,
    val salarySince: Instant? = null
)

/**
 * One way a bank writes a person: an [aliasKey] (`Merchants.key` of a transfer's title) belongs
 * to exactly one person. Transfers find their person through it, by their own `merchantKey`, so
 * merging or splitting people moves aliases and never rewrites a transaction. [descriptor] is
 * the name as a bank first wrote it, to show.
 */
@Entity(
    tableName = "person_aliases",
    foreignKeys = [
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["aliasKey"], unique = true), Index(value = ["personId"])]
)
data class PersonAlias(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val aliasKey: String,
    val descriptor: String
)
