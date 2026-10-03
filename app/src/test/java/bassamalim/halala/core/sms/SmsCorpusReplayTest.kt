package bassamalim.halala.core.sms

import bassamalim.halala.core.data.dataSources.definitions.DefinitionsFile
import androidx.test.core.app.ApplicationProvider
import bassamalim.halala.core.data.TEST_CLOCK
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.InstitutionsRepository
import bassamalim.halala.core.data.repositories.SavingsRepository
import bassamalim.halala.core.data.repositories.SmsRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.data.testDatabase
import bassamalim.halala.core.models.AccountDraft
import bassamalim.halala.features.onboarding.OnboardingDomain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * Replays a real inbox (see [SmsCorpusTest]) through the whole pipeline the way onboarding
 * does, naming every found account, and writes what the ledger ends up holding next to the
 * corpus. Skipped unless HALALA_SMS_CORPUS is set.
 */
@RunWith(RobolectricTestRunner::class)
class SmsCorpusReplayTest {

    @Test
    fun `replay a real inbox`() = runTest(timeout = 30.minutes) {
        val path = System.getenv("HALALA_SMS_CORPUS")
        assumeTrue(path != null)
        val corpus = File(path!!)

        val db = testDatabase()
        val accounts = AccountsRepository(db.accountsDao(), TEST_CLOCK)
        val sms = SmsRepository(db.smsDao())
        val institutions = InstitutionsRepository(db.institutionsDao())
        val ingest = SmsIngest(sms, TransactionsRepository(db.transactionsDao(), db.accountsDao(), TEST_CLOCK), accounts, institutions, ClassificationRepository(db.classificationDao(), db.merchantsDao(), db.transactionsDao(), db.peopleDao(), DefinitionsFile(ApplicationProvider.getApplicationContext()), TEST_CLOCK), SavingsRepository(db.savingsDao(), db.accountsDao(), db.transactionsDao(), db.goalsDao(), TransactionsRepository(db.transactionsDao(), db.accountsDao(), TEST_CLOCK), TEST_CLOCK), TEST_CLOCK)

        for (row in corpus.readLines()) {
            val (sender, millis, escaped) = row.split('\t', limit = 3)
            ingest.store(sender, escaped.replace("\\n", "\n"), Instant.ofEpochMilli(millis.toLong()))
        }
        ingest.processPending()

        val out = StringBuilder()
        val found = OnboardingDomain.found(sms.observeUnrouted().first())
        out.appendLine("## found"); found.forEach { out.appendLine("${it.bank} ${it.refs} ${it.messages}") }

        val banks = institutions.getAll().associate { it.name to it.id }
        // Every row named apart, except the one-account wallets, which get one name.
        val wallets = setOf("STC Bank", "Barq", "D360")
        val names = found.map {
            val lead = if (it.bank in wallets) found.first { other -> other.bank == it.bank } else it
            it to "${it.bank} ${lead.refs.firstOrNull().orEmpty()}"
        }
        for (account in OnboardingDomain.plan(names)) {
            val id = accounts.create(AccountDraft(banks[account.bank], account.name, OnboardingDomain.typeFor(account.bank), account.last4, "SAR", 0))
            account.otherRefs.forEach { sms.addRef(banks.getValue(account.bank), it, id) }
        }
        ingest.processPending(retry = true)

        fun query(title: String, sql: String) {
            out.appendLine("## $title")
            db.query(sql, null).use { c ->
                while (c.moveToNext()) out.appendLine((0 until c.columnCount).joinToString(" | ") { c.getString(it) ?: "null" })
            }
        }
        out.appendLine("## balances")
        accounts.getAllWithBalance().forEach { out.appendLine("${it.account.nickname} | ${it.balanceMinor / 100.0} | ${it.transactionCount}") }
        query("statuses", "SELECT status, COUNT(*) FROM raw_messages GROUP BY status")
        query("still unrouted", "SELECT sender, unroutedRefs, COUNT(*) FROM raw_messages WHERE status='UNROUTED' GROUP BY 1,2 ORDER BY 3 DESC LIMIT 20")
        query("refs", "SELECT a.nickname, r.ref FROM account_refs r JOIN accounts a ON a.id = r.accountId")
        query("pairs", "SELECT COUNT(*), matchConfidence FROM internal_transfers GROUP BY 2")
        query("by account, kind, direction (unpaired only)", """
            SELECT a.nickname, t.kind, t.direction, COUNT(*), SUM(t.amountMinor)/100.0 FROM transactions t JOIN accounts a ON a.id=t.accountId
            WHERE t.id NOT IN (SELECT outTransactionId FROM internal_transfers UNION SELECT inTransactionId FROM internal_transfers)
            GROUP BY 1,2,3 ORDER BY 1,5 DESC""")
        query("by account, paired", """
            SELECT a.nickname, t.direction, COUNT(*), SUM(t.amountMinor)/100.0 FROM transactions t JOIN accounts a ON a.id=t.accountId
            WHERE t.id IN (SELECT outTransactionId FROM internal_transfers UNION SELECT inTransactionId FROM internal_transfers)
            GROUP BY 1,2 ORDER BY 1""")
        query("last 3 months in/out per month (unpaired, counted kinds)", """
            SELECT strftime('%Y-%m', t.occurredAt/1000, 'unixepoch'), t.direction, COUNT(*), SUM(t.amountMinor)/100.0 FROM transactions t
            WHERE t.id NOT IN (SELECT outTransactionId FROM internal_transfers UNION SELECT inTransactionId FROM internal_transfers)
            AND t.kind NOT IN ('INTERNAL_TRANSFER','ATM_WITHDRAWAL','CASH_DEPOSIT','ADJUSTMENT') AND t.occurredAt > 1782000000000
            GROUP BY 1,2 ORDER BY 1""")
        query("biggest unpaired transfers since July", """
            SELECT strftime('%Y-%m-%d %H:%M', t.occurredAt/1000, 'unixepoch'), a.nickname, t.kind, t.direction, t.amountMinor/100.0, t.title FROM transactions t JOIN accounts a ON a.id=t.accountId
            WHERE t.id NOT IN (SELECT outTransactionId FROM internal_transfers UNION SELECT inTransactionId FROM internal_transfers)
            AND t.kind IN ('TRANSFER_IN','TRANSFER_OUT','INTERNAL_TRANSFER') AND t.occurredAt > 1782000000000 ORDER BY t.amountMinor DESC LIMIT 40""")
        query("lowest end-of-day and lowest running balance per account", """
            SELECT a.nickname, MIN(d.run)/100.0, (SELECT MIN(r.run)/100.0 FROM (
                SELECT SUM(CASE WHEN t.direction='CREDIT' THEN t.amountMinor ELSE -t.amountMinor END) OVER (ORDER BY t.occurredAt, t.id) AS run
                FROM transactions t WHERE t.accountId = a.id) r)
            FROM accounts a JOIN (
                SELECT accountId, SUM(net) OVER (PARTITION BY accountId ORDER BY day) AS run FROM (
                    SELECT accountId, occurredAt/86400000 AS day,
                        SUM(CASE WHEN direction='CREDIT' THEN amountMinor ELSE -amountMinor END) AS net
                    FROM transactions GROUP BY accountId, day)) d ON d.accountId = a.id
            GROUP BY a.id""")
        query("dropped or held", "SELECT status, sender, replace(body, char(10), ' / ') FROM raw_messages WHERE status IN ('DUPLICATE','FOREIGN','UNRECOGNISED') ORDER BY status, sender, receivedAt")
        File(corpus.parentFile, "replay.txt").writeText(out.toString())
        db.close()
    }
}
