package bassamalim.halala.features.onboarding

import bassamalim.halala.core.data.dataSources.room.relations.UnroutedGroup
import bassamalim.halala.core.sms.SmsIngest
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingDomainTest {

    private fun found(vararg groups: Triple<String, String, Int>) =
        OnboardingDomain.found(groups.map { UnroutedGroup(it.first, it.second, it.third) })
            .map { Triple(it.bank, it.refs, it.messages) }

    @Test
    fun `a card quoted beside its account is the same account, led by the account's digits`() {
        assertEquals(
            listOf(
                Triple("Al Rajhi", listOf("1111", "9001"), 1470),
                Triple("Al Rajhi", listOf("3333"), 81)
            ),
            found(
                Triple("AlRajhiBank", "9001", 1052),
                Triple("AlRajhiBank", "1111,9001", 418),
                Triple("AlRajhiBank", "3333", 81)
            )
        )
    }

    @Test
    fun `three digits are the tail of the full number seen at that bank`() {
        // SNB writes ••4444 as "444*690" and ••5555 as "555*690": 690 ends neither, so it is noise.
        assertEquals(
            listOf(
                Triple("SNB", listOf("4444", "9002"), 904),
                Triple("SNB", listOf("9005"), 209),
                Triple("SNB", listOf("5555"), 83)
            ),
            found(
                Triple("SNB-AlAhli", "444,690,9002", 482),
                Triple("SNB-AlAhli", "444,690", 200),
                Triple("SNB-AlAhli", "4444", 222),
                Triple("SNB-AlAhli", "9005", 209),
                Triple("SNB-AlAhli", "555,690", 71),
                Triple("SNB-AlAhli", "5555", 12)
            )
        )
    }

    @Test
    fun `messages with no digits count with the bank's busiest account, or stand alone at a wallet`() {
        assertEquals(
            listOf(Triple("STC Bank", listOf("6666"), 222), Triple("STC Bank", listOf("7777"), 9)),
            found(Triple("STC Bank", "6666", 12), Triple("STC Bank", "7777", 9), Triple("STCPAY", "", 210))
        )
        assertEquals(
            listOf(Triple("Barq", emptyList<String>(), 5)),
            found(Triple("barq app", "", 5))
        )
    }

    @Test
    fun `rows given the same name at one bank become one account, and a blank row is shelved`() {
        val account = FoundAccount("SNB", listOf("4444", "9002"), 904)
        val card = FoundAccount("SNB", listOf("9005"), 209)
        val quiet = FoundAccount("SNB", emptyList(), 30)
        val other = FoundAccount("SNB", listOf("5555"), 83)
        val elsewhere = FoundAccount("D360", listOf("7777"), 80)

        assertEquals(
            listOf(
                OnboardingDomain.NewAccount("SNB", "Main", "4444", listOf("9002", "9005", SmsIngest.NO_DIGITS)),
                OnboardingDomain.NewAccount("SNB", "••5555", "5555", emptyList(), shelved = true),
                OnboardingDomain.NewAccount("D360", "Main", "7777", emptyList())
            ),
            OnboardingDomain.plan(
                listOf(account to "Main", card to " main ", quiet to "Main", other to "  ", elsewhere to "Main")
            )
        )
    }

    @Test
    fun `an account whose messages quote no digits has no last four`() {
        assertEquals(
            listOf(OnboardingDomain.NewAccount("Barq", "Barq", null, listOf(SmsIngest.NO_DIGITS))),
            OnboardingDomain.plan(listOf(FoundAccount("Barq", emptyList(), 5) to "Barq"))
        )
    }
}
