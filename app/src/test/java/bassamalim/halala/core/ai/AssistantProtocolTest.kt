package bassamalim.halala.core.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AssistantProtocolTest {

    private val today = LocalDate.of(2026, 10, 2)

    private fun body(content: String) =
        """{"choices":[{"message":{"role":"assistant","content":${Json.encodeToString(content)}}}]}"""

    @Test
    fun `the request holds the question and today, nothing from the ledger`() {
        val request = Json.parseToJsonElement(AssistantProtocol.request("How much on coffee since June?", today)).jsonObject
        val messages = request.getValue("messages").jsonArray
        assertEquals(2, messages.size)
        assertEquals("How much on coffee since June?", messages[1].jsonObject.getValue("content").jsonPrimitive.content)
        // The instructions are the same for every question and person, but for the date.
        val other = Json.parseToJsonElement(AssistantProtocol.request("x", today)).jsonObject.getValue("messages").jsonArray[0]
        assertEquals(other, messages[0])
        assertTrue(messages[0].jsonObject.getValue("content").jsonPrimitive.content.endsWith("Today is 2026-10-02, a Friday."))
        assertTrue(request.getValue("response_format").jsonObject.getValue("json_schema").jsonObject.getValue("strict").jsonPrimitive.boolean)
    }

    @Test
    fun `a refused query goes back with what SQLite said`() {
        val failed = FailedQuery("SELECT total FROM tx", "no such column: total")
        val messages = Json.parseToJsonElement(AssistantProtocol.request("q", today, failed)).jsonObject.getValue("messages").jsonArray
        assertEquals(4, messages.size)
        assertEquals("""{"sql":"SELECT total FROM tx"}""", messages[2].jsonObject.getValue("content").jsonPrimitive.content)
        assertTrue("no such column: total" in messages[3].jsonObject.getValue("content").jsonPrimitive.content)
    }

    @Test
    fun `the query is read, and none is null`() {
        assertEquals("SELECT 1", AssistantProtocol.parse(body("""{"sql":" SELECT 1 "}""")))
        assertNull(AssistantProtocol.parse(body("""{"sql":null}""")))
        assertNull(AssistantProtocol.parse(body("""{"sql":""}""")))
    }
}
