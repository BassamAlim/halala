package bassamalim.halala.core.ui.theme

import androidx.compose.ui.unit.dp

/** The spacing scale (space-1 … space-6). Anything that isn't on it is a mistake worth noticing. */
object Spacing {
    /** A row's title to its metadata line (the boards' 2px). */
    val xxs = 2.dp
    /** space-1: label to value inside a card. */
    val xs = 4.dp
    /** space-2: between chips, and between buttons in a row. */
    val sm = 8.dp
    /** space-3: avatar to text in a row; card inner gap. */
    val md = 12.dp
    /** space-4: card padding, and the gap between stacked cards. */
    val card = 14.dp
    /** space-5: screen side padding. */
    val screen = 18.dp
    /** space-6: between major screen sections. */
    val section = 24.dp
}

/** Fixed sizes the design pins down. */
object Sizes {
    /** Minimum height and width of anything tappable. */
    val touchTarget = 44.dp
    /** Merchant and person avatars in rows. */
    val avatar = 36.dp
    /** Nav icons; inline icons use [iconSmall]. */
    val icon = 22.dp
    val iconSmall = 18.dp
    val chip = 28.dp
    val border = 1.dp
    val progress = 4.dp
    /** The balance card's own track, thicker than a [progress] bar. */
    val balanceTrack = 8.dp
    /** Settings-style rows inside a card. */
    val listRow = 52.dp
    val fab = 56.dp
    /** The coin on the lock screen. */
    val lockMark = 64.dp
    /** The coin beside the wordmark on Home. */
    val logo = 26.dp
}

/** Component insets from the boards that are not on the spacing scale. */
object Insets {
    /** Balance card padding, and the gap between its lines. */
    val balanceCard = 20.dp
    val balanceCardGap = 10.dp
    /** The two-up summary-card grid on Home and Activity. */
    val grid = 10.dp
    /** Vertical padding of a transaction row. */
    val row = 10.dp
    /** Chip label padding. */
    val chip = 11.dp
    /** Top padding of a screen's content. */
    val screenTop = 16.dp
    /** Bottom nav: padding above and below its items. */
    val navTop = 10.dp
    val navBottom = 14.dp
    /** The auto badge's padding. */
    val badgeX = 5.dp
    val badgeY = 1.dp
    /** Horizontal padding inside a button. */
    val button = 16.dp
    /** Inputs: horizontal padding inside the field. */
    val field = 12.dp
    /** Pulls a top bar's 44dp back button out so its glyph lines up with the content edge. */
    val backNudge = 10.dp
    /** The segmented control's inset around its selected segment. */
    val segment = 3.dp
    /** Group labels above list sections. */
    val groupLabelTop = 6.dp
    val groupLabelBottom = 2.dp
    /** Nav items are at least this wide. */
    val navItemMinWidth = 56.dp
}
