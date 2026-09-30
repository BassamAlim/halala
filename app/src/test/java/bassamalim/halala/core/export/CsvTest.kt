package bassamalim.halala.core.export

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTest {

    @Test
    fun `plain fields are written as they are, rows end in CRLF`() {
        assertEquals("a,b,214.50\r\n", Csv.row(listOf("a", "b", "214.50")))
    }

    @Test
    fun `commas, quotes and line breaks are quoted, quotes doubled`() {
        assertEquals("\"Panda, Olaya\"\r\n", Csv.row(listOf("Panda, Olaya")))
        assertEquals("\"say \"\"hi\"\"\"\r\n", Csv.row(listOf("say \"hi\"")))
        assertEquals("\"two\nlines\"\r\n", Csv.row(listOf("two\nlines")))
    }

    @Test
    fun `text that a spreadsheet would run as a formula is defused`() {
        assertEquals("'=HYPERLINK(\"x\")", Csv.text("=HYPERLINK(\"x\")"))
        assertEquals("'+1", Csv.text("+1"))
        assertEquals("'-1", Csv.text("-1"))
        assertEquals("'@SUM", Csv.text("@SUM"))
        assertEquals("Panda", Csv.text("Panda"))
        assertEquals("", Csv.text(null))
    }

    @Test
    fun `numbers are left alone so a debit stays negative`() {
        assertEquals("-214.50", Csv.number("-214.50"))
    }

    @Test
    fun `Arabic text passes through untouched`() {
        assertEquals("بنده\r\n", Csv.row(listOf(Csv.text("بنده"))))
    }
}
