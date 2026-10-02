package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class MoneyFlowTest {

    private val zone = ZoneOffset.UTC
    private val september = YearMonth.of(2026, 9)

    private fun leg(
        day: String, minor: Long, direction: Direction, kind: TransactionKind,
        account: Long = 1, to: Long? = null
    ) = TransactionDetail(
        transaction = Transaction(
            uid = day + minor + direction, accountId = account, direction = direction, amountMinor = minor, currency = "SAR",
            occurredAt = Instant.parse("${day}T10:00:00Z"), kind = kind, source = TransactionSource.SMS, createdAt = Instant.EPOCH
        ),
        accountNickname = "", institutionName = null, counterpartId = to?.let { 99 }, counterpartAccountId = to,
        counterpartNickname = null, counterpartInstitutionName = null, isTransferInLeg = false
    )

    // The board: 18,000 salary; 5,000 to Awaeed, 1,500 Hasad, 3,000 STC, 1,500 D360, 7,000 spent.
    private val board = listOf(
        leg("2026-09-27", 1_800_000, Direction.CREDIT, TransactionKind.SALARY),
        leg("2026-09-27", 500_000, Direction.DEBIT, TransactionKind.INTERNAL_TRANSFER, to = 2),
        leg("2026-09-27", 150_000, Direction.DEBIT, TransactionKind.INTERNAL_TRANSFER, to = 3),
        leg("2026-09-28", 300_000, Direction.DEBIT, TransactionKind.INTERNAL_TRANSFER, to = 4),
        leg("2026-09-28", 150_000, Direction.DEBIT, TransactionKind.INTERNAL_TRANSFER, to = 5),
        leg("2026-09-29", 400_000, Direction.DEBIT, TransactionKind.PURCHASE),
        leg("2026-09-30", 300_000, Direction.DEBIT, TransactionKind.BILL_PAYMENT),
        // Another month, another account: not in it.
        leg("2026-08-27", 1_800_000, Direction.CREDIT, TransactionKind.SALARY),
        leg("2026-09-10", 20_000, Direction.DEBIT, TransactionKind.PURCHASE, account = 4)
    )

    @Test
    fun `the board's month`() {
        val flow = MoneyFlow.of(board, 1, september, zone)
        assertEquals(1_800_000, flow.salaryMinor)
        assertEquals(LocalDate.of(2026, 9, 27), flow.salaryOn)
        assertEquals(listOf(2L to 500_000L, 4L to 300_000L, 3L to 150_000L, 5L to 150_000L), flow.moves)
        assertEquals(1_100_000, flow.movedMinor)
        assertEquals(700_000, flow.spentMinor)
        assertEquals(0, flow.keptMinor)
    }

    @Test
    fun `what isn't moved or spent stays`() {
        val flow = MoneyFlow.of(board.filter { it.transaction.kind != TransactionKind.BILL_PAYMENT }, 1, september, zone)
        assertEquals(300_000, flow.keptMinor)
    }

    @Test
    fun `starts on the account salary landed in`() {
        assertEquals(1L, MoneyFlow.startingAccount(board, september, zone))
        assertNull(MoneyFlow.startingAccount(board, YearMonth.of(2026, 1), zone))
    }

    @Test
    fun `a move with one side is unmatched, a paired one is not`() {
        val lone = leg("2026-09-24", 500_000, Direction.DEBIT, TransactionKind.INTERNAL_TRANSFER, account = 5)
        assertEquals(listOf(lone), MoneyFlow.unmatched(board + lone))
    }
}
