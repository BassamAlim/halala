package bassamalim.halala.core.ai

import bassamalim.halala.core.enums.BusinessType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebSearchTest {

    @Test
    fun `a query is the name and the country, in the name's own language`() {
        assertEquals("ALMTRF TRDG EST 0412 Saudi Arabia", TavilyProtocol.query("ALMTRF TRDG EST 0412"))
        assertEquals("مؤسسة المطرف السعودية", TavilyProtocol.query("مؤسسة المطرف"))
        val request = Json.parseToJsonElement(TavilyProtocol.request("Panda Saudi Arabia")).jsonObject
        assertEquals(setOf("query", "search_depth", "max_results"), request.keys)
        assertEquals("basic", request.getValue("search_depth").jsonPrimitive.content)
    }

    @Test
    fun `results are read, and anything that isn't a web page is dropped`() {
        val body = """{"query":"x","results":[
            {"title":"Al-Mutref Trading","url":"https://mutref.sa","content":"Building materials in Riyadh","score":0.9},
            {"title":"Odd","url":"javascript:alert(1)","content":""}
        ],"response_time":1.2}"""
        assertEquals(listOf(WebResult("Al-Mutref Trading", "https://mutref.sa", "Building materials in Riyadh")), TavilyProtocol.parse(body))
    }

    @Test
    fun `asking again sends the name and the results, and the answer names the page it rests on`() {
        val results = listOf(WebResult("Al-Mutref Trading", "https://mutref.sa", "x".repeat(5_000)))
        val request = Json.parseToJsonElement(GroqProtocol.requestWithResults("ALMTRF TRDG EST", results)).jsonObject
        val user = Json.parseToJsonElement(
            request.getValue("messages").jsonArray[1].jsonObject.getValue("content").jsonPrimitive.content
        ).jsonObject
        assertEquals("ALMTRF TRDG EST", user.getValue("name").jsonPrimitive.content)
        // Each page's text is cut short.
        assertEquals(600, user.getValue("results").jsonArray[0].jsonObject.getValue("text").jsonPrimitive.content.length)

        fun reply(content: String) = """{"choices":[{"message":{"content":${Json.encodeToString(content)}}}]}"""
        val (answer, source) = GroqProtocol.parseWithSource(
            reply("""{"name":"Al-Mutref","businessType":"HARDWARE","confidence":91,"source":1}"""), results.size
        )
        assertEquals(BusinessType.HARDWARE, answer.type)
        assertEquals(91, answer.confidence)
        assertEquals(0, source)
        // No page, or one it wasn't sent, is no source.
        assertNull(GroqProtocol.parseWithSource(reply("""{"name":"X","businessType":"UNKNOWN","confidence":0,"source":0}"""), 1).second)
        assertNull(GroqProtocol.parseWithSource(reply("""{"name":"X","businessType":"CAFE","confidence":70,"source":4}"""), 1).second)
    }
}
