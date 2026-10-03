package bassamalim.halala.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import bassamalim.halala.R

@OptIn(ExperimentalTextApi::class)
private fun instrument(weight: Int) = Font(
    resId = R.font.instrument_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

/**
 * Instrument Sans for words: bundled as a variable font (OFL) and instanced at the weights the
 * system uses. Bundled rather than downloaded, so nothing is fetched and nothing depends on
 * Play services.
 */
val InstrumentSans = FontFamily(
    instrument(400),
    instrument(500),
    instrument(600),
    instrument(700)
)

/** IBM Plex Mono for every number (OFL, bundled). */
val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium)
)

/** The design system's Text styles, by their token names. */
object HalalaType {

    /** screen-title: top-level tab titles and inbox headings. */
    val ScreenTitle = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight(600),
        fontSize = 26.sp,
        lineHeight = 32.sp
    )

    /** The wordmark beside the mark on Home. */
    val Wordmark = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight(600),
        fontSize = 20.sp,
        lineHeight = 24.sp
    )

    /** title: top bars of sub-screens, merchant name in detail. */
    val Title = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight(600),
        fontSize = 17.sp,
        lineHeight = 22.sp
    )

    /** body-strong: list row titles, button labels. */
    val BodyStrong = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight(500),
        fontSize = 14.sp,
        lineHeight = 20.sp
    )

    /** body: sentences, questions, rule descriptions. */
    val Body = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight(400),
        fontSize = 14.sp,
        lineHeight = 20.sp
    )

    /** label: card labels and section headers, in text-muted. */
    val Label = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight(400),
        fontSize = 13.sp,
        lineHeight = 18.sp
    )

    /** caption: row metadata, chip text, deltas. */
    val Caption = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight(400),
        fontSize = 12.sp,
        lineHeight = 16.sp
    )

    /** overline: the uppercase label inside the balance card, and nowhere else. */
    val Overline = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight(700),
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.12.em
    )

    /** The auto badge: 10sp uppercase. */
    val Badge = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight(500),
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.06.em
    )

    /** Bottom nav labels. */
    val NavLabel = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight(500),
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
}

/**
 * The Numbers styles. Every amount uses one of these: Plex Mono with tabular figures, so columns
 * of money line up.
 */
object HalalaNumbers {

    private const val TABULAR = "tnum"

    /** amount-hero: the balance card figure. One per screen. */
    val AmountHero = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Medium,
        fontSize = 46.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.03).em,
        fontFeatureSettings = TABULAR
    )

    /** amount-xl: transaction detail amount, net worth total. */
    val AmountXl = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Medium,
        fontSize = 38.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.03).em,
        fontFeatureSettings = TABULAR
    )

    /** amount-lg: summary cards. */
    val AmountLg = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.01).em,
        fontFeatureSettings = TABULAR
    )

    /** amount: list rows. */
    val Amount = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.01).em,
        fontFeatureSettings = TABULAR
    )

    /** Digits in metadata: "••4821", raw SMS. */
    val Meta = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontFeatureSettings = TABULAR
    )
}

/** The same styles on the Material 3 slots its components read. */
val Typography = Typography(
    headlineMedium = HalalaType.ScreenTitle,
    titleMedium = HalalaType.Title,
    titleSmall = HalalaType.BodyStrong,
    bodyLarge = HalalaType.Body,
    bodyMedium = HalalaType.Body,
    bodySmall = HalalaType.Caption,
    labelLarge = HalalaType.BodyStrong,
    labelMedium = HalalaType.Label,
    labelSmall = HalalaType.Overline
)
