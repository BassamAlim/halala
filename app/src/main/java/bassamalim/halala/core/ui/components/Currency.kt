package bassamalim.halala.core.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.em
import bassamalim.halala.R
import bassamalim.halala.core.Globals

/*
 * SAR is shown as the riyal sign rather than its code. The sign's code point (U+20C1) is too new
 * for the bundled fonts, so it is a drawable: an Icon on its own, inline content inside a Text.
 * Other currencies stay as their ISO code.
 */

private const val INLINE_ID = "currency"

/** The sign's width over its height, from the official artwork. */
private const val RIYAL_ASPECT = 1124.14f / 1256.39f

/** A currency set on its own beside an amount: the riyal sign for SAR, else the code. */
@Composable
fun CurrencyText(code: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    if (code != Globals.PRIMARY_CURRENCY) {
        Text(code, modifier, color = color, style = style)
        return
    }

    val height = with(LocalDensity.current) { style.fontSize.toDp() * RIYAL_HEIGHT }
    Icon(
        painter = painterResource(R.drawable.ic_riyal),
        contentDescription = code,
        tint = color,
        modifier = modifier.size(width = height * RIYAL_ASPECT, height = height)
    )
}

/** Appends " SAR" as a space and the riyal sign; pair the Text with [currencyInlineContent]. */
fun AnnotatedString.Builder.appendCurrency(code: String) {
    append(' ')
    if (code == Globals.PRIMARY_CURRENCY) appendInlineContent(INLINE_ID, code) else append(code)
}

/** The riyal sign for [appendCurrency], sized to the span it sits in and tinted [color]. */
fun currencyInlineContent(color: Color): Map<String, InlineTextContent> = mapOf(
    INLINE_ID to InlineTextContent(
        Placeholder(
            width = (RIYAL_HEIGHT * RIYAL_ASPECT).em,
            height = RIYAL_HEIGHT.em,
            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
        )
    ) { code ->
        Icon(painterResource(R.drawable.ic_riyal), code, Modifier.fillMaxSize(), tint = color)
    }
)

/** The sign's height against the font size: about a capital's, so it reads as a symbol, not a word. */
private const val RIYAL_HEIGHT = 0.8f

/** Where [MoneyText] puts the amount in a sentence: pass it as the string's money argument. */
const val MONEY_MARK = "\u0000"

/**
 * A sentence quoting money, the amount set as numbers are and followed by its currency (the
 * riyal sign for SAR): "Khalid owes you 1,000.00 ⃁ from 12 Sep." [text] holds [MONEY_MARK]
 * where the amount goes.
 */
@Composable
fun MoneyText(text: String, amount: String, currency: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val (before, after) = text.split(MONEY_MARK, limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    Text(
        text = buildAnnotatedString {
            append(before)
            append(amount)
            appendCurrency(currency)
            append(after)
        },
        inlineContent = currencyInlineContent(color),
        style = style,
        color = color,
        modifier = modifier
    )
}
