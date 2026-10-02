package bassamalim.halala.core.data.dataSources.room.relations

import androidx.room.Embedded
import bassamalim.halala.core.data.dataSources.room.entities.Category

/** A category with how many transactions are filed under it. */
data class CategoryWithUse(
    @Embedded val category: Category,
    val uses: Int
)
