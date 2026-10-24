package bassamalim.halala.core.reminders

import bassamalim.halala.core.data.dataSources.room.entities.Loan
import bassamalim.halala.core.data.dataSources.room.entities.RecurringSeries
import bassamalim.halala.core.data.dataSources.room.relations.LoanEventRow
import bassamalim.halala.core.domain.DigestKind
import bassamalim.halala.core.domain.DigestPeriod
import bassamalim.halala.core.domain.Loans
import bassamalim.halala.core.domain.Recurring
import bassamalim.halala.core.enums.CadenceUnit
import bassamalim.halala.core.enums.LoanDirection
import bassamalim.halala.core.enums.LoanEventType
import bassamalim.halala.core.enums.RecurringKind
import bassamalim.halala.core.enums.SeriesStatus
import bassamalim.halala.core.nav.Screen
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DueRemindersTest {

    private val today = LocalDate.parse("2026-10-01")

    private fun series(id: Long, due: String, reminder: Int?, cancel: Boolean = false) = Recurring.stateOf(
        RecurringSeries(
            id = id, uid = "s$id", kind = RecurringKind.BILL, name = "Rent $id", amountMinor = 100, currency = "SAR",
            unit = CadenceUnit.MONTH, anchor = LocalDate.parse(due), reminderDays = reminder, cancelReminder = cancel,
            status = SeriesStatus.ACTIVE, createdAt = Instant.EPOCH
        ),
        emptyList(),
        today
    )

    @Test
    fun `a bill reminds on its lead day, a cancel three days before, a loan on its day`() {
        val loan = Loans.stateOf(
            Loan(9, "l", personId = 4, direction = LoanDirection.LENT, currency = "SAR", dueOn = today, createdAt = Instant.EPOCH),
            listOf(LoanEventRow(1, 9, LoanEventType.DISBURSEMENT, null, 500, Instant.EPOCH, null, null, null))
        )
        val notices = DueReminders.dueOn(
            today,
            listOf(series(1, "2026-10-04", 3), series(2, "2026-10-05", 3), series(3, "2026-10-04", null, cancel = true)),
            listOf(loan),
            mapOf(4L to "Khalid")
        )
        assertEquals(
            listOf(
                DueNotice.Bill(100_001, "Rent 1", LocalDate.parse("2026-10-04")),
                DueNotice.Cancel(200_003, "Rent 3", LocalDate.parse("2026-10-04")),
                DueNotice.Loan(300_009, 4, "Khalid", lent = true)
            ),
            notices
        )
    }

    @Test
    fun `a digest notice opens its digest, and the route survives the intent`() {
        val start = LocalDate.parse("2026-10-18")
        val screen = DueReminders.screenFor(DueNotice.DigestReady(DigestKind.WEEK, DigestPeriod(DigestKind.WEEK, start, start.plusDays(7))))
        assertEquals(Screen.Digest("WEEK", start.toEpochDay()), screen)
        assertEquals(screen, Json.decodeFromString(Screen.serializer(), Json.encodeToString(Screen.serializer(), screen)))
    }
}
