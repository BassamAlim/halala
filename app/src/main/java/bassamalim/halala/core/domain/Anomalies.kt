package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.BalanceCheckpoint
import bassamalim.halala.core.data.dataSources.room.entities.RawMessage
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.IdentifiedBy
import bassamalim.halala.core.enums.RawStatus
import java.time.Duration
import java.time.Instant

/** Something that looks wrong, with a stable [key] so dismissing it sticks. */
sealed interface Anomaly {
    val key: String
    val at: Instant

    /** The same charge twice: [second] a day or less after [first], same merchant, amount and account. */
    data class Duplicate(val first: TransactionDetail, val second: TransactionDetail) : Anomaly {
        override val key get() = "dup:${first.transaction.id}:${second.transaction.id}"
        override val at: Instant get() = second.transaction.occurredAt
    }

    /** Far above what this merchant usually charges ([typicalMinor], the median before it). */
    data class Large(val detail: TransactionDetail, val typicalMinor: Long) : Anomaly {
        override val key get() = "large:${detail.transaction.id}"
        override val at: Instant get() = detail.transaction.occurredAt
    }

    /** A charge in a foreign currency. */
    data class Foreign(val detail: TransactionDetail) : Anomaly {
        override val key get() = "foreign:${detail.transaction.id}"
        override val at: Instant get() = detail.transaction.occurredAt
    }

    /** A card or transfer the bank declined. */
    data class Declined(val message: RawMessage) : Anomaly {
        override val key get() = "declined:${message.hash}"
        override val at: Instant get() = message.receivedAt
    }

    /**
     * The bank's balance on [accountId] at [at] isn't the one before it plus what was recorded
     * in between: an SMS was missed, or recorded twice. [expectedMinor] is what the ledger makes it.
     */
    data class Mismatch(
        val accountId: Long,
        val currency: String,
        val expectedMinor: Long,
        val reportedMinor: Long,
        override val at: Instant
    ) : Anomaly {
        override val key get() = "mismatch:$accountId:${at.toEpochMilli()}"
    }
}

/**
 * The spec's anomaly alerts, found in the ledger as it is (nothing is stored but what you
 * dismissed): over the last [WINDOW_DAYS] days, newest first.
 */
object Anomalies {

    const val WINDOW_DAYS = 30L

    /** A charge this many times the merchant's median, and at least [LARGE_FLOOR_MINOR] above it, is unusual. */
    private const val LARGE_FACTOR = 3
    private const val LARGE_FLOOR_MINOR = 10_000L
    private const val LARGE_HISTORY = 4

    private val KNOWN = setOf(IdentifiedBy.LIST, IdentifiedBy.YOU)

    /** A key that quiets every "unusually large" for a merchant: "this is normal for it". */
    fun normalFor(merchantId: Long) = "large-merchant:$merchantId"

    fun find(
        details: List<TransactionDetail>,
        checkpoints: List<BalanceCheckpoint>,
        messages: List<RawMessage>,
        currencies: Map<Long, String>,
        dismissed: Set<String>,
        now: Instant
    ): List<Anomaly> {
        val since = now.minus(Duration.ofDays(WINDOW_DAYS))
        val spending = details.filter {
            it.transaction.direction == Direction.DEBIT && !it.isInternalTransfer && it.transaction.kind.countsInTotals
        }
        val recent = spending.filter { it.transaction.occurredAt.isAfter(since) }

        val duplicates = recent
            .filter { it.transaction.merchantKey.isNotBlank() }
            .groupBy { Triple(it.transaction.merchantKey, it.transaction.amountMinor, it.transaction.accountId) }
            .values.flatMap { same ->
                same.sortedBy { it.transaction.occurredAt }.zipWithNext()
                    .filter { (a, b) -> Duration.between(a.transaction.occurredAt, b.transaction.occurredAt) <= Duration.ofDays(1) }
                    .map { (a, b) -> Anomaly.Duplicate(a, b) }
            }

        val byMerchant = spending.filter { it.merchantId != null }.groupBy { it.merchantId!! }
        val large = recent.mapNotNull { detail ->
            val merchantId = detail.merchantId ?: return@mapNotNull null
            if (normalFor(merchantId) in dismissed) return@mapNotNull null
            val before = byMerchant[merchantId].orEmpty()
                .filter { it.transaction.occurredAt.isBefore(detail.transaction.occurredAt) }
                .map { it.transaction.amountMinor }
            if (before.size < LARGE_HISTORY) return@mapNotNull null
            val typical = PayCycles.median(before)
            val amount = detail.transaction.amountMinor
            if (amount > typical * LARGE_FACTOR && amount - typical >= LARGE_FLOOR_MINOR) Anomaly.Large(detail, typical) else null
        }

        // A merchant the list or you identified is known: its foreign charges are expected.
        val foreign = recent
            .filter { it.transaction.originalCurrency != null && it.merchantIdentifiedBy !in KNOWN }
            .map { Anomaly.Foreign(it) }

        val declined = messages.filter { it.status == RawStatus.DECLINED && it.receivedAt.isAfter(since) }.map { Anomaly.Declined(it) }

        val mismatches = checkpoints.groupBy { it.accountId }.flatMap { (accountId, points) ->
            val flows = details.filter { it.transaction.accountId == accountId }
            points.sortedBy { it.at }.zipWithNext().mapNotNull { (a, b) ->
                if (!b.at.isAfter(since)) return@mapNotNull null
                val between = flows.filter { it.transaction.occurredAt.isAfter(a.at) && !it.transaction.occurredAt.isAfter(b.at) }
                val net = between.sumOf { if (it.transaction.direction == Direction.CREDIT) it.transaction.amountMinor else -it.transaction.amountMinor }
                val expected = a.balanceMinor + net
                if (expected != b.balanceMinor)
                    Anomaly.Mismatch(accountId, currencies[accountId].orEmpty(), expected, b.balanceMinor, b.at)
                else null
            }
        }

        return (duplicates + large + foreign + declined + mismatches)
            .filter { it.key !in dismissed }
            .sortedByDescending { it.at }
    }
}
