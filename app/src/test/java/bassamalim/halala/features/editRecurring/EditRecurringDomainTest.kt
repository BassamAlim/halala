package bassamalim.halala.features.editRecurring

import bassamalim.halala.core.enums.RecurringKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class EditRecurringDomainTest {

    private val today = LocalDate.parse("2026-10-03")

    @Test
    fun `a series started from a charge is monthly, next due on or after today, linked to its merchant`() {
        val form = EditRecurringDomain.fromCharge("Netflix", 9, 5_600, "SAR", LocalDate.parse("2026-08-31"), RecurringKind.SUBSCRIPTION, today)
        // Aug 31 → Sep 30 has passed → Oct 31, counted from the charge so the month end holds.
        assertEquals(LocalDate.parse("2026-10-31"), form.nextDue)
        assertEquals("56.00", form.amount)
        assertEquals(9L, form.merchantId)
        assertEquals(true, form.autoRenew)

        val bill = EditRecurringDomain.fromCharge("SEC", null, 31_250, "SAR", LocalDate.parse("2026-09-03"), RecurringKind.BILL, today)
        assertEquals(LocalDate.parse("2026-10-03"), bill.nextDue)
        assertEquals(false, bill.autoRenew)
    }
}
