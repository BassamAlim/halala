package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/** A retirement plan you saved to compare: its inputs, rates as exact decimal text. */
@Entity(tableName = "retirement_scenarios", indices = [Index(value = ["uid"], unique = true)])
data class RetirementScenario(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val name: String,
    val ageNow: Int,
    val retireAt: Int,
    val startMinor: Long,
    val monthlyMinor: Long,
    val returnPercent: String,
    val inflationPercent: String,
    val wantedMinor: Long,
    val currency: String,
    val createdAt: Instant
)
