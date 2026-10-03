package bassamalim.halala.core.ai

import bassamalim.halala.core.data.repositories.IdentifiedAs
import bassamalim.halala.core.enums.BusinessType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * What Halala says to Groq and reads back, as pure functions. The request holds merchants'
 * names and nothing else; the answer must follow a strict JSON schema (constrained decoding), so
 * a business type is always one of [BusinessType] and never a category of yours.
 */
object GroqProtocol {

    const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"

    /** The spec's model: strict JSON schemas, and no reasoning needed to recall a shop. */
    const val MODEL = "qwen/qwen3.8-27b"

    /** The chat request asking what each of [names] is, numbered from 1 so answers find their name. */
    fun request(names: List<String>): String {
        val items = buildJsonArray {
            names.forEachIndexed { index, name ->
                addJsonObject {
                    put("id", (index + 1).toString())
                    put("name", name)
                }
            }
        }
        return buildJsonObject {
            put("model", MODEL)
            put("temperature", 0)
            put("reasoning_effort", "none")
            put("max_completion_tokens", MAX_TOKENS)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", INSTRUCTIONS)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", buildJsonObject { put("items", items) }.toString())
                }
            }
            putJsonObject("response_format") {
                put("type", "json_schema")
                putJsonObject("json_schema") {
                    put("name", "merchants")
                    put("strict", true)
                    put("schema", SCHEMA)
                }
            }
        }.toString()
    }

    /**
     * The answer for each of [count] names, in order; null for one it left out. A confidence
     * outside 0–100 is clamped; a business type it shouldn't have given reads as unknown.
     */
    fun parse(body: String, count: Int): List<IdentifiedAs?> {
        val content = contentOf(body)
        val answers = json.decodeFromString<Answers>(content).items.associateBy { it.id.trim() }

        return (1..count).map { id ->
            answers[id.toString()]?.let { answer ->
                IdentifiedAs(
                    name = answer.name,
                    type = BusinessType.entries.firstOrNull { it.name == answer.businessType } ?: BusinessType.UNKNOWN,
                    confidence = answer.confidence.coerceIn(0, 100)
                )
            }
        }
    }

    /**
     * Asking again about one [name] the first answer was unsure of, with what a web search found
     * for it ([results], numbered from 1): public pages, nothing of yours.
     */
    fun requestWithResults(name: String, results: List<WebResult>): String = buildJsonObject {
        put("model", MODEL)
        put("temperature", 0)
        put("reasoning_effort", "none")
        put("max_completion_tokens", MAX_TOKENS)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "system")
                put("content", INSTRUCTIONS_WITH_RESULTS)
            }
            addJsonObject {
                put("role", "user")
                put("content", buildJsonObject {
                    put("name", name)
                    putJsonArray("results") {
                        results.forEachIndexed { index, result ->
                            addJsonObject {
                                put("source", index + 1)
                                put("title", result.title)
                                put("url", result.url)
                                put("text", result.content.take(MAX_RESULT_CHARS))
                            }
                        }
                    }
                }.toString())
            }
        }
        putJsonObject("response_format") {
            put("type", "json_schema")
            putJsonObject("json_schema") {
                put("name", "merchant")
                put("strict", true)
                put("schema", SCHEMA_WITH_SOURCE)
            }
        }
    }.toString()

    /**
     * The answer to [requestWithResults], and the result (by its index in the list sent) it rests
     * on; null when it named none, or one that wasn't sent.
     */
    fun parseWithSource(body: String, resultCount: Int): Pair<IdentifiedAs, Int?> {
        val answer = json.decodeFromString<SourcedAnswer>(contentOf(body))
        return IdentifiedAs(
            name = answer.name,
            type = BusinessType.entries.firstOrNull { it.name == answer.businessType } ?: BusinessType.UNKNOWN,
            confidence = answer.confidence.coerceIn(0, 100)
        ) to (answer.source - 1).takeIf { it in 0 until resultCount }
    }

    /** Asking each of [names]' own website, numbered from 1 as [request] numbers them: for its logo. */
    fun websitesRequest(names: List<String>): String = buildJsonObject {
        put("model", MODEL)
        put("temperature", 0)
        put("reasoning_effort", "none")
        put("max_completion_tokens", MAX_TOKENS)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "system")
                put("content", WEBSITE_INSTRUCTIONS)
            }
            addJsonObject {
                put("role", "user")
                put("content", buildJsonObject {
                    putJsonArray("items") {
                        names.forEachIndexed { index, name ->
                            addJsonObject {
                                put("id", (index + 1).toString())
                                put("name", name)
                            }
                        }
                    }
                }.toString())
            }
        }
        putJsonObject("response_format") {
            put("type", "json_schema")
            putJsonObject("json_schema") {
                put("name", "websites")
                put("strict", true)
                put("schema", WEBSITE_SCHEMA)
            }
        }
    }.toString()

    /** Each name's website as a bare domain ("panda.com.sa"), in order; null where none was given. */
    fun parseWebsites(body: String, count: Int): List<String?> {
        val answers = json.decodeFromString<Websites>(contentOf(body)).items.associateBy { it.id.trim() }
        return (1..count).map { id -> answers[id.toString()]?.website?.let(::domainOf) }
    }

    /** "https://www.Panda.com.sa/ar" → "panda.com.sa"; null for anything that isn't a domain. */
    fun domainOf(text: String): String? {
        val host = text.trim().lowercase()
            .substringAfter("://")
            .substringBefore('/')
            .substringBefore('?')
            .removePrefix("www.")
        return host.takeIf { DOMAIN.matches(it) }
    }

    private val DOMAIN = Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}$")

    /** Enough for a batch of 40 short answers, with room to spare. */
    private const val MAX_TOKENS = 4096

    /** Each result's text, cut short: enough to tell what a business is. */
    private const val MAX_RESULT_CHARS = 600

    private val json = Json { ignoreUnknownKeys = true }

    /** The model's message in a chat completion [body]. */
    fun contentOf(body: String): String = json.decodeFromString<Completion>(body).choices.firstOrNull()?.message?.content
        ?: throw IllegalArgumentException("No answer in the response.")

    @Serializable
    private data class Completion(val choices: List<Choice> = emptyList())

    @Serializable
    private data class Choice(val message: Message? = null)

    @Serializable
    private data class Message(val content: String? = null)

    @Serializable
    private data class Answers(val items: List<Answer> = emptyList())

    @Serializable
    private data class Answer(val id: String, val name: String, val businessType: String, val confidence: Int)

    @Serializable
    private data class Websites(val items: List<Website> = emptyList())

    @Serializable
    private data class Website(val id: String, val website: String)

    private val WEBSITE_SCHEMA: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("items") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("id") { put("type", "string") }
                        putJsonObject("website") { put("type", "string") }
                    }
                    put("required", JsonArray(listOf("id", "website").map(::JsonPrimitive)))
                    put("additionalProperties", false)
                }
            }
        }
        put("required", JsonArray(listOf(JsonPrimitive("items"))))
        put("additionalProperties", false)
    }

    private val WEBSITE_INSTRUCTIONS = """
        Each item is how a bank in Saudi Arabia wrote a merchant's name on a card payment or bill.
        For each, answer with its id and website: the business's own official website as a bare domain, such as "panda.com.sa".
        Prefer its Saudi site when it has one. Use an empty string when you don't know it for certain; never guess, and never give a directory, map or social media site.
    """.trimIndent()

    @Serializable
    private data class SourcedAnswer(val name: String, val businessType: String, val confidence: Int, val source: Int)

    /** One business, and the result it rests on (0 for none). */
    private val SCHEMA_WITH_SOURCE: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("name") { put("type", "string") }
            putJsonObject("businessType") {
                put("type", "string")
                putJsonArray("enum") { BusinessType.entries.forEach { add(it.name) } }
            }
            putJsonObject("confidence") { put("type", "integer") }
            putJsonObject("source") { put("type", "integer") }
        }
        put("required", JsonArray(listOf("name", "businessType", "confidence", "source").map(::JsonPrimitive)))
        put("additionalProperties", false)
    }

    /** Strict mode: every field required, no others. */
    private val SCHEMA: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("items") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("id") { put("type", "string") }
                        putJsonObject("name") { put("type", "string") }
                        putJsonObject("businessType") {
                            put("type", "string")
                            putJsonArray("enum") { BusinessType.entries.forEach { add(it.name) } }
                        }
                        putJsonObject("confidence") { put("type", "integer") }
                    }
                    put("required", JsonArray(listOf("id", "name", "businessType", "confidence").map(::JsonPrimitive)))
                    put("additionalProperties", false)
                }
            }
        }
        put("required", JsonArray(listOf(JsonPrimitive("items"))))
        put("additionalProperties", false)
    }

    /** What each business type covers, so the model can tell a bakery from a supermarket. */
    val MEANINGS = mapOf(
        BusinessType.SUPERMARKET to "supermarkets and hypermarkets",
        BusinessType.CONVENIENCE_STORE to "corner shops (baqala), mini markets",
        BusinessType.BAKERY to "bakeries and sweet shops",
        BusinessType.RESTAURANT to "sit-down restaurants",
        BusinessType.FAST_FOOD to "fast food and takeaway chains",
        BusinessType.CAFE to "coffee shops and cafés",
        BusinessType.FOOD_DELIVERY to "food and grocery delivery apps",
        BusinessType.FUEL_STATION to "petrol stations",
        BusinessType.CAR_SERVICE to "car workshops, car washes, tyres, oil change",
        BusinessType.PARKING to "parking and tolls",
        BusinessType.RIDE_HAILING to "taxis and ride-hailing apps",
        BusinessType.PUBLIC_TRANSPORT to "buses, metro, trains",
        BusinessType.CAR_RENTAL to "car rental",
        BusinessType.AIRLINE to "airlines",
        BusinessType.HOTEL to "hotels and stays, booking sites for them",
        BusinessType.TRAVEL_AGENCY to "travel agencies",
        BusinessType.PHARMACY to "pharmacies",
        BusinessType.CLINIC to "hospitals, clinics, dentists, labs",
        BusinessType.OPTICIAN to "opticians",
        BusinessType.GYM to "gyms and sports clubs",
        BusinessType.TELECOM to "mobile, internet and phone companies",
        BusinessType.UTILITY to "electricity, water, gas",
        BusinessType.GOVERNMENT to "government services and fees",
        BusinessType.INSURANCE to "insurance",
        BusinessType.EDUCATION to "schools, universities, courses",
        BusinessType.BOOKSTORE to "bookshops and stationery",
        BusinessType.ELECTRONICS to "electronics and phone shops",
        BusinessType.CLOTHING to "clothes and shoes",
        BusinessType.BEAUTY to "cosmetics and perfume",
        BusinessType.SALON to "salons, barbers, spas",
        BusinessType.JEWELRY to "jewellery and gold shops",
        BusinessType.GIFTS to "gifts, flowers, chocolates",
        BusinessType.SPORTS_GOODS to "sportswear and sports equipment",
        BusinessType.TOYS to "toys and baby goods",
        BusinessType.HOME_FURNISHING to "furniture and home goods",
        BusinessType.HARDWARE to "hardware, tools, building materials",
        BusinessType.LAUNDRY to "laundry and dry cleaning",
        BusinessType.REAL_ESTATE to "rent and real estate",
        BusinessType.DEPARTMENT_STORE to "department stores selling many kinds of goods",
        BusinessType.ONLINE_MARKETPLACE to "online marketplaces selling many kinds of goods",
        BusinessType.STREAMING to "video and music streaming",
        BusinessType.SOFTWARE to "app stores, software, cloud services",
        BusinessType.GAMING to "games and gaming stores",
        BusinessType.ENTERTAINMENT to "cinemas, events, theme parks",
        BusinessType.CHARITY to "charities and donations",
        BusinessType.MONEY_TRANSFER to "money transfer, exchange houses, wallets",
        BusinessType.UNKNOWN to "when you cannot tell what the business is"
    )

    private val INSTRUCTIONS = """
        You identify businesses in Saudi Arabia from how a bank wrote a merchant's name on a card payment or bill.
        For each item, answer with its id and:
        - name: the business's usual brand name in English, without branch numbers, city or company words ("PANDA 1042 RIYADH" is "Panda"). If you don't know it, tidy the bank's words.
        - businessType: what the business is, from the list below. Use UNKNOWN when you can't tell; don't guess.
        - confidence: 0 to 100, how sure you are of businessType.
        Business types:
    """.trimIndent() + "\n" + BusinessType.entries.joinToString("\n") { "- ${it.name}: ${MEANINGS.getValue(it)}" }

    private val INSTRUCTIONS_WITH_RESULTS = """
        You identify a business in Saudi Arabia from how a bank wrote a merchant's name on a card payment or bill, with the results of a web search for that name.
        Answer with:
        - name: the business's usual brand name in English, without branch numbers, city or company words. If you don't know it, tidy the bank's words.
        - businessType: what the business is, from the list below. Use UNKNOWN when neither the name nor the results tell you; don't guess.
        - confidence: 0 to 100, how sure you are of businessType. Only be sure when a result is clearly about this business.
        - source: the number of the result your answer rests on, or 0 when none does.
        Business types:
    """.trimIndent() + "\n" + BusinessType.entries.joinToString("\n") { "- ${it.name}: ${MEANINGS.getValue(it)}" }
}
