package bassamalim.halala.core.data.dataSources.room.relations

import androidx.room.Embedded
import bassamalim.halala.core.data.dataSources.room.entities.Rule
import java.time.Instant

/**
 * A rule with what it has done: how many transactions it filed, and when the latest one was.
 * [merchantName] is what its merchant is called now, when it was taught one.
 */
data class RuleWithStats(
    @Embedded val rule: Rule,
    val categoryName: String?,
    val hits: Int,
    val lastHitAt: Instant?,
    val merchantName: String? = null
)
