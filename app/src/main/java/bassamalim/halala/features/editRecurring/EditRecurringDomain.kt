package bassamalim.halala.features.editRecurring

import bassamalim.halala.core.Globals
import bassamalim.halala.core.data.dataSources.room.entities.RecurringSeries
import bassamalim.halala.core.data.repositories.RecurringRepository
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.enums.CadenceUnit
import bassamalim.halala.core.enums.RecurringKind
import bassamalim.halala.core.enums.SeriesStatus
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** A subscription, bill or planned payment as the form holds it: text as typed. */
data class SeriesForm(
    val name: String = "",
    val kind: RecurringKind = RecurringKind.BILL,
    val amount: String = "",
    val every: String = "1",
    val unit: CadenceUnit = CadenceUnit.MONTH,
    val nextDue: LocalDate = LocalDate.MIN,
    val autoRenew: Boolean = false,
    val endsOn: LocalDate? = null,
    val reminderDays: Int? = null,
    val currency: String = Globals.PRIMARY_CURRENCY,
    /** What it is paid to, when it was found in history: kept, not edited. */
    val merchantId: Long? = null,
    val personId: Long? = null,
    val uid: String = "",
    val status: SeriesStatus = SeriesStatus.ACTIVE,
    val cancelReminder: Boolean = false,
    val createdAt: Instant = Instant.EPOCH
)

enum class SeriesProblem { NameMissing, AmountInvalid, EveryInvalid, EndsBeforeDue }

sealed interface CheckedSeries {
    data class Valid(val series: RecurringSeries) : CheckedSeries
    data class Invalid(val problems: Set<SeriesProblem>) : CheckedSeries
}

class EditRecurringDomain @Inject constructor(
    private val recurringRepository: RecurringRepository,
    private val clock: Clock
) {

    fun today(): LocalDate = LocalDate.now(clock)

    /** The series as a form, due on the day it is next due (its charges so far counted). */
    suspend fun load(id: Long): SeriesForm? {
        val state = recurringRepository.observeStates().first().firstOrNull { it.series.id == id } ?: return null
        val series = state.series
        return SeriesForm(
            name = series.name,
            kind = series.kind,
            amount = Money.input(state.raisedTo ?: series.amountMinor, series.currency),
            every = series.every.toString(),
            unit = series.unit,
            nextDue = state.nextDue ?: series.anchor,
            autoRenew = series.autoRenew,
            endsOn = series.endsOn,
            reminderDays = series.reminderDays,
            currency = series.currency,
            merchantId = series.merchantId,
            personId = series.personId,
            uid = series.uid,
            status = series.status,
            cancelReminder = series.cancelReminder,
            createdAt = series.createdAt
        )
    }

    /** Checks and writes (a proposal saved is confirmed); the problems when there are any. */
    suspend fun save(id: Long, form: SeriesForm): Set<SeriesProblem> = when (val checked = validate(id, form)) {
        is CheckedSeries.Invalid -> checked.problems
        is CheckedSeries.Valid -> {
            recurringRepository.save(checked.series)
            emptySet()
        }
    }

    suspend fun delete(id: Long) = recurringRepository.delete(id)

    companion object {

        const val MAX_EVERY = 365

        /** The reminder lead times offered, in days. */
        val REMINDERS = listOf(1, 3, 7, 30)

        fun validate(id: Long, form: SeriesForm): CheckedSeries {
            val problems = mutableSetOf<SeriesProblem>()
            if (form.name.isBlank()) problems += SeriesProblem.NameMissing
            val amount = Money.parse(form.amount, form.currency)?.takeIf { it > 0 }
            if (amount == null) problems += SeriesProblem.AmountInvalid
            val every = form.every.trim().toIntOrNull()?.takeIf { it in 1..MAX_EVERY }
            if (every == null) problems += SeriesProblem.EveryInvalid
            if (form.endsOn != null && form.endsOn.isBefore(form.nextDue)) problems += SeriesProblem.EndsBeforeDue
            if (problems.isNotEmpty()) return CheckedSeries.Invalid(problems)

            return CheckedSeries.Valid(
                RecurringSeries(
                    id = id,
                    uid = form.uid,
                    kind = form.kind,
                    name = form.name.trim(),
                    merchantId = form.merchantId,
                    personId = form.personId,
                    amountMinor = amount!!,
                    currency = form.currency,
                    every = every!!,
                    unit = form.unit,
                    anchor = form.nextDue,
                    autoRenew = form.autoRenew,
                    endsOn = form.endsOn,
                    reminderDays = form.reminderDays,
                    cancelReminder = form.cancelReminder,
                    status = SeriesStatus.ACTIVE,
                    createdAt = form.createdAt
                )
            )
        }
    }
}
