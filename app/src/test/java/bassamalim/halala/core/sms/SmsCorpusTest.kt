package bassamalim.halala.core.sms

import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Runs the parsers over a dump of a real inbox, which never enters the repo: set
 * HALALA_SMS_CORPUS to a TSV of sender, epoch millis and body (newlines as \n), and the report
 * lands next to it. Skipped everywhere else, CI included.
 */
class SmsCorpusTest {

    @Test
    fun `report on a real inbox`() {
        val path = System.getenv("HALALA_SMS_CORPUS")
        assumeTrue(path != null)
        val corpus = File(path!!)

        val report = corpus.readLines().map { row ->
            val (sender, _, escaped) = row.split('\t', limit = 3)
            val body = escaped.replace("\\n", "\n").replace("\\\\", "\\")
            val parsed = SmsParser.bankFor(sender)?.let { SmsParser.parse(it, body) }
            listOf(sender, parsed.toString(), escaped).joinToString("\t")
        }
        File(corpus.parentFile, "report.tsv").writeText(report.joinToString("\n"))
    }
}
