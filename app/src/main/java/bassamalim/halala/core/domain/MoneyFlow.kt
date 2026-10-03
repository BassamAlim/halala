package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** What a part of the flow is: where money came from, or where it went. */
enum class PartKind(val incoming: Boolean) {
    SALARY(true),
    FROM_ACCOUNT(true),
    FROM_PEOPLE(true),
    REFUNDS(true),
    OTHER_IN(true),
    /** More went out than came in: the rest came out of what was already there. */
    FROM_BALANCE(true),
    TO_ACCOUNT(false),
    /** Put into savings or investments the ledger has no account for. */
    SAVED(false),
    TO_PEOPLE(false),
    CATEGORY(false),
    /** The smaller categories, together. */
    OTHER_SPENDING(false),
    /** Spending not filed yet. */
    UNFILED(false),
    OTHER_OUT(false),
    KEPT(false);

    /** Money that left you: spending, and what went to people. */
    val spending get() = this in setOf(TO_PEOPLE, CATEGORY, OTHER_SPENDING, UNFILED, OTHER_OUT)

    /** Money still yours: moved to your accounts, saved, or left where it was. */
    val kept get() = this in setOf(TO_ACCOUNT, SAVED, KEPT)
}

/**
 * One part of the flow: its [kind], the account or category it is (when it is one) and the
 * transactions that make it up, biggest first. Amounts in minor units.
 */
data class FlowPart(
    val kind: PartKind,
    val minor: Long,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val name: String? = null,
    val transactionIds: List<Long> = emptyList()
) {
    /** Which part this is, the same from month to month. */
    val key get() = "${kind.name}:${accountId ?: ""}:${categoryId ?: ""}"
}

/**
 * Where one account's money went in a month: what came in (the salary, when there was one),
 * what moved to each of your other accounts, what was spent from it, and what stayed; and the
 * same in [sources] (where it came from) and [uses] (where it went), which come to the same total.
 * Amounts in the account's currency, minor units.
 */
data class Flow(
    val inMinor: Long,
    val salaryMinor: Long,
    val salaryOn: LocalDate?,
    /** Each account money moved to, and how much, biggest first. */
    val moves: List<Pair<Long, Long>>,
    val spentMinor: Long,
    val keptMinor: Long,
    val sources: List<FlowPart> = emptyList(),
    val uses: List<FlowPart> = emptyList()
) {
    val movedMinor get() = Money.sum(moves.map { it.second })

    /** What both sides come to: what came in, or what went out when that was more. */
    val totalMinor get() = Money.sum(sources.map { it.minor })
}

/**
 * The Money flow board: internal transfers drawn as where your money goes. A leg the bank called
 * "between your accounts" that was never paired has one side missing, for you to resolve.
 */
object MoneyFlow {

    /** Categories shown on their own; the rest are one part. */
    const val TOP_CATEGORIES = 5

    private val SAVING = setOf(TransactionKind.SAVINGS_DEPOSIT, TransactionKind.INVESTMENT_BUY)

    fun inMonth(detail: TransactionDetail, month: YearMonth, zone: ZoneId) =
        YearMonth.from(detail.transaction.occurredAt.atZone(zone)) == month

    fun of(details: List<TransactionDetail>, accountId: Long, month: YearMonth, zone: ZoneId): Flow {
        val here = details.filter { it.transaction.accountId == accountId && inMonth(it, month, zone) }
        val credits = here.filter { it.transaction.direction == Direction.CREDIT }
        val debits = here.filter { it.transaction.direction == Direction.DEBIT }
        val salaries = credits.filter { it.transaction.kind == TransactionKind.SALARY }
        val moves = debits.filter { it.isInternalTransfer && it.counterpartAccountId != null }
            .groupBy { it.counterpartAccountId!! }
            .map { (id, legs) -> id to Money.sum(legs.map { it.transaction.amountMinor }) }
            .sortedByDescending { it.second }
        val inMinor = Money.sum(credits.map { it.transaction.amountMinor })
        val spent = Money.sum(debits.filter { !it.isInternalTransfer }.map { it.transaction.amountMinor })
        val out = Math.addExact(Money.sum(moves.map { it.second }), spent)
        val kept = maxOf(0, inMinor - out)
        return Flow(
            inMinor = inMinor,
            salaryMinor = Money.sum(salaries.map { it.transaction.amountMinor }),
            salaryOn = salaries.maxOfOrNull { it.transaction.occurredAt.atZone(zone).toLocalDate() },
            moves = moves,
            spentMinor = spent,
            keptMinor = kept,
            sources = sourcesOf(credits) + listOfNotNull(
                (out - inMinor).takeIf { it > 0 }?.let { FlowPart(PartKind.FROM_BALANCE, it) }
            ),
            uses = usesOf(debits) + listOfNotNull(kept.takeIf { it > 0 }?.let { FlowPart(PartKind.KEPT, it) })
        )
    }

    /** Where the money came from, biggest first. */
    private fun sourcesOf(credits: List<TransactionDetail>): List<FlowPart> = credits
        .groupBy { d ->
            val kind = when {
                d.isInternalTransfer && d.counterpartAccountId != null -> PartKind.FROM_ACCOUNT
                d.transaction.kind == TransactionKind.SALARY -> PartKind.SALARY
                d.transaction.kind in People.KINDS -> PartKind.FROM_PEOPLE
                d.transaction.kind == TransactionKind.REFUND -> PartKind.REFUNDS
                else -> PartKind.OTHER_IN
            }
            kind to d.counterpartAccountId.takeIf { kind == PartKind.FROM_ACCOUNT }
        }
        .map { (key, group) -> part(key.first, group, accountId = key.second) }
        .sortedByDescending { it.minor }

    /** Where it went: your accounts, savings, people, then spending by category, biggest first in each. */
    private fun usesOf(debits: List<TransactionDetail>): List<FlowPart> {
        val groups = debits.groupBy { d ->
            val kind = when {
                d.isInternalTransfer && d.counterpartAccountId != null -> PartKind.TO_ACCOUNT
                d.transaction.kind in SAVING -> PartKind.SAVED
                d.transaction.kind in People.KINDS -> PartKind.TO_PEOPLE
                !d.transaction.kind.countsInTotals -> PartKind.OTHER_OUT
                d.transaction.categoryId != null -> PartKind.CATEGORY
                else -> PartKind.UNFILED
            }
            Triple(
                kind,
                d.counterpartAccountId.takeIf { kind == PartKind.TO_ACCOUNT },
                d.transaction.categoryId.takeIf { kind == PartKind.CATEGORY }
            )
        }
        val parts = groups.map { (key, group) ->
            part(key.first, group, accountId = key.second, categoryId = key.third, name = group.first().categoryName.takeIf { key.third != null })
        }
        val categories = parts.filter { it.kind == PartKind.CATEGORY }.sortedByDescending { it.minor }
        val rest = categories.drop(TOP_CATEGORIES)
        val otherSpending = rest.takeIf { it.isNotEmpty() }?.let { small ->
            FlowPart(
                PartKind.OTHER_SPENDING,
                Money.sum(small.map { it.minor }),
                transactionIds = small.flatMap { it.transactionIds }
            )
        }
        fun of(kind: PartKind) = parts.filter { it.kind == kind }.sortedByDescending { it.minor }
        return of(PartKind.TO_ACCOUNT) + of(PartKind.SAVED) + of(PartKind.TO_PEOPLE) +
                categories.take(TOP_CATEGORIES) + listOfNotNull(otherSpending) + of(PartKind.UNFILED) + of(PartKind.OTHER_OUT)
    }

    private fun part(kind: PartKind, group: List<TransactionDetail>, accountId: Long? = null, categoryId: Long? = null, name: String? = null) =
        FlowPart(
            kind = kind,
            minor = Money.sum(group.map { it.transaction.amountMinor }),
            accountId = accountId,
            categoryId = categoryId,
            name = name,
            transactionIds = group.sortedByDescending { it.transaction.amountMinor }.map { it.transaction.id }
        )

    /** Each part's whole percent of [total], rounded half up; none of nothing. */
    fun percentOf(minor: Long, total: Long): Int =
        if (total <= 0) 0 else ((minor * 200 + total) / (total * 2)).toInt()

    /**
     * How a part moved against the month before, in whole percent: null when it wasn't there
     * then (nothing to compare with).
     */
    fun change(minor: Long, previousMinor: Long?): Int? {
        if (previousMinor == null || previousMinor <= 0) return null
        val diff = minor - previousMinor
        val sign = if (diff < 0) -1 else 1
        return sign * ((Math.abs(diff) * 200 + previousMinor) / (previousMinor * 2)).toInt()
    }

    /** The account the board starts on: the one salary landed in, else the one most money came into. */
    fun startingAccount(details: List<TransactionDetail>, month: YearMonth, zone: ZoneId): Long? {
        val credits = details.filter { it.transaction.direction == Direction.CREDIT && inMonth(it, month, zone) }
        fun busiest(list: List<TransactionDetail>) = list.groupBy { it.transaction.accountId }
            .maxByOrNull { (_, legs) -> Money.sum(legs.map { it.transaction.amountMinor }) }?.key
        return busiest(credits.filter { it.transaction.kind == TransactionKind.SALARY }) ?: busiest(credits)
    }

    /** Legs the bank said went between your own accounts with no other side recorded, newest first. */
    fun unmatched(details: List<TransactionDetail>): List<TransactionDetail> = details
        .filter { it.transaction.kind == TransactionKind.INTERNAL_TRANSFER && !it.isInternalTransfer }
        .sortedByDescending { it.transaction.occurredAt }
}
