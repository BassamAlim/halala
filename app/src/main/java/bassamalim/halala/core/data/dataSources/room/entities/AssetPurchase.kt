package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A transaction you said bought (or added to) an asset: money turned into something you own, so
 * net worth doesn't dip with it. Its asset's cost is what its purchases add up to. A transaction
 * buys one asset at most; an asset may take several.
 */
@Entity(
    tableName = "asset_purchases",
    foreignKeys = [
        ForeignKey(entity = Transaction::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Asset::class, parentColumns = ["id"], childColumns = ["assetId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["assetId"])]
)
data class AssetPurchase(
    @PrimaryKey val transactionId: Long,
    val assetId: Long
)
