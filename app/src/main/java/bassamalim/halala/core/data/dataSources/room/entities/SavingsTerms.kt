package bassamalim.halala.core.data.dataSources.room.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import bassamalim.halala.core.enums.MaturityChoice
import bassamalim.halala.core.enums.SavingsKind
import java.time.LocalDate

/**
 * The terms of a savings account (its balance stays the account's): the profit [ratePercent] a
 * year (exact decimal text; yours to keep current, since the bank changes it) and, for a term
 * deposit, when the term started, how many months it runs and what happens at maturity.
 */
@Entity(
    tableName = "savings_terms",
    foreignKeys = [ForeignKey(entity = Account::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE)]
)
data class SavingsTerms(
    @PrimaryKey val accountId: Long,
    val kind: SavingsKind,
    val ratePercent: String,
    val startDate: LocalDate? = null,
    val tenorMonths: Int? = null,
    val maturityChoice: MaturityChoice? = null
)
