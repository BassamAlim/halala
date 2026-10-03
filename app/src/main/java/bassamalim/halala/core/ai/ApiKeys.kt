package bassamalim.halala.core.ai

/** The keys Halala is built with for the services it calls: Groq, and Tavily for web search. */
interface ApiKeys {
    /** Null when this build has none. */
    fun groq(): String?

    fun hasGroq(): Boolean = groq() != null

    /** Null when this build has none: merchants are then never looked up online. */
    fun tavily(): String? = null

    fun hasTavily(): Boolean = tavily() != null
}
