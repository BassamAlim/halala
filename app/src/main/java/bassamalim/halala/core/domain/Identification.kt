package bassamalim.halala.core.domain

import bassamalim.halala.core.data.dataSources.room.entities.Category
import bassamalim.halala.core.data.dataSources.room.entities.Merchant
import bassamalim.halala.core.enums.BusinessType
import bassamalim.halala.core.enums.IdentifiedBy

/** How a merchant's spending is filed once it is identified (the spec's confidence tiers). */
enum class Tier {
    /** Filed on its own, by an automatic rule. */
    AUTO,

    /** In the review inbox with its category chosen, one tap to confirm. */
    SUGGEST,

    /** In the review inbox with nothing chosen. */
    ASK
}

/**
 * Turning what a merchant is into where its spending goes. The AI (or the bundled list, or you)
 * only says what the business is; which category takes that, its expense type and how sure to
 * be are decided here, on the phone.
 */
object Identification {

    /** The AI's confidence (0–100) at or above which its answer files on its own. */
    const val AUTO_AT = 90

    /** Below this, its answer is only evidence: nothing is chosen for you. */
    const val SUGGEST_AT = 60

    /** The category that takes [type]: none for an unknown business or a type no category takes. */
    fun categoryFor(type: BusinessType?, categories: List<Category>): Category? =
        if (type == null || type == BusinessType.UNKNOWN) null
        else categories.firstOrNull { type in it.businessTypes }

    /**
     * Whether [merchant]'s spending files itself, is suggested, or is asked. What the bundled
     * list or you said is trusted; what the AI said is as trusted as it was sure.
     */
    fun tierOf(merchant: Merchant, categories: List<Category>): Tier {
        if (categoryFor(merchant.businessType, categories) == null) return Tier.ASK
        return when (merchant.identifiedBy) {
            IdentifiedBy.LIST, IdentifiedBy.YOU -> Tier.AUTO
            IdentifiedBy.AI -> {
                val confidence = merchant.confidence ?: 0
                when {
                    confidence >= AUTO_AT -> Tier.AUTO
                    confidence >= SUGGEST_AT -> Tier.SUGGEST
                    else -> Tier.ASK
                }
            }
            IdentifiedBy.WITHHELD, null -> Tier.ASK
        }
    }

    /**
     * Whether a merchant's name may be sent to the AI. A shop's name is all that ever leaves the
     * phone, so a name that might hold more is kept back: one with any of your accounts' [last4s],
     * a run of ten or more digits (a card, account or phone number), or an IBAN.
     */
    fun sendable(name: String, last4s: Collection<String>): Boolean {
        val latin = name.map { char ->
            when (char) {
                in '٠'..'٩' -> '0' + (char - '٠')
                in '۰'..'۹' -> '0' + (char - '۰')
                else -> char
            }
        }.joinToString("")
        val runs = DIGITS.findAll(latin).map { it.value }.toList()
        if (runs.any { run -> run.length >= LONG_RUN || last4s.any { it.length == 4 && it in run } }) return false
        // A word and a number run together ("UR12HYPERMARKET") look like one; an IBAN is mostly digits.
        return IBAN.findAll(latin.uppercase().replace(" ", "")).none { match -> match.value.count(Char::isDigit) >= LONG_RUN }
    }

    private val DIGITS = Regex("[0-9]+")
    private val IBAN = Regex("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}")
    private const val LONG_RUN = 10
}
