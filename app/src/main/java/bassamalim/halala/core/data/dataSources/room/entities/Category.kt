package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import bassamalim.halala.core.enums.ExpenseType
import kotlinx.serialization.Serializable

/**
 * What spending was for. [name] is yours to change, so it is data, not a string resource.
 * [expenseType] is what choosing this category fills in for a transaction's type.
 */
@Serializable
@Entity(tableName = "categories", indices = [Index(value = ["uid"], unique = true)])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val name: String,
    val expenseType: ExpenseType? = null
)
