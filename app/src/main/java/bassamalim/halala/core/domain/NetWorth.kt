package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.Asset
import bassamalim.halala.core.data.dataSources.room.entities.GoalContribution
import bassamalim.halala.core.data.dataSources.room.entities.NetWorthSnapshot
import bassamalim.halala.core.data.dataSources.room.relations.AccountWithBalance
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.AssetType
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.LoanDirection
import bassamalim.halala.core.enums.LoanEventType
import java.time.LocalDate
import java.time.ZoneId

/** The parts net worth is made of, as the Net worth board lists them. */
enum class WealthClass { ACCOUNTS, SAVINGS, FUNDS, GOLD, OTHER_ASSETS, OWED_TO_YOU, YOU_OWE }

/** Net worth now: each class's total (what you owe is negative) and the sum. */
data class NetWorthNow(val parts: Map<WealthClass, Long>, val totalMinor: Long)

/**
 * Net worth: everything you own (accounts, savings, funds, gold, other assets, what is owed to
 * you) less what you owe people, in one currency. Account balances already carry a card's debt.
 */
object NetWorth {

    fun classOf(type: AccountType): WealthClass = when (type) {
        AccountType.SAVINGS, AccountType.DEPOSIT -> WealthClass.SAVINGS
        AccountType.INVESTMENT -> WealthClass.FUNDS
        else -> WealthClass.ACCOUNTS
    }

    fun classOf(type: AssetType): WealthClass = when (type) {
        AssetType.FUND -> WealthClass.FUNDS
        AssetType.GOLD -> WealthClass.GOLD
        else -> WealthClass.OTHER_ASSETS
    }

    fun now(
        accounts: List<AccountWithBalance>,
        assets: List<Asset>,
        loans: List<LoanState>,
        currency: String,
        today: LocalDate,
        heldElsewhereMinor: Long = 0
    ): NetWorthNow {
        val parts = mutableMapOf<WealthClass, Long>()
        fun add(kind: WealthClass, minor: Long) { parts[kind] = Math.addExact(parts[kind] ?: 0, minor) }
        accounts.filter { !it.account.archived && it.account.currency == currency }.forEach { add(classOf(it.account.type), it.balanceMinor) }
        assets.filter { it.currency == currency }.forEach { add(classOf(it.type), Assets.valueOf(it, today)) }
        val (owed, owing) = Loans.owed(loans, currency)
        if (heldElsewhereMinor != 0L) add(WealthClass.SAVINGS, heldElsewhereMinor)
        if (owed != 0L) add(WealthClass.OWED_TO_YOU, owed)
        if (owing != 0L) add(WealthClass.YOU_OWE, -owing)
        return NetWorthNow(parts, Money.sum(parts.values))
    }

    /**
     * Net worth at the end of each day from [from] to [today]: [totalNow] with each later day's
     * money in and out of your accounts undone, loans as they stood that day, and assets as the
     * latest snapshot on or before it (the earliest one before any was taken, else today's).
     */
    fun timeline(
        totalNow: Long,
        assetsNow: Long,
        from: LocalDate,
        today: LocalDate,
        details: List<TransactionDetail>,
        accountIds: Set<Long>,
        loans: List<LoanState>,
        currency: String,
        snapshots: List<NetWorthSnapshot>,
        zone: ZoneId,
        elsewhereByDay: Map<LocalDate, Long> = emptyMap()
    ): List<Pair<LocalDate, Long>> {
        val netByDay = details
            .filter { it.transaction.accountId in accountIds }
            .groupBy { it.transaction.occurredAt.atZone(zone).toLocalDate() }
            .mapValues { (_, list) -> list.sumOf { if (it.transaction.direction == Direction.CREDIT) it.transaction.amountMinor else -it.transaction.amountMinor } }
        // What loans owed (to you +, by you −) changed by each day.
        val loanByDay = loans.filter { it.loan.currency == currency }.flatMap { state ->
            val sign = if (state.loan.direction == LoanDirection.LENT) 1 else -1
            state.events.map { event ->
                val change = when (event.type) {
                    LoanEventType.DISBURSEMENT -> event.amountMinor
                    else -> -event.amountMinor
                }
                event.at.atZone(zone).toLocalDate() to sign * change
            }
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        val sortedSnapshots = snapshots.sortedBy { it.date }
        fun assetsOn(day: LocalDate) =
            sortedSnapshots.lastOrNull { !it.date.isAfter(day) }?.assetsMinor ?: sortedSnapshots.firstOrNull()?.assetsMinor ?: assetsNow

        val points = mutableListOf<Pair<LocalDate, Long>>()
        var withoutAssets = totalNow - assetsNow
        var day = today
        while (!day.isBefore(from)) {
            points += day to withoutAssets + (if (day == today) assetsNow else assetsOn(day))
            withoutAssets -= (netByDay[day] ?: 0) + (loanByDay[day] ?: 0) + (elsewhereByDay[day] ?: 0)
            day = day.minusDays(1)
        }
        return points.reversed()
    }

    /**
     * Savings kept where Halala has no account (Al Rajhi Capital, say), by the day they changed:
     * what left your accounts marked as put toward a goal, less what came back marked as taken
     * out, never below none (what comes back beyond it is profit). A move between your own
     * accounts is already in their balances, and so is a mark that didn't leave them.
     */
    fun heldElsewhere(
        contributions: List<Pair<GoalContribution, TransactionDetail>>,
        currency: String,
        zone: ZoneId
    ): Map<LocalDate, Long> {
        var held = 0L
        return contributions
            .filter { (_, d) -> !d.isInternalTransfer && d.transaction.currency == currency }
            .sortedBy { (_, d) -> d.transaction.occurredAt }
            .mapNotNull { (c, d) ->
                val tx = d.transaction
                val change = when {
                    tx.direction == Direction.DEBIT && !c.withdrawn -> tx.amountMinor
                    tx.direction == Direction.CREDIT && c.withdrawn -> -minOf(tx.amountMinor, held)
                    else -> return@mapNotNull null
                }
                held = Math.addExact(held, change)
                tx.occurredAt.atZone(zone).toLocalDate() to change
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { Money.sum(it.value) }
    }
}
