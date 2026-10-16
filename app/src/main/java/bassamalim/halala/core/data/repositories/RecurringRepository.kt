package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.RecurringDao
import bassamalim.halala.core.data.dataSources.room.daos.TransactionsDao
import bassamalim.halala.core.data.dataSources.room.entities.RecurringSeries
import bassamalim.halala.core.data.dataSources.room.entities.SeriesPayment
import bassamalim.halala.core.domain.Charges
import bassamalim.halala.core.domain.Recurring
import bassamalim.halala.core.domain.SeriesState
import bassamalim.halala.core.enums.RecurringKind
import bassamalim.halala.core.enums.SeriesStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Subscriptions, bills and planned payments. Detection proposes; you confirm, change or dismiss.
 * Charges aren't stored against a series: they are its merchant's (or person's) transactions,
 * read as they are, so nothing here has to be kept in step with the ledger. Only the payments
 * you link by hand (rent by a transfer that names no one) are stored, in `series_payments`.
 */
@Singleton
class RecurringRepository @Inject constructor(
    private val recurringDao: RecurringDao,
    private val transactionsDao: TransactionsDao,
    private val clock: Clock
) {

    /** Every series not dismissed, where it stands today. */
    fun observeStates(): Flow<List<SeriesState>> =
        combine(recurringDao.observeAll(), transactionsDao.observeAllDetails(), recurringDao.observePayments()) { all, details, payments ->
            val today = LocalDate.now(clock)
            val paidTo = payments.associate { it.transactionId to it.seriesId }
            all.filter { it.status != SeriesStatus.DISMISSED }
                .map { Recurring.stateOf(it, Charges.of(it, details, clock.zone, paidTo), today) }
        }.flowOn(Dispatchers.Default)

    /** Which series each transaction you linked by hand pays: transaction id → series id. */
    fun observePayments(): Flow<Map<Long, Long>> =
        recurringDao.observePayments().map { payments -> payments.associate { it.transactionId to it.seriesId } }

    suspend fun getPayments(): List<SeriesPayment> = recurringDao.getPayments()

    /** Transaction [transactionId] pays [seriesId] (and no other): one of its charges, whatever it was paid to. */
    suspend fun link(transactionId: Long, seriesId: Long) {
        if (recurringDao.get(seriesId) == null) return
        recurringDao.insertPayment(SeriesPayment(transactionId = transactionId, seriesId = seriesId))
    }

    /** It no longer pays a series by hand: it counts for its merchant's or person's again, if any. */
    suspend fun unlink(transactionId: Long) = recurringDao.deletePayment(transactionId)

    fun observe(id: Long): Flow<RecurringSeries?> = recurringDao.observe(id)

    fun observeAll(): Flow<List<RecurringSeries>> = recurringDao.observeAll()

    suspend fun getAll(): List<RecurringSeries> = recurringDao.getAll()

    suspend fun get(id: Long): RecurringSeries? = recurringDao.get(id)

    /**
     * Proposes each repeating payment that no series speaks for yet (a dismissed one included,
     * so what you said isn't one stays that way). Returns how many it proposed.
     */
    suspend fun detect(): Int {
        val details = transactionsDao.getAllDetails()
        val existing = recurringDao.getAll()
        val taken = existing.mapNotNull { it.merchantId }.toSet()
        val takenPeople = existing.mapNotNull { it.personId }.toSet()

        val paid = recurringDao.getPayments().mapTo(mutableSetOf()) { it.transactionId }

        val proposals = Recurring.detect(Charges.groups(details, clock.zone, paid), LocalDate.now(clock))
            .filter { (it.merchantId == null || it.merchantId !in taken) && (it.personId == null || it.personId !in takenPeople) }
        for (proposal in proposals) recurringDao.insert(
            RecurringSeries(
                uid = UUID.randomUUID().toString(),
                kind = proposal.kind,
                name = proposal.name,
                merchantId = proposal.merchantId,
                personId = proposal.personId,
                amountMinor = proposal.amountMinor,
                currency = proposal.currency,
                every = proposal.every,
                unit = proposal.unit,
                anchor = proposal.anchor,
                autoRenew = proposal.kind == RecurringKind.SUBSCRIPTION,
                status = SeriesStatus.PROPOSED,
                createdAt = clock.instant()
            )
        )
        return proposals.size
    }

    /** Adds a new one ([series] with id 0, made active) or saves changes to an existing one. */
    suspend fun save(series: RecurringSeries): Long {
        require(series.amountMinor > 0) { "Amounts are positive." }
        require(series.every >= 1) { "A cadence is at least one unit." }
        return if (series.id == 0L) recurringDao.insert(
            series.copy(uid = series.uid.ifEmpty { UUID.randomUUID().toString() }, status = SeriesStatus.ACTIVE, createdAt = clock.instant())
        ) else {
            recurringDao.update(series)
            series.id
        }
    }

    suspend fun setStatus(id: Long, status: SeriesStatus) {
        val series = recurringDao.get(id) ?: return
        if (series.status != status) recurringDao.update(series.copy(status = status))
    }

    /** "Keep it": the new price is the price you know. */
    suspend fun acceptPrice(id: Long, amountMinor: Long) {
        val series = recurringDao.get(id) ?: return
        if (amountMinor > 0) recurringDao.update(series.copy(amountMinor = amountMinor))
    }

    /** "Remind me to cancel": a reminder before it next renews, and the new price taken as known. */
    suspend fun remindToCancel(id: Long, amountMinor: Long?) {
        val series = recurringDao.get(id) ?: return
        recurringDao.update(series.copy(cancelReminder = true, amountMinor = amountMinor?.takeIf { it > 0 } ?: series.amountMinor))
    }

    suspend fun delete(id: Long) = recurringDao.delete(id)
}
