package bassamalim.halala.core.ai

/** The keys Halala is built with for the services it calls: only Groq's so far. */
interface ApiKeys {
    /** Null when this build has none. */
    fun groq(): String?

    fun hasGroq(): Boolean = groq() != null
}
