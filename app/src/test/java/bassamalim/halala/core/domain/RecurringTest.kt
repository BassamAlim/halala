package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.RecurringSeries
import bassamalim.halala.core.enums.CadenceUnit
import bassamalim.halala.core.enums.RecurringKind
import bassamalim.halala.core.enums.SeriesStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class RecurringTest {

    private val today = LocalDate.parse("2026-09-30")

    private fun series(
        anchor: String,
        amount: Long = 5_600,
        unit: CadenceUnit = CadenceUnit.MONTH,
        every: Int = 1,
        merchantId: Long? = 1,
        endsOn: String? = null
    ) = RecurringSeries(
        uid = "s", kind = RecurringKind.SUBSCRIPTION, name = "Netflix", merchantId = merchantId,
        amountMinor = amount, currency = "SAR", every = every, unit = unit, anchor = LocalDate.parse(anchor),
        endsOn = endsOn?.let(LocalDate::parse), status = SeriesStatus.ACTIVE, createdAt = Instant.EPOCH
    )

    private fun charge(date: String, amount: Long = 5_600, id: Long = date.hashCode().toLong()) =
        Charge(id, LocalDate.parse(date), amount)

    @Test
    fun `months count from the anchor, so the 31st never drifts`() {
        val jan31 = LocalDate.parse("2026-01-31")
        assertEquals(LocalDate.parse("2026-02-28"), Recurring.occurrence(jan31, 1, CadenceUnit.MONTH, 1))
        assertEquals(LocalDate.parse("2026-03-31"), Recurring.occurrence(jan31, 1, CadenceUnit.MONTH, 2))
    }

    @Test
    fun `each charge moves a linked series on, an early one included`() {
        val state = Recurring.stateOf(series("2026-09-03"), listOf(charge("2026-08-03"), charge("2026-09-01")), today)
        assertEquals(LocalDate.parse("2026-10-03"), state.nextDue)
        assertFalse(state.missed)
    }

    @Test
    fun `a charge that doesn't come is missed after the grace days`() {
        val state = Recurring.stateOf(series("2026-09-20"), emptyList(), today)
        assertEquals(LocalDate.parse("2026-09-20"), state.nextDue)
        assertTrue(state.missed)
        assertFalse(Recurring.stateOf(series("2026-09-28"), emptyList(), today).missed)
    }

    @Test
    fun `an unlinked bill is taken as paid when its day passes`() {
        val state = Recurring.stateOf(series("2026-07-01", merchantId = null), emptyList(), today)
        assertEquals(LocalDate.parse("2026-10-01"), state.nextDue)
        assertFalse(state.missed)
    }

    @Test
    fun `a charge above the known price is a rise`() {
        val state = Recurring.stateOf(series("2026-09-19", amount = 2_900), listOf(charge("2026-09-19", 3_500)), today)
        assertEquals(3_500L, state.raisedTo)
    }

    @Test
    fun `an ended contract is due no more and costs nothing`() {
        val state = Recurring.stateOf(series("2026-01-01", merchantId = null, endsOn = "2026-08-31"), emptyList(), today)
        assertNull(state.nextDue)
        assertEquals(0, state.monthlyMinor)
    }

    @Test
    fun `a yearly renewal within a month, and a trial's first charge within a week, are heads-ups`() {
        // Insurance renews yearly: 20 days away is a heads-up, 40 isn't; monthly ones never are.
        val renewal = Recurring.stateOf(series("2025-10-20", unit = CadenceUnit.YEAR), listOf(charge("2025-10-20")), today)
        assertEquals(HeadsUp.RENEWAL, Recurring.headsUp(renewal, today))
        assertNull(Recurring.headsUp(Recurring.stateOf(series("2025-11-09", unit = CadenceUnit.YEAR), listOf(charge("2025-11-09")), today), today))
        assertEquals(HeadsUp.RENEWAL, Recurring.headsUp(Recurring.stateOf(series("2025-10-20", every = 12), listOf(charge("2025-10-20")), today), today))
        assertNull(Recurring.headsUp(Recurring.stateOf(series("2026-09-03"), listOf(charge("2026-08-03"), charge("2026-09-01")), today), today))

        // A trial: no charge yet, first one in five days; not once charged, nor ten days out.
        val trial = Recurring.stateOf(series("2026-10-05"), emptyList(), today)
        assertEquals(HeadsUp.FIRST_CHARGE, Recurring.headsUp(trial, today))
        assertNull(Recurring.headsUp(Recurring.stateOf(series("2026-10-10"), emptyList(), today), today))
        assertNull(Recurring.headsUp(trial.copy(series = trial.series.copy(cancelReminder = true)), today))
        assertEquals("series:0:2026-10-05", Recurring.headsUpKey(trial))
    }

    @Test
    fun `yearly and monthly equivalents round half up`() {
        assertEquals(67_200, Recurring.yearly(5_600, 1, CadenceUnit.MONTH))
        assertEquals(2_400, Recurring.yearly(2_400, 1, CadenceUnit.YEAR))
        assertEquals(200, Recurring.monthly(2_400))
        assertEquals(5_200, Recurring.yearly(100, 1, CadenceUnit.WEEK))
        assertEquals(33_600, Recurring.yearly(5_600, 2, CadenceUnit.MONTH))
    }

    @Test
    fun `three steady monthly charges are a subscription, due a month after the last`() {
        val group = ChargeGroup("Netflix", 1, null, "SAR", listOf(charge("2026-07-03"), charge("2026-08-03"), charge("2026-09-03")))
        val proposal = Recurring.detect(listOf(group), today).single()
        assertEquals(RecurringKind.SUBSCRIPTION, proposal.kind)
        assertEquals(CadenceUnit.MONTH, proposal.unit)
        assertEquals(LocalDate.parse("2026-10-03"), proposal.anchor)
    }

    @Test
    fun `an amount that moves some is a bill, too much or too irregular is nothing`() {
        val bill = ChargeGroup("STC", 2, null, "SAR", listOf(charge("2026-07-05", 20_000), charge("2026-08-05", 23_000), charge("2026-09-05", 21_000)))
        assertEquals(RecurringKind.BILL, Recurring.detect(listOf(bill), today).single().kind)

        val wild = ChargeGroup("Panda", 3, null, "SAR", listOf(charge("2026-07-05", 1_000), charge("2026-08-05", 9_000), charge("2026-09-05", 2_000)))
        val irregular = ChargeGroup("Jahez", 4, null, "SAR", listOf(charge("2026-08-01"), charge("2026-08-12"), charge("2026-09-20")))
        val twice = ChargeGroup("Shahid", 5, null, "SAR", listOf(charge("2026-08-19"), charge("2026-09-19")))
        assertEquals(emptyList<Proposal>(), Recurring.detect(listOf(wild, irregular, twice), today))
    }

    @Test
    fun `regular transfers to someone are planned, and old ones aren't proposed`() {
        val mother = ChargeGroup("Mother", null, 7, "SAR", listOf(charge("2026-06-27", 200_000), charge("2026-07-27", 200_000), charge("2026-08-27", 200_000), charge("2026-09-27", 200_000)))
        assertEquals(RecurringKind.PLANNED, Recurring.detect(listOf(mother), today).single().kind)

        val stopped = ChargeGroup("Gym", 8, null, "SAR", listOf(charge("2026-01-10"), charge("2026-02-10"), charge("2026-03-10")))
        assertEquals(emptyList<Proposal>(), Recurring.detect(listOf(stopped), today))
    }
}
