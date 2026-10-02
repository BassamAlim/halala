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
}
