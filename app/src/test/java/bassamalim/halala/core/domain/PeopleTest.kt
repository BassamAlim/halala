package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.daos.PERSON_KINDS
import bassamalim.halala.core.enums.Direction
import org.junit.Assert.assertEquals
import org.junit.Test

class PeopleTest {

    @Test
    fun `a new person is named as the bank wrote them, tidied`() {
        assertEquals("Ahmed Ali Alqahtani", People.nameOf("AHMED ALI ALQAHTANI"))
        assertEquals("Ahmed Ali", People.nameOf("7700;AHMED ALI"))
        assertEquals("Khalid bin Saad", People.nameOf("Khalid bin Saad"))
        assertEquals("محمد العتيبي", People.nameOf("محمد العتيبي"))
    }

    @Test
    fun `the kinds agree with the SQL that finds them`() {
        assertEquals(People.KINDS.map { "'${it.name}'" }.toSet(), PERSON_KINDS.split(", ").toSet())
    }

    @Test
    fun `flow sums each way, and net is what came back less what went`() {
        val flow = People.flowOf(listOf(Direction.DEBIT to 650_000, Direction.CREDIT to 550_000, Direction.DEBIT to 0))
        assertEquals(650_000, flow.sentMinor)
        assertEquals(550_000, flow.receivedMinor)
        assertEquals(-100_000, flow.netMinor)
    }

    private fun known(id: Long, vararg names: String) = People.Known(id, "u$id", names.map(Merchants::key))

    @Test
    fun `spacing and the ways an Arabic letter is written never make another name`() {
        assertEquals(People.canonical(Merchants.key("ABDUL RAHMAN ALHARBI")), People.canonical(Merchants.key("Abdulrahman Al Harbi")))
        assertEquals(People.canonical(Merchants.key("أحمد العتيبى")), People.canonical(Merchants.key("احمد العتيبي")))
        assertEquals(People.canonical(Merchants.key("سارة")), People.canonical(Merchants.key("ساره")))
    }

    @Test
    fun `people spelled the same are suggested, siblings are not`() {
        val people = listOf(
            known(1, "ABDUL RAHMAN ALHARBI"),
            known(2, "Abdulrahman Alharbi"),
            known(3, "Ahmed Mohammed Alharbi"),
            known(4, "Hamad Mohammed Alharbi")
        )
        val found = People.suggest(people, emptyMap(), emptySet(), emptySet())

        assertEquals(listOf(People.Suggestion(1, 2, "u1|u2", People.MergeReason.SPELLING)), found)
    }

    @Test
    fun `shared account digits and the AI suggest too, the surest reason first, and no means no`() {
        val people = listOf(known(1, "AHMED ALI"), known(2, "أحمد علي"), known(3, "A. Ali"), known(4, "Omar Saleh"))
        val refs = mapOf(1L to setOf("7700"), 2L to setOf("7700", "1234"), 4L to setOf("9400"))
        val ai = setOf(People.pairKey("u3", "u1"), People.pairKey("u2", "u3"))

        val found = People.suggest(people, refs, ai, dismissed = setOf("u2|u3"))

        assertEquals(
            listOf(
                People.Suggestion(1, 2, "u1|u2", People.MergeReason.ACCOUNT, ref = "7700"),
                People.Suggestion(1, 3, "u1|u3", People.MergeReason.AI)
            ),
            found
        )
    }
}
