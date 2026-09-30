package bassamalim.halala.features.editTransaction

import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class EditTransactionDomainTest {

    private val zone = ZoneId.of("Asia/Riyadh")
    private val cash = AccountOption(1, "Cash", "SAR", isCash = true)
    private val salary = AccountOption(2, "Al Rajhi – Salary", "SAR", isCash = false)
    private val dollars = AccountOption(3, "Al Rajhi – Dollars", "USD", isCash = false)
    private val accounts = listOf(cash, salary, dollars)

    private fun form(
        mode: EntryMode = EntryMode.OUT,
        amount: String = "62.50",
        accountId: Long? = cash.id,
        toAccountId: Long? = null,
        kind: TransactionKind = TransactionKind.PURCHASE
    ) = TransactionForm(
        mode = mode,
        amount = amount,
        accountId = accountId,
        toAccountId = toAccountId,
        kind = kind,
        title = "Jahez",
        date = LocalDate.of(2026, 9, 29),
        time = LocalTime.of(21, 14)
    )

    private fun check(form: TransactionForm) = EditTransactionDomain.validate(form, accounts, zone)

    @Test
    fun `money out is a debit in halalas, at the local time you gave`() {
        val draft = (check(form()) as CheckedEntry.Single).draft

        assertEquals(Direction.DEBIT, draft.direction)
        assertEquals(6_250L, draft.amountMinor)
        assertEquals(LocalDateTime.of(2026, 9, 29, 21, 14).atZone(zone).toInstant(), draft.occurredAt)
        assertEquals(TransactionKind.PURCHASE, draft.kind)
    }

    @Test
    fun `money in is a credit, and a kind from the other side falls back to one of its own`() {
        val draft = (check(form(mode = EntryMode.IN, kind = TransactionKind.PURCHASE)) as CheckedEntry.Single).draft

        assertEquals(Direction.CREDIT, draft.direction)
        assertEquals(TransactionKind.MANUAL_IN.first(), draft.kind)
    }

    @Test
    fun `the amount is read in the account's own currency`() {
        assertEquals(setOf(TransactionProblem.AmountInvalid), (check(form(amount = "1.234")) as CheckedEntry.Invalid).problems)
        assertEquals(12_300L, ((check(form(amount = "123", accountId = dollars.id))) as CheckedEntry.Single).draft.amountMinor)
    }

    @Test
    fun `zero and nonsense are not amounts`() {
        listOf("0", "", "abc", "-5").forEach {
            assertEquals(
                "\"$it\"",
                setOf(TransactionProblem.AmountInvalid),
                (check(form(amount = it)) as CheckedEntry.Invalid).problems
            )
        }
    }

    @Test
    fun `no account, no transaction`() {
        assertEquals(setOf(TransactionProblem.AccountMissing), (check(form(accountId = null)) as CheckedEntry.Invalid).problems)
    }

    @Test
    fun `taking cash out of the bank is an ATM withdrawal, putting it back a deposit`() {
        val out = (check(form(mode = EntryMode.MOVE, accountId = salary.id, toAccountId = cash.id)) as CheckedEntry.Move).draft
        assertEquals(TransactionKind.ATM_WITHDRAWAL, out.kind)
        assertEquals(salary.id, out.fromAccountId)
        assertEquals(cash.id, out.toAccountId)

        val back = (check(form(mode = EntryMode.MOVE, accountId = cash.id, toAccountId = salary.id)) as CheckedEntry.Move).draft
        assertEquals(TransactionKind.CASH_DEPOSIT, back.kind)
    }

    @Test
    fun `a move needs two different accounts in one currency`() {
        assertEquals(
            setOf(TransactionProblem.SameAccount),
            (check(form(mode = EntryMode.MOVE, accountId = salary.id, toAccountId = salary.id)) as CheckedEntry.Invalid).problems
        )
        assertEquals(
            setOf(TransactionProblem.CurrencyMismatch),
            (check(form(mode = EntryMode.MOVE, accountId = salary.id, toAccountId = dollars.id)) as CheckedEntry.Invalid).problems
        )
        assertEquals(
            setOf(TransactionProblem.AccountMissing),
            (check(form(mode = EntryMode.MOVE, accountId = salary.id, toAccountId = null)) as CheckedEntry.Invalid).problems
        )
    }

    @Test
    fun `moves between two banks are internal transfers`() {
        val other = AccountOption(4, "D360", "SAR", isCash = false)
        val move = EditTransactionDomain.validate(
            form(mode = EntryMode.MOVE, accountId = salary.id, toAccountId = other.id),
            accounts + other,
            zone
        ) as CheckedEntry.Move

        assertEquals(TransactionKind.INTERNAL_TRANSFER, move.draft.kind)
    }
}
