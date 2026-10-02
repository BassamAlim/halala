package bassamalim.halala.core.ai

import bassamalim.halala.core.enums.BusinessType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
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
        assertTrue(messages[0].jsonObject.getValue("content").jsonPrimitive.content.endsWith("Today is 2026-10-02."))

        val schema = request.getValue("response_format").jsonObject.getValue("json_schema").jsonObject
        assertTrue(schema.getValue("strict").jsonPrimitive.boolean)
        val tools = schema.getValue("schema").jsonObject.getValue("properties").jsonObject.getValue("tool").jsonObject
            .getValue("enum").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(AskTool.entries.map { it.name }, tools)
    }

    @Test
    fun `a query is read, and what can't be read is left out`() {
        val ask = AssistantProtocol.parse(
            body("""{"tool":"SPENDING","topic":" coffee ","businessType":"CAFE","from":"2026-06-01","to":null,"amount":null,"on":"soon","monthly":null}""")
        )
        assertEquals(Ask(AskTool.SPENDING, topic = "coffee", businessType = BusinessType.CAFE, from = LocalDate.of(2026, 6, 1)), ask)

        val afford = AssistantProtocol.parse(
            body("""{"tool":"AFFORD","topic":null,"businessType":"UNKNOWN","from":null,"to":null,"amount":"12000","on":"2026-11-01","monthly":false}""")
        )
        assertEquals(BigDecimal("12000"), afford.amount)
        assertEquals(LocalDate.of(2026, 11, 1), afford.on)
        assertNull(afford.businessType)

        val odd = AssistantProtocol.parse(
            body("""{"tool":"WEATHER","topic":"","businessType":null,"from":null,"to":null,"amount":"-5","on":null,"monthly":true}""")
        )
        assertEquals(AskTool.UNSUPPORTED, odd.tool)
        assertNull(odd.topic)
        assertNull(odd.amount)
    }
}
