package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Where one account's money went in a month: what came in (the salary, when there was one),
 * what moved to each of your other accounts, what was spent from it, and what stayed.
 * Amounts in the account's currency, minor units.
 */
data class Flow(
    val inMinor: Long,
    val salaryMinor: Long,
    val salaryOn: LocalDate?,
    /** Each account money moved to, and how much, biggest first. */
    val moves: List<Pair<Long, Long>>,
    val spentMinor: Long,
    val keptMinor: Long
) {
    val movedMinor get() = Money.sum(moves.map { it.second })
}

/**
 * The Money flow board: internal transfers drawn as where your money goes. A leg the bank called
 * "between your accounts" that was never paired has one side missing, for you to resolve.
 */
object MoneyFlow {

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
        return Flow(
            inMinor = inMinor,
            salaryMinor = Money.sum(salaries.map { it.transaction.amountMinor }),
            salaryOn = salaries.maxOfOrNull { it.transaction.occurredAt.atZone(zone).toLocalDate() },
            moves = moves,
            spentMinor = spent,
            keptMinor = maxOf(0, inMinor - out)
        )
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
