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
    fun `where it came from and where it went come to the same, by part`() {
        val flow = MoneyFlow.of(board, 1, september, zone)
        assertEquals(listOf(PartKind.SALARY), flow.sources.map { it.kind })
        assertEquals(
            listOf(PartKind.TO_ACCOUNT, PartKind.TO_ACCOUNT, PartKind.TO_ACCOUNT, PartKind.TO_ACCOUNT, PartKind.UNFILED),
            flow.uses.map { it.kind }
        )
        assertEquals(flow.totalMinor, flow.uses.sumOf { it.minor })
        assertEquals(2, flow.uses.last().transactionIds.size)
    }

    @Test
    fun `spending by category, the smaller ones together, and what came out of the balance`() {
        val spends = (1..7).map { i ->
            leg("2026-09-0$i", i * 10_000L, Direction.DEBIT, TransactionKind.PURCHASE).let {
                it.copy(transaction = it.transaction.copy(categoryId = i.toLong()), categoryName = "C$i")
            }
        }
        val pay = leg("2026-09-01", 200_000, Direction.CREDIT, TransactionKind.SALARY)
        val flow = MoneyFlow.of(spends + pay, 1, september, zone)
        assertEquals(listOf("C7", "C6", "C5", "C4", "C3", null), flow.uses.map { it.name })
        assertEquals(PartKind.OTHER_SPENDING, flow.uses.last().kind)
        assertEquals(30_000, flow.uses.last().minor)
        assertEquals(listOf(PartKind.SALARY, PartKind.FROM_BALANCE), flow.sources.map { it.kind })
        assertEquals(80_000, flow.sources.last().minor)
    }

    @Test
    fun `percents round half up, and a change needs a month before`() {
        assertEquals(33, MoneyFlow.percentOf(1, 3))
        assertEquals(67, MoneyFlow.percentOf(2, 3))
        assertEquals(0, MoneyFlow.percentOf(5, 0))
        assertEquals(25, MoneyFlow.change(125, 100))
        assertEquals(-50, MoneyFlow.change(50, 100))
        assertEquals(null, MoneyFlow.change(50, null))
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
