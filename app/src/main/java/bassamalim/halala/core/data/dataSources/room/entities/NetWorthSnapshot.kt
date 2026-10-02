package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * What assets outside your accounts were worth on a [date] (funds, gold and the rest move with
 * prices the ledger doesn't hold), kept one a day so the net worth timeline can show them. The
 * accounts' part of net worth is read back from the ledger, so it needs no snapshot.
 */
@Entity(tableName = "net_worth_snapshots")
data class NetWorthSnapshot(
    @PrimaryKey val date: LocalDate,
    val assetsMinor: Long,
    val currency: String
)
