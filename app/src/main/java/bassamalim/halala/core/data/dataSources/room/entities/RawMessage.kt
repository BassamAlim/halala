package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import bassamalim.halala.core.enums.RawStatus
import java.time.Instant

/**
 * A bank SMS exactly as it arrived, kept forever: it is the source of truth, so any parse can be
 * re-run when the parsers improve. [hash] covers sender, time and body, so reading the inbox
 * twice never stores a message twice.
 */
@Entity(
    tableName = "raw_messages",
    indices = [Index(value = ["hash"], unique = true), Index(value = ["status"])]
)
data class RawMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val receivedAt: Instant,
    val hash: String,
    val status: RawStatus = RawStatus.PENDING,
    /** The parser version that set [status]; an older one is re-parsed. */
    val parserVersion: Int = 0,
    /**
     * For [RawStatus.UNROUTED]: the digits it quotes for your side, comma-separated and in the
     * SMS's order (the account before its card), to ask which account they are. Empty when it
     * quotes none.
     */
    val unroutedRefs: String? = null
)
