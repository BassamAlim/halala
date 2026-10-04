package bassamalim.halala.core.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebsitesProtocolTest {

    @Test
    fun `the request holds the names, numbered, and nothing else`() {
        val request = Json.parseToJsonElement(GroqProtocol.websitesRequest(listOf("PANDA 1042", "JAHEZ"))).jsonObject
        val user = Json.parseToJsonElement(
            request.getValue("messages").jsonArray[1].jsonObject.getValue("content").jsonPrimitive.content
        ).jsonObject
        assertEquals(
            listOf("1" to "PANDA 1042", "2" to "JAHEZ"),
            user.getValue("items").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content to it.jsonObject.getValue("name").jsonPrimitive.content }
        )
    }

    @Test
    fun `answers become bare domains, and anything else none`() {
        val content = """{"items":[
            {"id":"1","website":"https://www.Panda.com.sa/ar/home"},
            {"id":"2","website":""},
            {"id":"3","website":"not a site"}
        ]}"""
        val body = """{"choices":[{"message":{"content":${Json.encodeToString(content)}}}]}"""
        assertEquals(listOf("panda.com.sa", null, null, null), GroqProtocol.parseWebsites(body, 4))
    }

    @Test
    fun `a domain is a host and nothing more`() {
        assertEquals("jahez.net", GroqProtocol.domainOf("jahez.net"))
        assertEquals("hungerstation.com", GroqProtocol.domainOf("HTTP://hungerstation.com?x=1"))
        assertNull(GroqProtocol.domainOf("localhost"))
        assertNull(GroqProtocol.domainOf("panda .com"))
    }
}
