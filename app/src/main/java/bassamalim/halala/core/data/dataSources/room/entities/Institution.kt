package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One bank or broker. The v1 set is seeded when the database is created (see Seed.kt). */
@Entity(tableName = "institutions", indices = [Index(value = ["name"], unique = true)])
data class Institution(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** SMS sender IDs, comma-separated (e.g. `AlRajhiBank`). Filled in with the parsers. */
    val senderIds: String = "",
    val parserVersion: Int = 0
)
