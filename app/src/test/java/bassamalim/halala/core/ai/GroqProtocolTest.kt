package bassamalim.halala.core.ai

import bassamalim.halala.core.data.repositories.IdentifiedAs
import bassamalim.halala.core.enums.BusinessType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroqProtocolTest {

    private fun JsonObject.obj(key: String) = getValue(key).jsonObject

    @Test
    fun `the request holds the names and nothing else of yours`() {
        val request = Json.parseToJsonElement(GroqProtocol.request(listOf("PANDA 1042 RIYADH", "ALMTRF TRDG EST"))).jsonObject

        assertEquals(GroqProtocol.MODEL, request.getValue("model").jsonPrimitive.content)
        val messages = request.getValue("messages").jsonArray
        assertEquals(
            """{"items":[{"id":"1","name":"PANDA 1042 RIYADH"},{"id":"2","name":"ALMTRF TRDG EST"}]}""",
            messages[1].jsonObject.getValue("content").jsonPrimitive.content
        )
        // The instructions are the same for everyone: no categories of yours, no amounts.
        assertEquals(GroqProtocol.request(emptyList()).let { Json.parseToJsonElement(it).jsonObject }
            .getValue("messages").jsonArray[0], messages[0])
    }

    @Test
    fun `the answer must be one of the business types, under a strict schema`() {
        val format = Json.parseToJsonElement(GroqProtocol.request(listOf("X"))).jsonObject.obj("response_format")
        val schema = format.obj("json_schema")
        assertTrue(schema.getValue("strict").jsonPrimitive.boolean)

        val item = schema.obj("schema").obj("properties").obj("items").obj("items")
        val types = item.obj("properties").obj("businessType").getValue("enum").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(BusinessType.entries.map { it.name }, types)
        assertEquals(4, item.getValue("required").jsonArray.size)
        assertEquals(false, item.getValue("additionalProperties").jsonPrimitive.boolean)
    }

    @Test
    fun `answers find their names by id, and odd ones are made safe`() {
        val content = """{"items":[
            {"id":"2","name":"Al-Mutref Trading","businessType":"HARDWARE","confidence":91},
            {"id":"1","name":"Panda","businessType":"SUPERMARKET","confidence":140},
            {"id":"3","name":"?","businessType":"GROCERIES","confidence":-5}
        ]}"""
        val body = """{"id":"x","choices":[{"index":0,"message":{"role":"assistant","content":${Json.encodeToString(content)}}}]}"""

        val answers = GroqProtocol.parse(body, count = 4)

        assertEquals(IdentifiedAs("Panda", BusinessType.SUPERMARKET, 100), answers[0])
        assertEquals(IdentifiedAs("Al-Mutref Trading", BusinessType.HARDWARE, 91), answers[1])
        assertEquals(IdentifiedAs("?", BusinessType.UNKNOWN, 0), answers[2])
        assertNull(answers[3])
    }
}
