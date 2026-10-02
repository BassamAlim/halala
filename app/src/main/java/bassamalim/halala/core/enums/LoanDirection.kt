package bassamalim.halala.core.enums

/** Which way a loan went: you lent it (they owe you) or borrowed it (you owe them). */
enum class LoanDirection {
    LENT,
    BORROWED;

    /** The way money moves when it is lent or borrowed: out when you lend, in when you borrow. */
    val outgoing get() = if (this == LENT) Direction.DEBIT else Direction.CREDIT

    /** The way it moves when it is paid back. */
    val repaying get() = if (this == LENT) Direction.CREDIT else Direction.DEBIT

    companion object {
        /** A transfer out lends; one in borrows. */
        fun of(direction: Direction) = if (direction == Direction.DEBIT) LENT else BORROWED
    }
}
