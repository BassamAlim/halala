package bassamalim.halala.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The Halala palette ("Ink Block Jade"), one-to-one with the design system's colour tokens.
 * There is one theme and it is dark. Tokens are semantic: never use a hex anywhere else.
 */
object HalalaColors {

    /** Screen ground. Everything sits on this. */
    val Bg = Color(0xFF0E0F11)

    /** Cards, bottom nav, inputs, secondary buttons. Always with a 1dp [Line] border. */
    val Surface = Color(0xFF16181B)

    /** Raised or inset fills inside a surface: avatars, plain chips, progress tracks. */
    val Surface2 = Color(0xFF1D2024)

    /** Borders, dividers between list rows, outline chips. Never text. */
    val Line = Color(0xFF2A2E33)

    /** Primary text and all spending amounts. */
    val Text = Color(0xFFECEEF0)

    /** Labels, metadata, secondary lines, inactive nav. Never below 12sp. */
    val TextMuted = Color(0xFF9AA1A9)

    /**
     * Jade, the brand: logo, primary buttons, active nav, links, selected chips, income. Use it
     * on one or two things per screen.
     */
    val Accent = Color(0xFF7DD4A0)

    /** Text and marks on any [Accent], [StateWarn] or [StateOver] fill. Never white on these. */
    val OnAccent = Color(0xFF0E0F11)

    /** Positive amounts: salary, incoming transfers, refunds, growth. */
    val Income = Accent

    /** Balance card fill while spending is under 80% of the cycle budget. */
    val StateOk = Accent

    /** Amber: balance card at 80–100% of budget, and pace warnings. */
    val StateWarn = Color(0xFFE8B55B)

    /** Coral: over budget, anomalies, destructive confirmations. The only red in the app. */
    val StateOver = Color(0xFFF08A7B)

    /** Quiet blue for neutral information that must not read as money. */
    val Info = Color(0xFF8FA7C9)

    /** Keyboard and switch-access focus ring. */
    val FocusRing = Accent

    /** The progress track inside the balance card: black at 18% over the fill. */
    val BalanceTrack = Color(0x2E000000)

    /** The status pill inside the balance card: black at 14% over the fill. */
    val BalancePill = Color(0x24000000)

    /** Scrim behind a sheet or dialog. */
    val Scrim = Color(0x99000000)

    /** Cards: borderless, a step above [Bg] and [Surface], told apart by tone alone. */
    val Card = Color(0xFF181B1E)

    /** A press: the pressed thing is washed with this, as well as shrinking a little. */
    val Pressed = Color(0x0FECEEF0)

    /** The highlight across a filled object's top (the balance card, primary buttons). */
    val Sheen = Color(0x29FFFFFF)
}
