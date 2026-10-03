package bassamalim.halala.core.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PeopleMatchProtocolTest {

    private fun completion(content: String) = buildJsonObject {
        putJsonArray("choices") { addJsonObject { putJsonObject("message") { put("content", JsonPrimitive(content)) } } }
    }.toString()

    @Test
    fun `the request holds the names and nothing else of yours`() {
        val request = Json.parseToJsonElement(PeopleMatchProtocol.request(listOf(listOf("AHMED ALI", "Ahmed A"), listOf("أحمد علي")))).jsonObject

        assertEquals(
            """{"items":[{"id":"1","names":["AHMED ALI","Ahmed A"]},{"id":"2","names":["أحمد علي"]}]}""",
            request.getValue("messages").jsonArray[1].jsonObject.getValue("content").jsonPrimitive.content
        )
    }

    @Test
    fun `groups become pairs, and ids it wasn't given are dropped`() {
        val body = completion("""{"groups":[{"ids":["3","1","2"]},{"ids":["4","9"]},{"ids":["x"]}]}""")

        assertEquals(setOf(0 to 1, 0 to 2, 1 to 2), PeopleMatchProtocol.parse(body, count = 4))
    }
}
