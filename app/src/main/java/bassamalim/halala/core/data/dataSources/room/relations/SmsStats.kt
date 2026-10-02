package bassamalim.halala.core.data.dataSources.room.relations

import java.time.Instant

/** What the SMS pipeline has read so far. */
data class SmsStats(
    val messages: Int,
    val transactions: Int,
    /** The oldest bank SMS kept; null before any. */
    val since: Instant?
)
