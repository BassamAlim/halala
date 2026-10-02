package bassamalim.halala.core.domain

import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind

/**
 * Telling people apart from the way banks write them. A transfer's title is a person; it is
 * keyed as merchants are (`Merchants.key`), and each key belongs to one person. Unlike a
 * merchant, a key not seen before never joins a look-alike: "Ahmed Ali" and "Ahmed Saleh" are
 * two people, and only you can say two spellings are one.
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

    private val SPACES = Regex("\\s+")
    private val NOISE = Regex("[\\d*#]+")
    private val TRIM = charArrayOf('-', '_', '.', ',', ';', ':', '/', '(', ')')
}
