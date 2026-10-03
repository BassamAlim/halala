package bassamalim.halala.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import bassamalim.halala.R
import bassamalim.halala.core.ui.theme.HalalaColors
import bassamalim.halala.core.ui.theme.HalalaNumbers
import bassamalim.halala.core.ui.theme.HalalaTheme
import bassamalim.halala.core.ui.theme.HalalaType
import bassamalim.halala.core.ui.theme.Insets
import bassamalim.halala.core.ui.theme.Radius
import bassamalim.halala.core.ui.theme.Sizes
import bassamalim.halala.core.ui.theme.Spacing

/**
 * The default container: the card fill, no border, radius-lg, [Insets.card] padding. It starts with a
 * muted label when given one. No shadows, no borders, no coloured edges.
 */
@Composable
fun HalalaCard(
    modifier: Modifier = Modifier,
    label: String? = null,
    contentPadding: PaddingValues = PaddingValues(Insets.card),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(Spacing.xs),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(Radius.lg)
            // Before the fill, so a press sinks the whole card.
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .background(HalalaColors.Card)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement
    ) {
        if (label != null) CardLabel(label)
        content()
    }
}

/** The muted `label` a card or section starts with. */
@Composable
fun CardLabel(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = HalalaType.Label, color = HalalaColors.TextMuted, modifier = modifier)
}

/** A summary card: label, one amount-lg figure, and a caption under it. */
@Composable
fun SummaryCard(
    label: String,
    amount: String,
    modifier: Modifier = Modifier,
    amountColor: Color = HalalaColors.Text,
    currency: String? = null,
    caption: String? = null,
    captionColor: Color = HalalaColors.TextMuted,
    onClick: (() -> Unit)? = null
) {
    HalalaCard(modifier = modifier, label = label, onClick = onClick) {
        // A blank figure (still loading) is a shimmering bar; when it arrives its digits rise in.
        if (amount.isEmpty()) Text(
            text = "",
            style = HalalaNumbers.AmountLg,
            modifier = Modifier.fillMaxWidth(0.6f).clip(Radius.xs).shimmer()
        )
        else RollingAmount(text = amount, style = HalalaNumbers.AmountLg, color = amountColor, currency = currency)
        if (caption != null)
            Text(text = caption, style = HalalaType.Caption, color = captionColor)
    }
}

/**
 * A card of settings-style rows, divided by 1dp lines rather than split into more cards. Its
 * top and bottom padding plus a row's own make the first and last lines sit as far from the
 * card's edge as its sides do.
 */
@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    HalalaCard(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Insets.card, vertical = LIST_CARD_Y),
        verticalArrangement = Arrangement.Top,
        content = content
    )
}

/**
 * One row in a [ListCard]: a title, an optional muted second line and, when tappable, a
 * chevron. [divider] draws the line above it; the first row in a card has none.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    divider: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Column(modifier.fillMaxWidth()) {
        if (divider) HorizontalDivider(thickness = Sizes.border, color = HalalaColors.Line)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.listRow)
                .then(if (onClick != null) Modifier.pressArea().clickable(role = Role.Button, onClick = onClick).padding(horizontal = PRESS_BLEED) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            leading?.invoke()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = LIST_ROW_Y),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
            ) {
                Text(text = title, style = HalalaType.BodyStrong)
                if (subtitle != null)
                    Text(text = subtitle, style = HalalaType.Caption, color = HalalaColors.TextMuted)
            }

            trailing?.invoke(this)

            if (onClick != null && trailing == null) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = HalalaColors.TextMuted,
                    modifier = Modifier.size(Sizes.iconSmall)
                )
            }
        }
    }
}

/**
 * A row's press area: rounded, and reaching [PRESS_BLEED] past the row on each side without
 * moving it (follow the clickable with `padding(horizontal = PRESS_BLEED)`), so the highlight
 * has room around the text rather than a square edge against it.
 */
internal fun Modifier.pressArea(): Modifier = layout { measurable, constraints ->
    val bleed = PRESS_BLEED.roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = bleed * 2))
    layout(placeable.width - bleed * 2, placeable.height) { placeable.place(-bleed, 0) }
}.clip(Radius.sm)

internal val PRESS_BLEED = Spacing.sm

/** A [ListRow]'s text to its divider, and what a [ListCard] adds above its first and below its last. */
private val LIST_ROW_Y = Spacing.md
private val LIST_CARD_Y = Insets.card - LIST_ROW_Y

@Preview
@Composable
private fun CardPreview() = HalalaTheme {
    Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.card)) {
        SummaryCard(label = "Net worth", amount = "312,450", currency = "SAR", caption = "+2.1% this month", captionColor = HalalaColors.Income)
        ListCard {
            ListRow(title = "Accounts", subtitle = "7 accounts at 6 banks", onClick = {})
            ListRow(title = "Backup and export", subtitle = "CSV or JSON", divider = true, onClick = {})
        }
    }
}
