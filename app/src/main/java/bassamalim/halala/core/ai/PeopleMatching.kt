package bassamalim.halala.core.ai

import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.PeopleRepository
import bassamalim.halala.core.data.repositories.PreferencesRepository
import bassamalim.halala.core.data.repositories.SmsRepository
import bassamalim.halala.core.domain.Identification
import bassamalim.halala.core.domain.People
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
import javax.inject.Inject

/**
 * What Halala says to Groq to find one person under two names, and reads back, as pure
 * functions. The request holds people's names as banks wrote them and nothing else: no amount,
 * no account, nothing of who sent what to whom.
 */
object PeopleMatchProtocol {

    /** Each of [people] is the names one person goes by, numbered from 1. */
    fun request(people: List<List<String>>): String {
        val items = buildJsonArray {
            people.forEachIndexed { index, names ->
                addJsonObject {
                    put("id", (index + 1).toString())
                    putJsonArray("names") { names.forEach(::add) }
                }
            }
        }
        return buildJsonObject {
            put("model", GroqProtocol.MODEL)
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
                    put("name", "people")
                    put("strict", true)
                    put("schema", SCHEMA)
                }
            }
        }.toString()
    }

    /**
     * The pairs it read as one person, as indices into the [count] people asked about. An id
     * that isn't one of them is dropped, and a group of three is its three pairs.
     */
    fun parse(body: String, count: Int): Set<Pair<Int, Int>> =
        json.decodeFromString<Answers>(GroqProtocol.contentOf(body)).groups.flatMap { group ->
            val members = group.ids.mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..count }.distinct().sorted()
            members.flatMapIndexed { at, first -> members.drop(at + 1).map { second -> first - 1 to second - 1 } }
        }.toSet()

    private const val MAX_TOKENS = 4096

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Answers(val groups: List<Group> = emptyList())

    @Serializable
    private data class Group(val ids: List<String> = emptyList())

    /** Strict mode: every field required, no others. */
    private val SCHEMA: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("groups") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("ids") {
                            put("type", "array")
                            putJsonObject("items") { put("type", "string") }
                        }
                    }
                    put("required", JsonArray(listOf(JsonPrimitive("ids"))))
                    put("additionalProperties", false)
                }
            }
        }
        put("required", JsonArray(listOf(JsonPrimitive("groups"))))
        put("additionalProperties", false)
    }

    private val INSTRUCTIONS = """
        Each item is one person, with the names Saudi banks wrote for them on money transfers. The same person is often written differently: in Arabic and in English, with a first name cut to its initial, with a middle name left out, or cut short by the bank.
        Find the items that are the same person and answer with groups of their ids. An item in no group is someone on their own; most are.
        Group two items only when every part of the names that both give agrees: the same first name (or its initial) and the same family name. Arabic and English spellings of one name agree ("أحمد" and "Ahmed").
        Different first names are different people, even with the same father's and family name: they are siblings. A first name alone is not enough. When unsure, leave them apart.
    """.trimIndent()
}

/**
 * Asks the AI which people are one person under two names, for what the spelling and the
 * account digits can't tell (`People.suggest`). It only ever suggests: you merge. Asked again
 * only when someone new appears, with everyone, since the new name may be an old one's.
 */
class PeopleMatching @Inject constructor(
    private val people: PeopleRepository,
    private val accounts: AccountsRepository,
    private val sms: SmsRepository,
    private val preferences: PreferencesRepository,
    private val keys: ApiKeys,
    private val groq: GroqHttp
) {

    @Throws(IdentifyFailure::class)
    suspend fun run() {
        if (!keys.hasGroq()) return
        val everyone = people.getPeople()
        val seen = preferences.peopleAsked()
        if (everyone.size < 2 || everyone.all { it.uid in seen }) return

        // Only a name that can't hold more than a name is sent, as with merchants.
        val last4s = ownLast4s(accounts, sms)
        val names = people.getAliases().groupBy({ it.personId }) { it.descriptor }
        // ponytail: one request, the newest people; batch with overlap if there are ever more.
        val asked = everyone.mapNotNull { person ->
            names[person.id].orEmpty().filter { Identification.sendable(it, last4s) }.distinct()
                .takeIf { it.isNotEmpty() }?.let { person to it }
        }.takeLast(MAX_PEOPLE)

        val same = if (asked.size < 2) emptySet() else {
            val body = groq.post(PeopleMatchProtocol.request(asked.map { it.second }))
            runCatching { PeopleMatchProtocol.parse(body, asked.size) }
                .getOrElse { throw IdentifyFailure(IdentifyProblem.REJECTED, it) }
                .map { (first, second) -> People.pairKey(asked[first].first.uid, asked[second].first.uid) }
                .toSet()
        }
        preferences.recordPeopleMatches(asked = everyone.map { it.uid }.toSet(), same = same)
    }

    private companion object {
        const val MAX_PEOPLE = 300
    }
}
