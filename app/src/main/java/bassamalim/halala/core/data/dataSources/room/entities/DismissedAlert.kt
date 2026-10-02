package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/** An anomaly alert you dismissed (or a merchant whose large charges are normal), by its key. */
@Entity(tableName = "dismissed_alerts")
data class DismissedAlert(
    @PrimaryKey val key: String,
    val at: Instant
)
