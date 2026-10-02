package bassamalim.halala.core.data.dataSources.room.relations

import androidx.room.Embedded
import bassamalim.halala.core.data.dataSources.room.entities.Person
import bassamalim.halala.core.data.dataSources.room.entities.PersonAlias
import java.time.Instant

/** A person with how many transfers are theirs, and the latest. */
data class PersonWithStats(
    @Embedded val person: Person,
    val transactions: Int,
    val lastAt: Instant?
)

/** A spelling of a person with how many transfers it names. */
data class PersonAliasWithCount(
    @Embedded val alias: PersonAlias,
    val transactions: Int
)
