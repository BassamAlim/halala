package bassamalim.halala.core.domain

import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind

/**
 * Telling people apart from the way banks write them. A transfer's title is a person; it is
 * keyed as merchants are (`Merchants.key`), and each key belongs to one person. Unlike a
 * merchant, a key not seen before never joins a look-alike: "Ahmed Ali" and "Ahmed Saleh" are
 * two people, and only you can say two spellings are one. What Halala does is suggest
 * ([suggest]): it never merges on its own, since a merge moves loans.
 */
object People {

    /** The kinds whose title names a person. Kept in step with `PERSON_KINDS` in `PeopleDao`. */
    val KINDS = setOf(
        TransactionKind.TRANSFER_OUT,
        TransactionKind.TRANSFER_IN,
        TransactionKind.LOAN_GIVEN,
        TransactionKind.LOAN_RECEIVED,
        TransactionKind.LOAN_REPAYMENT
    )

    /**
     * A new person's name, from the first spelling seen: its digits and stray marks dropped, and
     * a name the bank shouted in capitals put in title case ("AHMED ALI" → "Ahmed Ali"). Yours
     * to rename.
     */
    fun nameOf(descriptor: String): String {
        val words = descriptor.trim().split(SPACES)
            .map { it.replace(NOISE, "").trim(*TRIM) }
            .filter { word -> word.any(Char::isLetter) }
        val name = words.joinToString(" ").ifEmpty { descriptor.trim() }
        val shouted = name.any(Char::isLetter) && name.none { it.isLetter() && it.isLowerCase() } &&
                name.any { it in 'A'..'Z' }
        return if (!shouted) name
        else name.lowercase().split(' ').joinToString(" ") { word -> word.replaceFirstChar(Char::titlecase) }
    }

    /** What was sent to someone and what came from them, in one currency, in minor units. */
    data class Flow(val sentMinor: Long, val receivedMinor: Long) {
        /** What came back less what went: negative when you sent more. */
        val netMinor get() = Math.subtractExact(receivedMinor, sentMinor)
    }

    /** Sums transfers ([direction] to amount), for one person in one currency. */
    fun flowOf(transfers: List<Pair<Direction, Long>>): Flow = Flow(
        sentMinor = Money.sum(transfers.filter { it.first == Direction.DEBIT }.map { it.second }),
        receivedMinor = Money.sum(transfers.filter { it.first == Direction.CREDIT }.map { it.second })
    )

    /** Why two people look like one. The first that holds is the one said. */
    enum class MergeReason {
        /** The same letters, however they are spaced or the Arabic is written. */
        SPELLING,
        /** A bank quoted the same last four digits for both. */
        ACCOUNT,
        /** The AI read the names as one: another language, an initial, a name cut short. */
        AI
    }

    /** One person as [suggest] sees them: their [keys] are their aliases' `Merchants.key`s. */
    data class Known(val id: Long, val uid: String, val keys: Collection<String>)

    /** [first] and [second] may be one person; [ref] is the digits they share, for [MergeReason.ACCOUNT]. */
    data class Suggestion(val first: Long, val second: Long, val key: String, val reason: MergeReason, val ref: String? = null)

    /**
     * A key with what never tells two people apart taken out: spaces ("abdul rahman" and
     * "abdulrahman"), and the ways one Arabic letter is written (أ إ آ are ا, ة is ه, ى is ي,
     * no tatweel). Any other difference is another name.
     */
    fun canonical(key: String): String = key.filterNot { it.isWhitespace() || it == 'ـ' }.map { char ->
        when (char) {
            'أ', 'إ', 'آ', 'ٱ' -> 'ا'
            'ة' -> 'ه'
            'ى' -> 'ي'
            else -> char
        }
    }.joinToString("")

    /** What names a pair of people whichever way round, by their uids: nothing of theirs is in it. */
    fun pairKey(uid: String, other: String): String = if (uid < other) "$uid|$other" else "$other|$uid"

    /**
     * The pairs of [people] that may be one person, the surest reason first: spelled the same
     * ([canonical]), sharing digits in [refs] (person id to the last fours banks quoted for
     * them), or in [aiSame] (pair keys the AI said are one). Never a pair in [dismissed].
     */
    // ponytail: every pair is compared (n²); fine for hundreds of people, index by canonical and ref beyond that.
    fun suggest(
        people: List<Known>,
        refs: Map<Long, Set<String>>,
        aiSame: Set<String>,
        dismissed: Set<String>
    ): List<Suggestion> {
        val spelled = people.associate { person -> person.id to person.keys.map(::canonical).filter(String::isNotEmpty).toSet() }
        val found = mutableListOf<Suggestion>()
        for (i in people.indices) for (j in i + 1 until people.size) {
            val a = people[i]
            val b = people[j]
            val key = pairKey(a.uid, b.uid)
            if (key in dismissed) continue
            val shared = refs[a.id].orEmpty().intersect(refs[b.id].orEmpty()).minOrNull()
            val reason = when {
                spelled.getValue(a.id).any { it in spelled.getValue(b.id) } -> MergeReason.SPELLING
                shared != null -> MergeReason.ACCOUNT
                key in aiSame -> MergeReason.AI
                else -> continue
            }
            found += Suggestion(a.id, b.id, key, reason, shared.takeIf { reason == MergeReason.ACCOUNT })
        }
        return found.sortedBy { it.reason }
    }

    private val SPACES = Regex("\\s+")
    private val NOISE = Regex("[\\d*#]+")
    private val TRIM = charArrayOf('-', '_', '.', ',', ';', ':', '/', '(', ')')
}
