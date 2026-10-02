package bassamalim.halala.core.domain

/** What can be wrong with a split before it is saved. */
enum class SplitProblem { NoOne, ShareMissing, TooMuch }

/**
 * Splitting a bill you paid: each person's share becomes a loan they owe you, and only what is
 * left is your spending. Shares are exact minor units.
 */
object Splits {

    /**
     * [totalMinor] in equal shares between you and [people] others: each of theirs, rounded down,
     * so any remainder (a halala or two) stays with you.
     */
    fun equalShare(totalMinor: Long, people: Int): Long = if (people <= 0) 0 else totalMinor / (people + 1)

    /** What is left as yours once [shares] are taken out of [totalMinor]. */
    fun yours(totalMinor: Long, shares: Collection<Long>): Long = Math.subtractExact(totalMinor, Money.sum(shares))

    /** The problems with giving [shares] (person → minor units) of [totalMinor]; empty when it can be saved. */
    fun validate(totalMinor: Long, shares: Map<Long, Long?>): Set<SplitProblem> = buildSet {
        if (shares.isEmpty()) add(SplitProblem.NoOne)
        if (shares.values.any { it == null || it <= 0 }) add(SplitProblem.ShareMissing)
        else if (Money.sum(shares.values.filterNotNull()) > totalMinor) add(SplitProblem.TooMuch)
    }
}
